package com.sltours.accounts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sltours.billing.BillingFixtures;
import com.sltours.billing.dto.BillingDtos.*;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.service.*;
import com.sltours.bookings.dto.CreateBookingRequest;
import com.sltours.bookings.service.BookingService;
import com.sltours.notifications.EmailSender;
import com.sltours.notifications.EmailSender.Delivery;
import com.sltours.support.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Customer sign-up, verification, sign-in, password reset and record ownership against PostgreSQL and the real security
 * chain. Only the outgoing email is replaced, so the test can read the links a customer would receive.
 */
@AutoConfigureMockMvc
class CustomerAccountFlowIntegrationTest extends PostgresIntegrationTest {
    private static final AtomicInteger ADDRESS = new AtomicInteger(10);
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]{43})");
    private static final String PASSWORD = "customer-password-1";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingService bookings;
    @Autowired BillingDocumentService documents;
    @Autowired BillingSettingsService settings;
    @Autowired NumberAllocator numbers;
    @MockBean EmailSender mail;

    @BeforeEach void mailAndNumbering() {
        when(mail.isEnabled()).thenReturn(true);
        when(mail.send(anyString(), anyString(), anyString())).thenReturn(Delivery.SENT);
        asAdmin(() -> { for (var s : NumberAllocator.Series.values()) settings.updateSequence(s, new SequenceUpdate(numbers.highestUsed(s) + 1, true)); return null; });
    }

    // Each request comes from its own address so the per-address limiter never interferes with the flow under test.
    private static RequestPostProcessor fresh() {
        String ip = "10.9." + (ADDRESS.get() / 250) + "." + (ADDRESS.getAndIncrement() % 250 + 1);
        return r -> { r.setRemoteAddr(ip); return r; };
    }
    private MockHttpServletRequestBuilder jsonPost(String url, Object body) throws Exception {
        return post(url).with(csrf()).with(fresh()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }
    private static String email() { return "customer" + UUID.randomUUID().toString().substring(0, 8) + "@example.com"; }
    private JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }

    private JsonNode register(String email, Map<String, Object> extra) throws Exception {
        Map<String, Object> b = new HashMap<>(Map.of("name", "Thandi Customer", "email", email, "password", PASSWORD, "passwordConfirmation", PASSWORD));
        b.putAll(extra);
        return body(mvc.perform(jsonPost("/api/v1/auth/register", b)).andExpect(status().isCreated()));
    }
    private MockHttpSession login(String email, String password, int expected) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf()).with(fresh()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("username", email).param("password", password)).andExpect(status().is(expected)).andReturn().getRequest().getSession();
    }
    /** The token in the last email sent to this address. */
    private String lastToken(String email) {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(email), anyString(), captor.capture());
        Matcher m = TOKEN.matcher(captor.getAllValues().get(captor.getAllValues().size() - 1));
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
    private static <T> T asAdmin(java.util.function.Supplier<T> work) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@test.local", null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try { return work.get(); } finally { SecurityContextHolder.clearContext(); }
    }
    private DocumentDetail issuedInvoice(String customerEmail, String bookingReference) {
        return asAdmin(() -> {
            DocumentContent c = new DocumentContent(new Customer("Thandi Customer", null, customerEmail, null), null, LocalDate.now().plusDays(7),
                null, null, null, null, null, null, null, BillingFixtures.youngLife().lines(), null, false, null, null, null);
            DocumentDetail d = documents.create(new SaveDocument(Kind.INVOICE, UUID.randomUUID(), null, bookingReference, c, null));
            return documents.issue(d.summary().id(), d.version());
        });
    }
    private String guestBooking(String email) {
        return bookings.createBooking(new CreateBookingRequest("cape-peninsula-journey", LocalDate.now().plusDays(20), 6, "Guest", email,
            null, null, null, null, null)).reference();
    }
    private Map<String, Object> enquiry(String email) {
        return Map.of("idempotencyKey", UUID.randomUUID(), "customerName", "Thandi", "customerEmail", email, "message", "We would like a quote for 4 people");
    }

    @Test void publicRegistrationOnlyEverCreatesAnUnverifiedCustomer() throws Exception {
        String email = email();
        JsonNode result = register(email.toUpperCase(Locale.ROOT), Map.of("role", "ADMIN", "roles", List.of("ADMIN", "STAFF"), "emailVerified", true,
            "phone", "+27 82 555 1234"));
        assertThat(result.get("email").asText()).isEqualTo(email);
        assertThat(result.get("verificationEmail").asText()).isEqualTo("SENT");
        Map<String, Object> row = jdbc.queryForMap("select role, email_verified_at, phone from user_accounts where email = ?", email);
        assertThat(row.get("role")).isEqualTo("CUSTOMER");
        assertThat(row.get("email_verified_at")).isNull();
        assertThat(row.get("phone")).isEqualTo("+27 82 555 1234");

        MockHttpSession session = login(email, PASSWORD, 204);
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(jsonPath("$.role").value("CUSTOMER")).andExpect(jsonPath("$.emailVerified").value(false));
        mvc.perform(get("/api/v1/admin/bookings").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/driver/assignments").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/partner/summary").session(session)).andExpect(status().isForbidden());
    }

    @Test void duplicateAndInvalidRegistrationsAreRefusedWithUsefulMessages() throws Exception {
        String email = email();
        register(email, Map.of());
        mvc.perform(jsonPost("/api/v1/auth/register", Map.of("name", "Someone", "email", email.toUpperCase(Locale.ROOT), "password", PASSWORD)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already exists")));
        mvc.perform(jsonPost("/api/v1/auth/register", Map.of("name", "A", "email", email(), "password", PASSWORD, "passwordConfirmation", "different-password-1")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("The passwords do not match"));
        mvc.perform(jsonPost("/api/v1/auth/register", Map.of("name", "A", "email", email(), "password", PASSWORD, "phone", "082 555 1234")))
            .andExpect(status().isBadRequest());
        mvc.perform(jsonPost("/api/v1/auth/register", Map.of("name", "A", "email", email(), "password", "short")))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from user_accounts where email = ?", Integer.class, email)).isEqualTo(1);
    }

    @Test void guestRecordsStayHiddenUntilTheEmailAddressIsVerified() throws Exception {
        String email = email();
        String booking = guestBooking(email);
        mvc.perform(jsonPost("/api/v1/enquiries", enquiry(email))).andExpect(status().isCreated());
        DocumentDetail looseInvoice = issuedInvoice(email, null);

        register(email, Map.of());
        MockHttpSession session = login(email, PASSWORD, 204);
        mvc.perform(get("/api/v1/account/bookings").session(session)).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/v1/account/enquiries").session(session)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/account/documents").session(session)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/account/documents/" + looseInvoice.summary().id() + "/pdf").session(session)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/account/guest-records").session(session).with(csrf())).andExpect(status().isForbidden());

        String token = lastToken(email);
        mvc.perform(jsonPost("/api/v1/auth/verify-email", Map.of("token", token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.linked.bookings").value(1)).andExpect(jsonPath("$.linked.enquiries").value(1)).andExpect(jsonPath("$.linked.documents").value(1));
        mvc.perform(jsonPost("/api/v1/auth/verify-email", Map.of("token", token))).andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(jsonPath("$.emailVerified").value(true));
        mvc.perform(get("/api/v1/account/bookings").session(session)).andExpect(jsonPath("$.content[0].reference").value(booking));
        mvc.perform(get("/api/v1/account/enquiries").session(session)).andExpect(jsonPath("$[0].message").value("We would like a quote for 4 people"));
        mvc.perform(get("/api/v1/account/documents").session(session)).andExpect(jsonPath("$[0].id").value(looseInvoice.summary().id().toString()));
        mvc.perform(get("/api/v1/account/documents/" + looseInvoice.summary().id() + "/pdf").session(session)).andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        // Later guest records with the verified address can be linked on request.
        guestBooking(email);
        mvc.perform(post("/api/v1/account/guest-records").session(session).with(csrf())).andExpect(jsonPath("$.bookings").value(1));
    }

    @Test void recordsMadeWhileSignedInBelongToTheCustomerAndNobodyElse() throws Exception {
        String alice = email(), bob = email();
        register(alice, Map.of()); register(bob, Map.of());
        MockHttpSession a = login(alice, PASSWORD, 204), b = login(bob, PASSWORD, 204);

        mvc.perform(post("/api/v1/enquiries").session(a).with(csrf()).with(fresh()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(enquiry("typed-differently@example.com")))).andExpect(status().isCreated());
        String booking = body(mvc.perform(post("/api/v1/bookings").session(a).with(csrf()).with(fresh()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new CreateBookingRequest("cape-peninsula-journey", LocalDate.now().plusDays(30), 6, "Alice", alice, null, null, null, null, null))))
            .andExpect(status().isCreated())).get("reference").asText();
        DocumentDetail invoice = issuedInvoice(alice, booking);

        mvc.perform(get("/api/v1/account/enquiries").session(a)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/account/bookings").session(a)).andExpect(jsonPath("$.content[0].reference").value(booking));
        mvc.perform(get("/api/v1/account/documents").session(a)).andExpect(jsonPath("$[0].canUploadProof").value(true));

        String id = invoice.summary().id().toString();
        mvc.perform(get("/api/v1/account/enquiries").session(b)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/account/bookings").session(b)).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/v1/account/documents").session(b)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/account/documents/" + id + "/pdf").session(b)).andExpect(status().isNotFound());
        MockMultipartFile proof = new MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-1.4\n%%EOF\n".getBytes());
        mvc.perform(multipart("/api/v1/account/documents/" + id + "/proof").file(proof).session(b).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/account/documents/" + id + "/pdf")).andExpect(status().isUnauthorized());

        // The owner can send proof; staff see it on the invoice and the balance does not change.
        mvc.perform(multipart("/api/v1/account/documents/" + id + "/proof").file(proof).session(a).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.proofsUploaded").value(1)).andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        mvc.perform(multipart("/api/v1/account/documents/" + id + "/proof").file(new MockMultipartFile("file", "x.txt", "text/plain", "hello".getBytes()))
            .session(a).with(csrf())).andExpect(status().isBadRequest());
        DocumentDetail staffView = asAdmin(() -> documents.get(invoice.summary().id()));
        assertThat(staffView.attachments()).singleElement().satisfies(x -> assertThat(x.uploadedBy()).isEqualTo(alice));
        assertThat(staffView.events()).extracting(EventView::event).contains("CUSTOMER_PROOF_UPLOADED");
        assertThat(staffView.summary().paidCents()).isZero();

        // Staff and administrators keep their access; customers never reach it.
        mvc.perform(get("/api/v1/admin/billing/documents/" + id).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .user("staff@test.local").roles("STAFF"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/billing/documents/" + id).session(a)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/account/profile").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .user("staff@test.local").roles("STAFF"))).andExpect(status().isForbidden());
    }

    @Test void passwordResetChangesThePasswordOnceAndSignsOutEveryOtherSession() throws Exception {
        String email = email();
        register(email, Map.of());
        MockHttpSession old = login(email, PASSWORD, 204);
        clearInvocations(mail);

        mvc.perform(jsonPost("/api/v1/auth/password-reset/request", Map.of("email", "nobody-" + email))).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.delivery").value("SENT"));
        verify(mail, never()).send(anyString(), anyString(), anyString());
        mvc.perform(jsonPost("/api/v1/auth/password-reset/request", Map.of("email", email.toUpperCase(Locale.ROOT)))).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.delivery").value("SENT"));
        String token = lastToken(email);

        mvc.perform(jsonPost("/api/v1/auth/password-reset/confirm", Map.of("token", token, "password", "a-new-password-22", "passwordConfirmation", "other")))
            .andExpect(status().isBadRequest());
        mvc.perform(jsonPost("/api/v1/auth/password-reset/confirm", Map.of("token", token, "password", "a-new-password-22", "passwordConfirmation", "a-new-password-22")))
            .andExpect(status().isNoContent());
        mvc.perform(jsonPost("/api/v1/auth/password-reset/confirm", Map.of("token", token, "password", "another-password-33")))
            .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/account/profile").session(old)).andExpect(status().isUnauthorized());
        login(email, PASSWORD, 401);
        MockHttpSession fresh = login(email, "a-new-password-22", 204);
        // Opening the emailed link proved control of the address.
        mvc.perform(get("/api/v1/account/profile").session(fresh)).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test void expiredAndOutdatedLinksAreRefused() throws Exception {
        String email = email();
        register(email, Map.of());
        String first = lastToken(email);
        jdbc.update("update account_tokens set expires_at = now() - interval '1 minute' where account_id = (select id from user_accounts where email = ?)", email);
        mvc.perform(jsonPost("/api/v1/auth/verify-email", Map.of("token", first))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expired")));
        mvc.perform(jsonPost("/api/v1/auth/verify-email", Map.of("token", "not-a-token"))).andExpect(status().isBadRequest());

        MockHttpSession session = login(email, PASSWORD, 204);
        jdbc.update("update account_tokens set created_at = now() - interval '2 minutes' where account_id = (select id from user_accounts where email = ?)", email);
        mvc.perform(post("/api/v1/account/verification-email").session(session).with(csrf()).with(fresh())).andExpect(jsonPath("$.delivery").value("SENT"));
        mvc.perform(post("/api/v1/account/verification-email").session(session).with(csrf()).with(fresh())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("wait a minute")));
        mvc.perform(jsonPost("/api/v1/auth/verify-email", Map.of("token", lastToken(email)))).andExpect(status().isOk());
    }

    @Test void profileUpdatesAndSignOutEndTheSession() throws Exception {
        String email = email();
        register(email, Map.of());
        MockHttpSession session = login(email, PASSWORD, 204);
        mvc.perform(put("/api/v1/account/profile").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Thandi M\",\"phone\":\"+44 7700 900123\",\"email\":\"taken@example.com\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Thandi M")).andExpect(jsonPath("$.email").value(email));
        mvc.perform(put("/api/v1/account/profile").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Thandi M\",\"phone\":\"0825551234\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/account/profile").session(session)).andExpect(status().isUnauthorized());
    }

    @Test void withoutAnEmailProviderTheSiteSaysSoInsteadOfPretending() throws Exception {
        when(mail.isEnabled()).thenReturn(false);
        String email = email();
        assertThat(register(email, Map.of()).get("verificationEmail").asText()).isEqualTo("NOT_CONFIGURED");
        mvc.perform(jsonPost("/api/v1/auth/password-reset/request", Map.of("email", email))).andExpect(jsonPath("$.delivery").value("NOT_CONFIGURED"));
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }
}
