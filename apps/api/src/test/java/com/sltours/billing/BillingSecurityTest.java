package com.sltours.billing;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sltours.accounts.repository.AccountRepository;
import com.sltours.accounts.service.AccountService;
import com.sltours.billing.controller.*;
import com.sltours.billing.service.*;
import com.sltours.config.SecurityConfig;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Who may do what with invoices, quotations, payments, links and billing settings, through the real security chain. */
@WebMvcTest(controllers = { AdminBillingController.class, PublicDocumentController.class, CustomerDocumentController.class })
@Import(SecurityConfig.class)
@TestPropertySource(properties = { "sltours.cors.allowed-origins=http://localhost:5173" })
class BillingSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean BillingDocumentService documents;
    @MockBean BillingPaymentService payments;
    @MockBean BillingShareService sharing;
    @MockBean BillingSettingsService settings;
    @MockBean CustomerDocumentService customerDocuments;
    @MockBean QuotationBookingService quotationBookings;
    @MockBean AccountRepository accounts;
    @MockBean AccountService accountService;

    private static final UUID ID = UUID.randomUUID();
    private static final String VERSION = "{\"version\":0}";

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) { return b.contentType(MediaType.APPLICATION_JSON).content(body); }

    /** Administrator-only actions: issuing, voiding, revising, converting, confirming money and changing settings. */
    static List<MockHttpServletRequestBuilder> adminOnly() {
        return List.of(
            json(post("/api/v1/admin/billing/documents/" + ID + "/issue"), VERSION),
            json(post("/api/v1/admin/billing/documents/" + ID + "/void"), "{\"version\":0,\"reason\":\"x\"}"),
            post("/api/v1/admin/billing/documents/" + ID + "/revise"),
            post("/api/v1/admin/billing/documents/" + ID + "/convert"),
            json(post("/api/v1/admin/billing/documents/" + ID + "/decision"), "{\"version\":0,\"decision\":\"ACCEPTED\"}"),
            json(post("/api/v1/admin/billing/payments/" + ID + "/confirm"), VERSION),
            json(post("/api/v1/admin/billing/payments/" + ID + "/reject"), "{\"version\":0,\"reason\":\"x\"}"),
            json(put("/api/v1/admin/billing/settings/numbering/INVOICE"), "{\"nextNumber\":100100,\"confirmed\":true}"),
            delete("/api/v1/admin/billing/settings/logo"));
    }

    @Test void anonymousVisitorsAndOtherRolesCannotReachBilling() throws Exception {
        mvc.perform(get("/api/v1/admin/billing/documents")).andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "DRIVER", "PARTNER")) {
            mvc.perform(get("/api/v1/admin/billing/documents").with(user("x").roles(role))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/billing/documents/" + ID + "/pdf").with(user("x").roles(role))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/billing/settings").with(user("x").roles(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(documents, payments, sharing, settings);
    }

    @Test void staffCanDraftDownloadShareAndRecordPaymentsButNotIssueVoidConfirmOrChangeSettings() throws Exception {
        mvc.perform(get("/api/v1/admin/billing/documents").with(user("staff@test.local").roles("STAFF"))).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/admin/billing/documents/" + ID + "/share"), "{\"channel\":\"WHATSAPP\",\"expiresInDays\":7}")
            .with(user("staff@test.local").roles("STAFF")).with(csrf())).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/admin/billing/documents/" + ID + "/payments"),
            "{\"idempotencyKey\":\"" + UUID.randomUUID() + "\",\"amountCents\":1000,\"paidOn\":\"2026-07-15\",\"method\":\"EFT\"}")
            .with(user("staff@test.local").roles("STAFF")).with(csrf())).andExpect(status().isOk());
        for (MockHttpServletRequestBuilder request : adminOnly())
            mvc.perform(request.with(user("staff@test.local").roles("STAFF")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(json(put("/api/v1/admin/billing/settings"), "{}").with(user("staff@test.local").roles("STAFF")).with(csrf()))
            .andExpect(status().is4xxClientError());
        verify(documents, never()).issue(any(), anyLong());
        verify(documents, never()).voidDocument(any(), any());
        verify(payments, never()).confirm(any(), any());
        verify(settings, never()).update(any());
        verify(settings, never()).updateSequence(any(), any());
    }

    @Test void writesNeedTheCsrfToken() throws Exception {
        mvc.perform(json(post("/api/v1/admin/billing/documents/" + ID + "/issue"), VERSION).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/admin/billing/documents/" + ID + "/issue"), VERSION).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
    }

    @Test void customerLinksAreOpenWithoutSigningInButCustomerListsNeedACustomerAccount() throws Exception {
        when(sharing.open(anyString())).thenReturn(new BillingShareService.PublicFile("%PDF-1.7".getBytes(), "INV-100099.pdf"));
        mvc.perform(get("/api/v1/public/documents/" + "a".repeat(43))).andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
            .andExpect(header().string("Cache-Control", "private, no-store"));
        mvc.perform(get("/api/v1/account/documents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/documents").with(user("staff").roles("STAFF"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/account/documents").with(user("c@example.com").roles("CUSTOMER"))).andExpect(status().isOk());
    }
}
