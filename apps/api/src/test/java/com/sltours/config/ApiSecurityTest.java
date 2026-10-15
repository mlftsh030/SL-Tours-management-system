package com.sltours.config;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.sltours.accounts.controller.*;
import com.sltours.customers.controller.AdminCustomerController;
import com.sltours.accounts.dto.SessionResponse;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.accounts.service.*;
import com.sltours.accounts.service.IdleSessionFilter;
import com.sltours.bookings.controller.BookingController;
import com.sltours.bookings.service.BookingService;
import com.sltours.operations.controller.OperationsController;
import com.sltours.operations.dto.OperationsDashboardResponse;
import com.sltours.operations.service.OperationsService;
import com.sltours.tours.controller.*;
import com.sltours.tours.service.*;
import com.sltours.tripplanner.controller.TripPlanningController;
import com.sltours.tripplanner.service.TripPlanningService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = { OperationsController.class, BookingController.class, TourController.class,
    TripPlanningController.class, AuthController.class, AdminTourController.class,
    CustomerBookingsController.class, AdminCustomerController.class })
@Import(SecurityConfig.class)
@TestPropertySource(properties = { "sltours.cors.allowed-origins=http://localhost:5173" })
class ApiSecurityTest {
    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwords;
    @MockBean private OperationsService operations;
    @MockBean private BookingService bookings;
    @MockBean private TourService tours;
    @MockBean private AdminTourService adminTours;
    @MockBean private TripPlanningService planner;
    @MockBean private AccountRepository accounts;
    @MockBean private AccountService accountService;
    @MockBean private CustomerBookingService customerBookings;
    @MockBean private com.sltours.customers.service.CustomerRecordsService customers;
    @MockBean private com.sltours.accounts.service.CustomerAccountService customerAccounts;

    private static final String TOUR = """
        {"version":0,"slug":"new-tour","title":"New tour","shortDescription":"Summary","fullDescription":"Description",
        "duration":"Full day","basePriceCents":123456,"pricingType":"PER_PERSON","minimumGuests":1,"maximumGuests":18,
        "pickupAvailable":true,"featured":false,"status":"DRAFT","category":"CITY","heroImage":"/assets/city-tour.jpg",
        "seoTitle":"New tour","seoDescription":"Summary","displayOrder":100,
        "locations":[],"tags":[],"highlights":[],"included":[],"excluded":[],"itinerary":[],"faqs":[]}
        """;

