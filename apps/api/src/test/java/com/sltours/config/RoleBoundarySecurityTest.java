package com.sltours.config;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sltours.accounts.repository.AccountRepository;
import com.sltours.accounts.service.AccountService;
import com.sltours.assignments.controller.AdminAssignmentController;
import com.sltours.assignments.service.*;
import com.sltours.drivers.controller.*;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.service.*;
import com.sltours.enquiries.controller.*;
import com.sltours.enquiries.dto.EnquiryReceipt;
import com.sltours.enquiries.model.EnquiryStatus;
import com.sltours.enquiries.service.EnquiryService;
import com.sltours.fleet.controller.AdminFleetController;
import com.sltours.fleet.service.*;
import com.sltours.inspections.service.InspectionService;
import com.sltours.media.*;
import com.sltours.privatetrips.controller.*;
import com.sltours.privatetrips.service.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Every role boundary in the brief, checked through the real security filter chain. */
@WebMvcTest(controllers = { AdminEnquiryController.class, EnquiryController.class, AdminDriverController.class, DriverPortalController.class,
    AdminFleetController.class, AdminAssignmentController.class, AdminPrivateTripController.class, PrivateTripController.class,
    AdminMediaController.class, PublicMediaController.class, com.sltours.siteimages.SiteImageController.class,
    com.sltours.activities.controller.ActivityController.class, com.sltours.activities.controller.AdminActivityController.class,
    com.sltours.seo.SeoController.class, com.sltours.assistant.controller.AssistantController.class,
    com.sltours.partners.controller.AdminPartnerController.class, com.sltours.partners.controller.PartnerPortalController.class,
    com.sltours.partners.controller.PublicPartnerController.class, com.sltours.payments.controller.AdminBookingFinanceController.class })
@Import(SecurityConfig.class)
@TestPropertySource(properties = { "sltours.cors.allowed-origins=http://localhost:5173" })
class RoleBoundarySecurityTest {
    @Autowired MockMvc mvc;
    @MockBean EnquiryService enquiries;
    @MockBean AdminDriverService adminDrivers;
    @MockBean CurrentDriver currentDriver;
    @MockBean DriverAssignmentService driverAssignments;
    @MockBean InspectionService inspections;
    @MockBean DriverEligibility eligibility;
    @MockBean AccountRepository accounts;
    @MockBean AccountService accountService;
    @MockBean MediaService media;
    @MockBean com.sltours.privatetrips.service.VehicleOptionService vehicleOptions;
    @MockBean FleetService fleet;
    @MockBean RestrictionService restrictions;
    @MockBean AdminAssignmentService adminAssignments;
    @MockBean PrivateTripAdminService privateTripAdmin;
    @MockBean PricingAdminService pricing;
    @MockBean PrivateTripService privateTrips;
    @MockBean com.sltours.siteimages.SiteImageService siteImages;
    @MockBean com.sltours.activities.service.ActivityService activityService;
    @MockBean com.sltours.activities.service.ItineraryService itineraryService;
    @MockBean com.sltours.activities.service.ActivityAdminService activityAdmin;
    @MockBean com.sltours.activities.service.PlanningService planningService;
    @MockBean com.sltours.seo.SeoService seoService;
    @MockBean com.sltours.assistant.service.AssistantService assistant;
    @MockBean com.sltours.partners.service.PartnerAdminService partnerAdmin;
    @MockBean com.sltours.partners.service.PartnerFinanceService partnerFinance;
    @MockBean com.sltours.partners.service.SupplierProposalService supplierProposals;
    @MockBean com.sltours.partners.service.PartnerPortalService partnerPortal;
    @MockBean com.sltours.partners.service.PartnerApplicationService partnerApplications;
    @MockBean com.sltours.partners.service.ReferralService referralService;
    @MockBean com.sltours.payments.service.BookingPaymentService bookingPayments;

    private static final UUID ID = UUID.randomUUID();
    private static final String REVIEW = "{\"version\":0,\"notes\":\"ok\"}";

