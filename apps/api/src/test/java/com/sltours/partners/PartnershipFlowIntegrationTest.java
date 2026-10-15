package com.sltours.partners;
import com.sltours.activities.model.ActivityEnums.*;
import com.sltours.activities.service.ActivityService;
import com.sltours.bookings.dto.CreateBookingRequest;
import com.sltours.bookings.model.*;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.bookings.service.BookingService;
import com.sltours.operations.service.OperationsService;
import com.sltours.partners.dto.PartnerDtos.*;
import com.sltours.partners.model.PartnerEnums.*;
import com.sltours.partners.service.*;
import com.sltours.payments.service.BookingPaymentService;
import com.sltours.seo.SeoService;
import com.sltours.shared.error.ConflictException;
import com.sltours.support.PostgresIntegrationTest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The complete partnership flow against PostgreSQL and the real security chain: application, approval, partner login,
 * referral, payment and completion, commission, refund adjustment and payout, plus duplicate-processing and
 * cross-partner checks. Commercial values below are test inputs, not SL Tours terms.
 */
@AutoConfigureMockMvc
class PartnershipFlowIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PartnerApplicationService applications;
    @Autowired PartnerAdminService admin;
    @Autowired PartnerFinanceService finance;
    @Autowired SupplierProposalService proposals;
    @Autowired ReferralService referrals;
    @Autowired BookingService bookingService;
    @Autowired BookingRepository bookings;
    @Autowired BookingPaymentService payments;
    @Autowired OperationsService operations;
    @Autowired ActivityService activities;
    @Autowired com.sltours.activities.service.ActivityAdminService activityAdmin;
    @Autowired SeoService seo;
    @Autowired JdbcTemplate jdbc;

    private static final String PASSWORD = "partner-test-password";

    private UUID apply(String business, String email, Set<Route> routes) {
        var receipt = applications.apply(new PartnerApplication(UUID.randomUUID(), routes, business, BusinessType.HOTEL, "Contact Person", email,
            "+27 21 000 0000", "https://example.org", "Sea Point", null, null, "We host visitors in Cape Town.",
            routes.contains(Route.SUPPLIER) ? "Guided township food walks" : null, PASSWORD, true));
        return jdbc.queryForObject("select id from partners where reference = ?", UUID.class, receipt.reference());
    }
    private MockHttpSession login(String email, int expectedStatus) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("username", email).param("password", PASSWORD)).andExpect(status().is(expectedStatus)).andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }
    private String book(String code, String email, OffsetDateTime capturedAt) {
        var r = bookingService.createBooking(new CreateBookingRequest("cape-peninsula-journey", LocalDate.now().plusDays(10), 6, "Guest Name", email,
            "+27 82 000 0000", "South Africa", "Hotel lobby", null, code == null ? null : new ReferralInput(code, capturedAt, ReferralSource.LINK)));
        return r.reference();
    }
    private void complete(String reference) {
        operations.updateStatus(reference, BookingStatus.CONFIRMED, bookings.findByReference(reference).orElseThrow().getVersion());
        operations.updateStatus(reference, BookingStatus.COMPLETED, bookings.findByReference(reference).orElseThrow().getVersion());
    }
    private BookingFinance pay(String reference, PaymentKind kind, int cents, UUID key) {
        return payments.record(reference, new NewPayment(key, kind, cents, "EFT", "BANK-" + key.toString().substring(0, 6), LocalDate.now(), null));
    }
    private NewAgreement referralAgreement(String ref, LocalDate from, RefundTreatment refunds) {
        return new NewAgreement(Route.REFERRAL, ref, from, null, CommissionType.PERCENTAGE, 1000, null, EligibleBasis.NET_AMOUNT_PAID,
            Set.of(BookingType.TOUR), 30, refunds, null, null, null, "Test terms");
    }

    @Test void applicationApprovalLoginReferralCommissionRefundAndPayout() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String emailA = "hotel-a-" + suffix + "@example.com", emailB = "hotel-b-" + suffix + "@example.com";
        UUID a = apply("Harbour Hotel " + suffix, emailA, Set.of(Route.REFERRAL));
        UUID b = apply("Mountain Guesthouse " + suffix, emailB, Set.of(Route.REFERRAL, Route.SUPPLIER));

        // Not approved yet: no login, no referral code.
        login(emailA, 401);
        assertThat(admin.detail(a).referralCode()).isNull();
        assertThatThrownBy(() -> admin.decide(a, new RouteDecision(Route.REFERRAL, Decision.REJECT, " "))).hasMessageContaining("reason");

        PartnerDetail approved = admin.decide(a, new RouteDecision(Route.REFERRAL, Decision.APPROVE, null));
        String codeA = approved.referralCode();
        assertThat(codeA).matches("[A-Z2-9]{8}");
        assertThat(approved.loginEnabled()).isTrue();
        admin.decide(b, new RouteDecision(Route.REFERRAL, Decision.APPROVE, null));
        admin.decide(b, new RouteDecision(Route.SUPPLIER, Decision.APPROVE, null));
        String codeB = admin.detail(b).referralCode();

        MockHttpSession sessionA = login(emailA, 204);
        mvc.perform(get("/api/v1/partner/me").session(sessionA)).andExpect(status().isOk())
            .andExpect(jsonPath("$.referralCode").value(codeA)).andExpect(jsonPath("$.referralLink").value("https://sl-tours.com/?ref=" + codeA));
        mvc.perform(get("/api/v1/admin/partners").session(sessionA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/partners/referrals/" + codeA.toLowerCase(Locale.ROOT))).andExpect(status().isOk())
            .andExpect(jsonPath("$.partnerName").value("Harbour Hotel " + suffix));

        // Attributed, completed and paid, but no agreement yet: nothing is earned.
        String early = book(codeA, "early-" + suffix + "@example.com", OffsetDateTime.now().minusDays(1));
        int earlyTotal = bookings.findByReference(early).orElseThrow().getTotalAmountCents();
        pay(early, PaymentKind.PAYMENT, earlyTotal, UUID.randomUUID());
        complete(early);
        assertThat(payments.finance(early).commissions()).isEmpty();
        assertThat(payments.finance(early).attribution().commissionStatus()).contains("no referral agreement was in force");

        admin.addAgreement(a, referralAgreement("AGR-A-1", LocalDate.now(PartnerSupport.ZONE).minusDays(1), RefundTreatment.PROPORTIONAL));
        assertThatThrownBy(() -> admin.addAgreement(a, referralAgreement("AGR-A-2", LocalDate.now(PartnerSupport.ZONE), RefundTreatment.PROPORTIONAL)))
            .isInstanceOf(ConflictException.class).hasMessageContaining("overlap");

        // Referral, payment and completion.
        String ref = book(codeA, "guest-" + suffix + "@example.com", OffsetDateTime.now().minusDays(2));
        int total = bookings.findByReference(ref).orElseThrow().getTotalAmountCents();
        assertThat(total).isPositive();
        UUID paymentKey = UUID.randomUUID();
        pay(ref, PaymentKind.PAYMENT, total, paymentKey);
        pay(ref, PaymentKind.PAYMENT, total, paymentKey); // retry: one payment only
        assertThat(payments.finance(ref).payments()).hasSize(1);
        assertThat(payments.finance(ref).netPaidCents()).isEqualTo(total);
        assertThat(payments.finance(ref).commissions()).isEmpty(); // paid but not yet completed
        complete(ref);

        BookingFinance afterCompletion = payments.finance(ref);
        int expected = java.math.BigDecimal.valueOf(total).multiply(java.math.BigDecimal.valueOf(1000))
            .divide(java.math.BigDecimal.valueOf(10_000), 0, java.math.RoundingMode.HALF_UP).intValueExact();
        assertThat(afterCompletion.commissions()).singleElement().satisfies(c -> {
            assertThat(c.kind()).isEqualTo(CommissionKind.EARNED);
            assertThat(c.amountCents()).isEqualTo(expected);
            assertThat(c.basisAmountCents()).isEqualTo(total);
            assertThat(c.agreementReference()).isEqualTo("AGR-A-1");
            assertThat(c.calculation()).contains("10.00% of", "net amount paid", "AGR-A-1");
        });

        // Re-evaluating many times, including concurrently, never creates a second earned line.
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> runs = new ArrayList<>();
        for (int i = 0; i < 8; i++) runs.add(pool.submit(() -> payments.reevaluate(ref)));
        for (Future<?> f : runs) f.get(30, TimeUnit.SECONDS);
        assertThat(payments.finance(ref).commissions()).hasSize(1);

        // Commission is locked in once calculated: the referral cannot be moved to another partner.
        assertThatThrownBy(() -> referrals.changeBookingAttribution(ref, new ChangeAttribution(b, "Guest says partner B referred them")))
            .isInstanceOf(ConflictException.class);

        // Partial refund: proportional adjustment, once per refund even when retried.
        int refund = total / 4;
        UUID refundKey = UUID.randomUUID();
        pay(ref, PaymentKind.REFUND, refund, refundKey);
        pay(ref, PaymentKind.REFUND, refund, refundKey);
        BookingFinance afterRefund = payments.finance(ref);
        int adjustment = java.math.BigDecimal.valueOf(expected).multiply(java.math.BigDecimal.valueOf(refund))
            .divide(java.math.BigDecimal.valueOf(total), 0, java.math.RoundingMode.HALF_UP).intValueExact();
        assertThat(afterRefund.commissions()).extracting(CommissionLine::kind, CommissionLine::amountCents)
            .containsExactly(tuple(CommissionKind.EARNED, expected), tuple(CommissionKind.REFUND_ADJUSTMENT, -adjustment));
        assertThat(afterRefund.netPaidCents()).isEqualTo(total - refund);
        assertThatThrownBy(() -> pay(ref, PaymentKind.REFUND, total, UUID.randomUUID())).hasMessageContaining("cannot exceed");

        // Payout of the net balance; retries return the same payout; a second payout cannot claim the same lines.
        List<UUID> lines = afterRefund.commissions().stream().map(CommissionLine::id).toList();
        assertThatThrownBy(() -> finance.recordPayout(a, new NewPayout(UUID.randomUUID(), lines, expected, LocalDate.now(), "EFT", "PAY-1", null)))
            .hasMessageContaining("must equal");
        UUID payoutKey = UUID.randomUUID();
        var payout = finance.recordPayout(a, new NewPayout(payoutKey, lines, expected - adjustment, LocalDate.now(), "EFT", "PAY-1", null));
        assertThat(finance.recordPayout(a, new NewPayout(payoutKey, lines, expected - adjustment, LocalDate.now(), "EFT", "PAY-1", null)).id()).isEqualTo(payout.id());
        assertThatThrownBy(() -> finance.recordPayout(a, new NewPayout(UUID.randomUUID(), lines, expected - adjustment, LocalDate.now(), "EFT", "PAY-2", null)))
            .isInstanceOf(ConflictException.class);
        assertThat(finance.unpaid(a)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from partner_payouts where partner_id = ?", Integer.class, a)).isEqualTo(1);

        // Refund of the rest and a refunded booking: the remaining commission is reversed and carried as owed back.
        pay(ref, PaymentKind.REFUND, total - refund, UUID.randomUUID());
        operations.updateStatus(ref, BookingStatus.REFUNDED, bookings.findByReference(ref).orElseThrow().getVersion());
        BookingFinance refunded = payments.finance(ref);
        assertThat(refunded.commissions().stream().mapToInt(CommissionLine::amountCents).sum()).isZero();
        assertThat(finance.unpaid(a)).isEqualTo(-(expected - adjustment));

        // Partner view: own references and coarse status only, no customer details.
        String referralsJson = mvc.perform(get("/api/v1/partner/referrals").session(sessionA)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(referralsJson).contains(ref, early, "Refunded").doesNotContain("guest-" + suffix, "Guest Name", "+27 82", "Hotel lobby");
        String statement = mvc.perform(get("/api/v1/partner/statement").session(sessionA)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(statement).contains("PAY-1", "AGR-A-1");

        // Partner B cannot see partner A's referrals or statement.
        MockHttpSession sessionB = login(emailB, 204);
        String bReferrals = mvc.perform(get("/api/v1/partner/referrals").session(sessionB)).andReturn().getResponse().getContentAsString();
        assertThat(bReferrals).doesNotContain(ref, early);
        assertThat(mvc.perform(get("/api/v1/partner/statement").session(sessionB)).andReturn().getResponse().getContentAsString()).doesNotContain("PAY-1");

        // Self-referral and an unknown code earn nothing; a suspended partner's code is no longer accepted.
        String self = book(codeA, emailA, OffsetDateTime.now().minusHours(1));
        pay(self, PaymentKind.PAYMENT, bookings.findByReference(self).orElseThrow().getTotalAmountCents(), UUID.randomUUID());
        complete(self);
        assertThat(payments.finance(self).attribution().commissionStatus()).contains("self-referral");
        assertThat(payments.finance(book("NOSUCHCODE", "x-" + suffix + "@example.com", null)).attribution()).isNull();
        admin.decide(b, new RouteDecision(Route.REFERRAL, Decision.SUSPEND, "Under review"));
        mvc.perform(get("/api/v1/partners/referrals/" + codeB)).andExpect(status().isNotFound());
        assertThat(payments.finance(book(codeB, "y-" + suffix + "@example.com", null)).attribution()).isNull();

        // Attribution can be set by an administrator, with a reason, on a booking without commission.
        String manual = book(null, "z-" + suffix + "@example.com", null);
        referrals.changeBookingAttribution(manual, new ChangeAttribution(a, "Concierge called on the guest's behalf"));
        assertThat(payments.finance(manual).attribution().source()).isEqualTo(ReferralSource.ADMIN);
        assertThat(payments.finance(manual).attributionHistory()).singleElement().satisfies(h -> assertThat(h.reason()).contains("Concierge"));

        // Every money and attribution step is in the audit log.
        assertThat(jdbc.queryForList("select distinct action from admin_audit_events where action like 'PARTNER%' or action like 'COMMISSION%' or action like 'REFERRAL%' or action like 'PAYMENT%' or action like 'REFUND%'", String.class))
            .contains("PARTNER_APPLIED", "PARTNER_REFERRAL_APPROVED", "PARTNER_AGREEMENT_RECORDED", "COMMISSION_EARNED", "COMMISSION_ADJUSTED",
                "PARTNER_PAYOUT_RECORDED", "REFERRAL_ATTRIBUTION_CHANGED", "PAYMENT_RECORDED", "REFUND_RECORDED", "PARTNER_REFERRAL_SUSPENDED");
    }

    @Test void concurrentPayoutsCannotPayTheSameCommissionTwice() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        UUID p = apply("Concierge Desk " + suffix, "desk-" + suffix + "@example.com", Set.of(Route.REFERRAL));
        String code = admin.decide(p, new RouteDecision(Route.REFERRAL, Decision.APPROVE, null)).referralCode();
        admin.addAgreement(p, referralAgreement("AGR-C-1", LocalDate.now(PartnerSupport.ZONE).minusDays(1), RefundTreatment.FULL_REVERSAL));
        String ref = book(code, "c-" + suffix + "@example.com", OffsetDateTime.now().minusMinutes(5));
        pay(ref, PaymentKind.PAYMENT, bookings.findByReference(ref).orElseThrow().getTotalAmountCents(), UUID.randomUUID());
        complete(ref);
        CommissionLine earned = payments.finance(ref).commissions().get(0);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> attempts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            int n = i;
            attempts.add(pool.submit(() -> { start.await();
                try { return finance.recordPayout(p, new NewPayout(UUID.randomUUID(), List.of(earned.id()), earned.amountCents(), LocalDate.now(), "EFT", "RACE-" + n, null)); }
                catch (ConflictException e) { return e; } }));
        }
        start.countDown();
        List<Object> results = new ArrayList<>();
        for (var f : attempts) results.add(f.get(30, TimeUnit.SECONDS));
        assertThat(results).filteredOn(PayoutView.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(ConflictException.class::isInstance).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from partner_payouts where partner_id = ?", Integer.class, p)).isEqualTo(1);

        // FULL_REVERSAL: any refund reverses the remaining commission.
        pay(ref, PaymentKind.REFUND, 100, UUID.randomUUID());
        assertThat(payments.finance(ref).commissions().stream().mapToInt(CommissionLine::amountCents).sum()).isZero();

        // A completed booking marked REFUNDED (refund handled outside the ledger) reverses what remains, once.
        String second = book(code, "d-" + suffix + "@example.com", OffsetDateTime.now().minusMinutes(5));
        pay(second, PaymentKind.PAYMENT, bookings.findByReference(second).orElseThrow().getTotalAmountCents(), UUID.randomUUID());
        complete(second);
        operations.updateStatus(second, BookingStatus.REFUNDED, bookings.findByReference(second).orElseThrow().getVersion());
        payments.reevaluate(second);
        assertThat(payments.finance(second).commissions()).extracting(CommissionLine::kind).containsExactly(CommissionKind.EARNED, CommissionKind.REVERSAL);
        assertThat(payments.finance(second).commissions().stream().mapToInt(CommissionLine::amountCents).sum()).isZero();
    }

    @Test void agreementsAreFixedOnceRecordedAndCommercialTermsAreValidated() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        UUID p = apply("Agent " + suffix, "agent-" + suffix + "@example.com", Set.of(Route.REFERRAL));
        assertThatThrownBy(() -> admin.addAgreement(p, new NewAgreement(Route.REFERRAL, "X", LocalDate.now(), null, CommissionType.PERCENTAGE, null, null,
            EligibleBasis.BOOKING_TOTAL, Set.of(BookingType.TOUR), 30, RefundTreatment.PROPORTIONAL, null, null, null, null))).hasMessageContaining("percentage rate");
        assertThatThrownBy(() -> admin.addAgreement(p, new NewAgreement(Route.SUPPLIER, "X", LocalDate.now(), null, null, null, null, null, null, null, null,
            null, null, "Net rates", null))).hasMessageContaining("not applied");
        assertThatThrownBy(() -> admin.addAgreement(p, new NewAgreement(Route.REFERRAL, "X", LocalDate.now(), null, null, null, null, null, null, null, null,
            null, null, null, null))).hasMessageContaining("Referral agreements need");
        PartnerDetail d = admin.addAgreement(p, referralAgreement("AGR-E-1", LocalDate.now(PartnerSupport.ZONE).minusDays(10), RefundTreatment.PROPORTIONAL));
        UUID agreementId = d.agreements().get(0).id();
        admin.endAgreement(p, agreementId, new EndAgreement(LocalDate.now(PartnerSupport.ZONE).minusDays(1)));
        assertThat(admin.detail(p).agreements().get(0).effectiveTo()).isEqualTo(LocalDate.now(PartnerSupport.ZONE).minusDays(1));
        // A later agreement may start after the earlier one ends.
        admin.addAgreement(p, new NewAgreement(Route.REFERRAL, "AGR-E-2", LocalDate.now(PartnerSupport.ZONE), null, CommissionType.FIXED_PER_BOOKING, null, 5000,
            EligibleBasis.BOOKING_TOTAL, Set.of(BookingType.TOUR, BookingType.PRIVATE_TRIP), 14, RefundTreatment.FULL_REVERSAL, null, null, null, null));
        assertThat(admin.detail(p).agreements()).hasSize(2);
    }

    @Test void supplierProposalsNeedApprovalAndTheCatalogueVerificationWorkflow() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "guide-" + suffix + "@example.com";
        UUID supplier = apply("Bo-Kaap Walks " + suffix, email, Set.of(Route.SUPPLIER));
        UUID other = apply("Other Supplier " + suffix, "other-" + suffix + "@example.com", Set.of(Route.SUPPLIER));
        var input = new ProposalInput("Bo-Kaap Food Walk " + suffix, Category.FOOD_MARKET, Area.CITY_CENTRE, "A guided food walk through the Bo-Kaap.",
            "Three hours of tastings with a local guide.", 180, "Wale Street, Bo-Kaap", "https://example.org/walk", "R650 per adult, R300 per child",
            "Daily at 10:00", "Ages 8 and up", null);
        assertThatThrownBy(() -> proposals.submit(supplier, input)).hasMessageContaining("not approved");
        admin.decide(supplier, new RouteDecision(Route.SUPPLIER, Decision.APPROVE, null));
        admin.decide(other, new RouteDecision(Route.SUPPLIER, Decision.APPROVE, null));
        assertThat(admin.detail(supplier).referralCode()).isNull(); // supplier route alone has no referral code

        MockHttpSession session = login(email, 204);
        String created = mvc.perform(post("/api/v1/partner/proposals").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(input))).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        UUID proposalId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"));
        MockHttpSession otherSession = login("other-" + suffix + "@example.com", 204);
        mvc.perform(get("/api/v1/partner/proposals/" + proposalId).session(otherSession)).andExpect(status().isNotFound());

        ProposalView approved = proposals.decide(proposalId, new ProposalDecisionRequest(0L, ProposalDecision.APPROVE, null));
        assertThat(approved.status()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(approved.activityStatus()).isEqualTo("DRAFT");
        // Commercial approval verifies nothing: no prices, no verification date, participation unknown, not public.
        var draft = jdbc.queryForMap("select id, price_status, last_verified_on, participation_rules, supplier_partner_id, verification_notes from activities where slug = ?",
            approved.activitySlug());
        assertThat(draft.get("price_status")).isEqualTo("NOT_PUBLISHED");
        assertThat(draft.get("last_verified_on")).isNull();
        assertThat(draft.get("participation_rules")).isEqualTo("UNKNOWN");
        assertThat(draft.get("supplier_partner_id")).isEqualTo(supplier);
        assertThat((String) draft.get("verification_notes")).contains("NOT VERIFIED", "R650 per adult");
        assertThat(jdbc.queryForObject("select count(*) from activity_price_rules where activity_id = ?", Integer.class, draft.get("id"))).isZero();
        assertThatThrownBy(() -> activities.get(approved.activitySlug())).hasMessageContaining("not found");
        UUID activityId = (UUID) draft.get("id");
        var adminView = activityAdmin.get(activityId);
        assertThat(adminView.publishProblems()).isNotEmpty();
        assertThatThrownBy(() -> activityAdmin.setStatus(activityId, adminView.version(), Status.PUBLISHED)).hasMessageContaining("Cannot publish yet");

        // After the normal verification steps it can be published, with the commercial relationship disclosed.
        var src = UUID.randomUUID();
        var save = new com.sltours.activities.dto.ActivityDtos.SaveActivityRequest(adminView.version(), adminView.slug(), adminView.title(), adminView.category(),
            adminView.summary(), adminView.description(), adminView.area(), adminView.address(), adminView.officialUrl(), adminView.durationMinutes(),
            ScheduleType.SCHEDULE_VARIES, false, false, null, null, null, PriceStatus.VARIES_AT_BOOKING, adminView.verificationNotes(), null, null, null, null,
            List.of(new com.sltours.activities.dto.ActivityDtos.Source(src, "https://example.org/walk", "Bo-Kaap Walks", SourceType.OFFICIAL_OPERATOR,
                "Description and meeting point", LocalDate.now())), List.of(), List.of(), List.of(), List.of(), ParticipationRules.UNKNOWN,
            "Age limits not verified", com.sltours.support.CatalogueTestData.capeTown(com.sltours.activities.model.ActivityEnums.Theme.HERITAGE));
        var saved = activityAdmin.update(activityId, save);
        var verified = activityAdmin.verify(activityId, saved.version());
        var published = activityAdmin.setStatus(activityId, verified.version(), Status.PUBLISHED);
        try {
            assertThat(activities.get(approved.activitySlug()).supplierName()).isEqualTo("Bo-Kaap Walks " + suffix);
            assertThat(seo.body("/explore/" + approved.activitySlug())).contains("commercial relationship");
        } finally {
            activityAdmin.setStatus(activityId, published.version(), Status.ARCHIVED);
        }
    }
}