    @Test void protectedReadsRejectAnonymousAndBasicCredentials() throws Exception {
        mvc.perform(get("/api/v1/admin/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/dashboard").with(httpBasic("ops@test.local", "correct-horse")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/bookings")).andExpect(status().isUnauthorized());
    }
    @Test void adminAndCustomerAuthorizationIsEnforced() throws Exception {
        when(operations.dashboard()).thenReturn(new OperationsDashboardResponse(0,0,0,0,0,0,0,0,0,0,0,0));
        mvc.perform(get("/api/v1/admin/dashboard").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/dashboard").with(user("staff").roles("STAFF"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/customers").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/account/bookings").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        verifyNoInteractions(customers, customerBookings);
    }
    @Test void csrfIsRequiredForLoginLogoutAndAllWrites() throws Exception {
        mvc.perform(post("/api/v1/auth/login").param("username", "ops@test.local").param("password", "correct-horse")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/tours").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(TOUR)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content("{bad")).andExpect(status().isForbidden());
        verifyNoInteractions(adminTours, bookings);
    }
    @Test void staffCannotCreateOrUpdateTours() throws Exception {
        mvc.perform(post("/api/v1/admin/tours").with(user("staff").roles("STAFF")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(TOUR)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/tours/" + UUID.randomUUID()).with(user("staff").roles("STAFF")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(TOUR)).andExpect(status().isForbidden());
        verifyNoInteractions(adminTours);
    }
    @Test void administratorCanCreateValidatedTours() throws Exception {
        mvc.perform(post("/api/v1/admin/tours").with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(TOUR)).andExpect(status().isCreated());
        verify(adminTours).create(any());
    }
    @Test void loginCreatesSessionAndLogoutInvalidatesIt() throws Exception {
        when(operations.bookings(null, 0, 20)).thenReturn(org.springframework.data.domain.Page.empty());
        Account account = new Account(); account.setEmail("ops@test.local"); account.setName("Ops");
        account.setRole(Account.Role.ADMIN); account.setPasswordHash(passwords.encode("correct-horse"));
        when(accounts.findByEmail("ops@test.local")).thenReturn(Optional.of(account));
        MockHttpSession beforeLogin = (MockHttpSession) mvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        String previousSessionId = beforeLogin.getId();
        var result = mvc.perform(post("/api/v1/auth/login").session(beforeLogin).with(csrf()).param("username", "OPS@test.local").param("password", "correct-horse"))
            .andExpect(status().isNoContent()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotEqualTo(previousSessionId);
        mvc.perform(get("/api/v1/admin/bookings").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void idleSessionsExpireEvenWhileBackgroundPollingContinues() throws Exception {
        when(operations.bookings(null, 0, 20)).thenReturn(org.springframework.data.domain.Page.empty());
        Account account = new Account(); account.setEmail("ops@test.local"); account.setName("Ops");
        account.setRole(Account.Role.ADMIN); account.setPasswordHash(passwords.encode("correct-horse"));
        when(accounts.findByEmail("ops@test.local")).thenReturn(Optional.of(account));
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf())
                .param("username", "ops@test.local").param("password", "correct-horse"))
            .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
        long idleSince = System.currentTimeMillis() - java.time.Duration.ofMinutes(20).toMillis();
        session.setAttribute(IdleSessionFilter.LAST_ACTIVITY, idleSince);
        // Polling keeps working but does not count as activity.
        mvc.perform(get("/api/v1/admin/bookings").session(session).header(IdleSessionFilter.BACKGROUND_HEADER, "1"))
            .andExpect(status().isOk());
        assertThat(session.getAttribute(IdleSessionFilter.LAST_ACTIVITY)).isEqualTo(idleSince);
        session.setAttribute(IdleSessionFilter.LAST_ACTIVITY, System.currentTimeMillis() - java.time.Duration.ofMinutes(31).toMillis());
        mvc.perform(get("/api/v1/admin/bookings").session(session).header(IdleSessionFilter.BACKGROUND_HEADER, "1"))
            .andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void userActivityRenewsTheIdleWindow() throws Exception {
        when(operations.bookings(null, 0, 20)).thenReturn(org.springframework.data.domain.Page.empty());
        Account account = new Account(); account.setEmail("ops@test.local"); account.setName("Ops");
        account.setRole(Account.Role.ADMIN); account.setPasswordHash(passwords.encode("correct-horse"));
        when(accounts.findByEmail("ops@test.local")).thenReturn(Optional.of(account));
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf())
                .param("username", "ops@test.local").param("password", "correct-horse"))
            .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
        long idleSince = System.currentTimeMillis() - java.time.Duration.ofMinutes(20).toMillis();
        session.setAttribute(IdleSessionFilter.LAST_ACTIVITY, idleSince);
        mvc.perform(get("/api/v1/admin/bookings").session(session)).andExpect(status().isOk());
        assertThat((Long) session.getAttribute(IdleSessionFilter.LAST_ACTIVITY)).isGreaterThan(idleSince);
    }
    @Test void updatesWithoutAVersionAreRejectedInsteadOfDefaultingToZero() throws Exception {
        mvc.perform(patch("/api/v1/admin/bookings/SLT-2026-AAAAAA/status").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.version").exists());
        mvc.perform(put("/api/v1/admin/tours/" + UUID.randomUUID()).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(TOUR.replace("\"version\":0,", "")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.version").exists());
        verifyNoInteractions(operations, adminTours);
    }
    @Test void wrongPasswordGetsGenericError() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf()).param("username", "nobody@test.local").param("password", "wrong"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Email or password is incorrect"));
    }
    @Test void publicCatalogueRemainsPublicAndUnknownRoutesAreDenied() throws Exception {
        when(tours.listTours(null, null)).thenReturn(List.of());
        mvc.perform(get("/api/v1/tours")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/unknown")).andExpect(status().isUnauthorized());
    }
    @Test void malformedRequestsAndInvalidPaginationAreRejected() throws Exception {
        mvc.perform(post("/api/v1/bookings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{bad"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(get("/api/v1/admin/bookings?page=-1").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/tours?size=500").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
    }
    @Test void registrationValidationPreventsWeakPasswords() throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Someone\",\"email\":\"person@example.com\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest());
        verify(accountService, never()).register(any());
    }
}