    @Test void driversHaveNoAccessToTheInboxCustomersOrAnyAdminArea() throws Exception {
        for (String path : List.of("/api/v1/admin/enquiries", "/api/v1/admin/enquiries/SLE-2026-AAAAA", "/api/v1/admin/customers",
                "/api/v1/admin/bookings", "/api/v1/admin/vehicles", "/api/v1/admin/assignments", "/api/v1/admin/inspections/pending",
                "/api/v1/admin/media/" + ID, "/api/v1/admin/drivers", "/api/v1/admin/pricing"))
            mvc.perform(get(path).with(user("driver@test.local").roles("DRIVER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/enquiries/SLE-2026-AAAAA/notes").with(user("driver@test.local").roles("DRIVER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(enquiries, media, fleet, adminAssignments, adminDrivers, pricing);
    }

    @Test void onlyDriversReachTheDriverPortal() throws Exception {
        mvc.perform(get("/api/v1/driver/assignments")).andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "STAFF", "ADMIN"))
            mvc.perform(get("/api/v1/driver/assignments").with(user("someone").roles(role))).andExpect(status().isForbidden());
        when(currentDriver.require(any())).thenReturn(new Driver());
        mvc.perform(get("/api/v1/driver/assignments").with(user("driver@test.local").roles("DRIVER"))).andExpect(status().isOk());
    }

    @Test void staffCanWorkTheInboxAndReportProblemsButNotApproveClearAssignOrPrice() throws Exception {
        when(enquiries.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get("/api/v1/admin/enquiries").with(user("staff").roles("STAFF"))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/vehicles/" + ID + "/restrictions").with(user("staff").roles("STAFF")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"MAINTENANCE\",\"reason\":\"Tyre looks soft\"}")).andExpect(status().isOk());
        var staff = user("staff").roles("STAFF");
        mvc.perform(post("/api/v1/admin/inspections/" + ID + "/approve").with(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REVIEW))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/inspections/" + ID + "/reject").with(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REVIEW))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/vehicles/" + ID + "/restrictions/" + ID + "/clear").with(staff).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"note\":\"Repaired and road tested\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/private-trips/SLT-2026-AAAAAA/confirm").with(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"vehicleId\":\"" + ID + "\",\"driverId\":\"" + ID + "\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/pricing/rates/VAN").with(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"hourlyRateCents\":50000,\"minimumMinutes\":120}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/drivers").with(staff)).andExpect(status().isForbidden());
        // Assignment options list driver eligibility problems, which are verification data.
        mvc.perform(get("/api/v1/admin/assignments/options?startsAt=2026-11-01T08:00:00Z&endsAt=2026-11-01T12:00:00Z").with(staff))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/site-images?slot=HOME_HERO").with(staff)).andExpect(status().isForbidden());
        verify(inspections, never()).approve(any(), any());
        verify(restrictions, never()).clear(any(), any(), anyLong(), any());
        verifyNoInteractions(privateTripAdmin, adminDrivers, siteImages);
        verify(adminAssignments, never()).options(any(), any(), anyInt());
        verify(pricing, never()).saveRate(any(), any());
    }

    @Test void driverDocumentsAreAdministratorOnlyAndPrivateFilesAreNeverPublic() throws Exception {
        MediaFile document = new MediaFile("driver_document/x.pdf", "application/pdf", 4, "0".repeat(64), "licence.pdf",
            MediaPurpose.DRIVER_DOCUMENT, MediaFile.Visibility.PRIVATE, "admin");
        when(media.find(ID)).thenReturn(document);
        when(media.open(document)).thenAnswer(i -> new ByteArrayInputStream("%PDF".getBytes()));
        mvc.perform(get("/api/v1/admin/media/" + ID).with(user("staff").roles("STAFF"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/media/" + ID).with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "private, no-store")).andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/api/v1/public/media/" + ID)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/media/" + ID)).andExpect(status().isUnauthorized());
    }

    @Test void driversOnlyReceiveTheirOwnInspectionMedia() throws Exception {
        Driver driver = new Driver();
        when(currentDriver.require(any())).thenReturn(driver);
        when(inspections.driverMayView(driver, ID)).thenReturn(false);
        mvc.perform(get("/api/v1/driver/media/" + ID).with(user("driver@test.local").roles("DRIVER"))).andExpect(status().isNotFound());
        verify(media, never()).open(any());
    }

    @Test void driverInspectionUploadsRequireCsrfAndReachTheServiceAsTheSignedInDriver() throws Exception {
        Driver driver = new Driver();
        when(currentDriver.require(any())).thenReturn(driver);
        var json = new MockMultipartFile("inspection", "", "application/json", """
            {"odometerKm":100,"fuelLevel":"FULL","checklist":[{"item":"BRAKES","result":"OK"}],"photoTags":["VEHICLE"]}""".getBytes());
        var photo = new MockMultipartFile("photos", "a.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        var selfie = new MockMultipartFile("selfie", "s.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        mvc.perform(multipart("/api/v1/driver/assignments/" + ID + "/collection").file(json).file(photo).file(selfie)
            .with(user("driver@test.local").roles("DRIVER"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/driver/assignments/" + ID + "/collection").file(json).file(photo).file(selfie)
            .with(user("driver@test.local").roles("DRIVER")).with(csrf())).andExpect(status().isCreated());
        verify(inspections).submitCollection(same(driver), eq(ID), any(), argThat(list -> list.size() == 1), any());
    }

    @Test void publicVisitorsCanSubmitEnquiriesAndPrivateTripRequestsButNothingElse() throws Exception {
        when(enquiries.submit(any())).thenReturn(new EnquiryReceipt("SLE-2026-AAAAA", EnquiryStatus.NEW, "Thanks", false));
        mvc.perform(post("/api/v1/enquiries").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
            {"idempotencyKey":"%s","customerName":"A","customerEmail":"a@example.com","message":"Hello"}""".formatted(UUID.randomUUID())))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.reference").value("SLE-2026-AAAAA"));
        mvc.perform(post("/api/v1/enquiries").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerName\":\"A\",\"customerEmail\":\"a@example.com\",\"message\":\"Hello\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.idempotencyKey").exists());
        mvc.perform(get("/api/v1/private-trips/vehicles")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/enquiries")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/enquiries").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
    }

    @Test void catalogueIsPublicButEditingItIsAdministratorOnly() throws Exception {
        when(seoService.sitemap()).thenReturn(java.util.Optional.of("<urlset/>"));
        mvc.perform(get("/api/v1/activities")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/seo/sitemap.xml")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-cache"));
        mvc.perform(get("/api/v1/admin/activities")).andExpect(status().isUnauthorized());
        for (String role : List.of("STAFF", "DRIVER", "CUSTOMER"))
            mvc.perform(get("/api/v1/admin/activities").with(user("x").roles(role))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/activities").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/planning").with(user("staff").roles("STAFF")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"roadFactor\":1.4,\"averageSpeedKmh\":40,\"bufferMinutes\":10,\"lunchMinutes\":60}")).andExpect(status().isForbidden());
        String plan = "{\"date\":\"2030-01-07\",\"startTime\":\"09:00\",\"endTime\":\"17:00\",\"adults\":2,\"childAges\":[],\"residency\":\"SA_RESIDENT\","
            + "\"interests\":[],\"startArea\":\"CITY_CENTRE\",\"transport\":\"OWN_VEHICLE\",\"maxStops\":3,\"onlyPublishedPrices\":false,\"mustInclude\":[]}";
        mvc.perform(post("/api/v1/itineraries").contentType(MediaType.APPLICATION_JSON).content(plan)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/itineraries").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(plan)).andExpect(status().isOk());
        verify(activityAdmin).list();
        verify(planningService, never()).update(anyLong(), any(), anyInt(), anyInt(), anyInt());
    }

    @Test void assistantIsPublicButCsrfProtectedAndBoundToTheVisitorSession() throws Exception {
        org.mockito.Mockito.when(assistant.status()).thenReturn(new com.sltours.assistant.dto.AssistantDtos.Status(false,
            com.sltours.assistant.dto.AssistantDtos.Mode.DISABLED, "Use the day planner"));
        mvc.perform(get("/api/v1/assistant/status")).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("DISABLED"));
        mvc.perform(post("/api/v1/assistant/conversations")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/assistant/conversations/abc/messages").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
            .andExpect(status().isForbidden());
        var session = new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/api/v1/assistant/conversations/abc/messages").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"hi\"}")).andExpect(status().isOk());
        org.mockito.Mockito.verify(assistant).send(org.mockito.ArgumentMatchers.eq("abc"), org.mockito.ArgumentMatchers.eq(session.getId()),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("hi"));
        mvc.perform(post("/api/v1/assistant/conversations/abc/messages").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/assistant/conversations/abc/enquiry").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"optionId\":\"A\",\"name\":\"V\",\"email\":\"v@example.com\",\"confirmed\":false,\"idempotencyKey\":\"" + ID + "\"}"))
            .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(assistant, org.mockito.Mockito.never()).submit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
        mvc.perform(delete("/api/v1/assistant/conversations/abc").with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/enquiries")).andExpect(status().isUnauthorized());
    }

    @Test void partnerRecordsAreForPartnersAndMoneyAndPartnerAdministrationAreForAdministrators() throws Exception {
        for (var other : List.of(user("customer@test.local").roles("CUSTOMER"), user("driver@test.local").roles("DRIVER"), user("staff").roles("STAFF"),
                user("admin").roles("ADMIN"))) {
            mvc.perform(get("/api/v1/partner/me").with(other)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/partner/referrals").with(other)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/partner/statement")).andExpect(status().isUnauthorized());
        var partner = user("partner@test.local").roles("PARTNER");
        mvc.perform(get("/api/v1/admin/partners").with(partner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/bookings/SLT-2026-AAAAAA/finance").with(partner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operations/bookings").with(partner)).andExpect(status().isForbidden());
        // Staff can view a booking's finances but cannot record money, change attribution or administer partners.
        var staff = user("staff").roles("STAFF");
        mvc.perform(get("/api/v1/admin/bookings/SLT-2026-AAAAAA/finance").with(staff)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/bookings/SLT-2026-AAAAAA/payments").with(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"idempotencyKey\":\"" + ID + "\",\"kind\":\"PAYMENT\",\"amountCents\":100,\"method\":\"EFT\",\"externalReference\":\"X\",\"receivedOn\":\"2026-10-03\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/bookings/SLT-2026-AAAAAA/referral").with(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"partnerId\":null,\"reason\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/partners").with(staff)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/partners").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        // Applications are public but CSRF-protected; referral lookups are public reads.
        mvc.perform(post("/api/v1/partner-applications").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/partner-applications").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        org.mockito.Mockito.when(referralService.lookup("ABCD2345")).thenReturn(Optional.of(new com.sltours.partners.dto.PartnerDtos.ReferralLookup("ABCD2345", "Hotel")));
        mvc.perform(get("/api/v1/partners/referrals/ABCD2345")).andExpect(status().isOk()).andExpect(jsonPath("$.partnerName").value("Hotel"));
    }
}
