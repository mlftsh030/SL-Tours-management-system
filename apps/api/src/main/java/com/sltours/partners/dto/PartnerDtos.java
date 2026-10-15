package com.sltours.partners.dto;
import com.sltours.activities.model.ActivityEnums.*;
import com.sltours.partners.model.PartnerEnums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class PartnerDtos {
    private PartnerDtos() {}

    /** Optional referral sent with public bookings and enquiries. capturedAt is reported by the visitor's browser. */
    public record ReferralInput(@NotBlank @Size(max = 20) String code, OffsetDateTime capturedAt, @NotNull ReferralSource source) {}

    // ----- public -----
    public record PartnerApplication(@NotNull UUID idempotencyKey, @NotEmpty @Size(max = 2) Set<@NotNull Route> routes,
        @NotBlank @Size(max = 160) String businessName, @NotNull BusinessType businessType, @NotBlank @Size(max = 160) String contactName,
        @NotBlank @Email @Size(max = 180) String email, @Size(max = 80) String phone,
        @Size(max = 300) @Pattern(regexp = "(https?://.+)?", message = "Use a full http(s) link") String website, @Size(max = 160) String area,
        @Size(max = 80) String registrationNumber, @Size(max = 600) String licences, @NotBlank @Size(max = 2000) String description,
        @Size(max = 2000) String servicesOffered, @NotBlank @Size(min = 12, max = 72) String password,
        @AssertTrue(message = "Confirm that SL Tours may use these details to assess your application") boolean privacyConsent) {}
    public record ApplicationReceipt(String reference, String message, boolean duplicate) {}
    public record ReferralLookup(String code, String partnerName) {}

    // ----- admin -----
    public record RouteView(Route route, RouteStatus status, String decidedBy, OffsetDateTime decidedAt, String reason) {}
    public record PartnerSummary(UUID id, String reference, String businessName, BusinessType businessType, String area, List<RouteView> routes,
        boolean hasReferralAgreement, long payableCents, OffsetDateTime createdAt) {}
    public record AgreementView(UUID id, Route route, String agreementReference, LocalDate effectiveFrom, LocalDate effectiveTo, CommissionType commissionType,
        Integer rateBasisPoints, Integer fixedAmountCents, EligibleBasis eligibleBasis, String eligibleBookingTypes, Integer attributionWindowDays,
        RefundTreatment refundTreatment, Integer minimumEligibleCents, Integer maximumCommissionCents, String supplierTerms, String notes,
        String recordedBy, OffsetDateTime recordedAt, String endedBy, OffsetDateTime endedAt) {}
    public record NewAgreement(@NotNull Route route, @NotBlank @Size(max = 80) String agreementReference, @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo, CommissionType commissionType, @Min(1) @Max(10000) Integer rateBasisPoints, @Min(1) Integer fixedAmountCents,
        EligibleBasis eligibleBasis, Set<com.sltours.bookings.model.BookingType> eligibleBookingTypes, @Min(1) @Max(3650) Integer attributionWindowDays,
        RefundTreatment refundTreatment, @Min(0) Integer minimumEligibleCents, @Min(1) Integer maximumCommissionCents,
        @Size(max = 2000) String supplierTerms, @Size(max = 2000) String notes) {}
    public record EndAgreement(@NotNull LocalDate effectiveTo) {}
    public record RouteDecision(@NotNull Route route, @NotNull Decision decision, @Size(max = 600) String reason) {}
    /** Admin view of an attributed booking or enquiry, including the customer (admins only). */
    public record AdminReferral(String kind, String reference, String customerName, String status, ReferralSource source, OffsetDateTime capturedAt,
        OffsetDateTime createdAt, int totalAmountCents, int netPaidCents) {}
    public record CommissionLine(UUID id, String bookingReference, CommissionKind kind, int amountCents, int basisAmountCents, String calculation,
        String agreementReference, UUID payoutId, String payoutReference, OffsetDateTime createdAt) {}
    public record PayoutView(UUID id, int amountCents, LocalDate paidOn, String method, String paymentReference, String notes, String recordedBy,
        OffsetDateTime recordedAt) {}
    public record NewPayout(@NotNull UUID idempotencyKey, @NotEmpty @Size(max = 500) List<@NotNull UUID> commissionIds, @Min(1) int amountCents,
        @NotNull LocalDate paidOn, @NotBlank @Size(max = 60) String method, @NotBlank @Size(max = 120) String paymentReference, @Size(max = 1000) String notes) {}
    public record SupplierPaymentView(UUID id, int amountCents, LocalDate paidOn, String method, String paymentReference, String bookingReference,
        String description, String recordedBy, OffsetDateTime recordedAt) {}
    public record NewSupplierPayment(@NotNull UUID idempotencyKey, @Min(1) int amountCents, @NotNull LocalDate paidOn, @NotBlank @Size(max = 60) String method,
        @NotBlank @Size(max = 120) String paymentReference, @Size(max = 32) String bookingReference, @NotBlank @Size(max = 600) String description) {}
    public record PartnerDetail(UUID id, long version, String reference, String businessName, BusinessType businessType, String contactName, String email,
        String phone, String website, String area, String registrationNumber, String licences, String description, String servicesOffered,
        String referralCode, boolean loginEnabled, OffsetDateTime createdAt, List<RouteView> routes, List<AgreementView> agreements,
        List<AdminReferral> referrals, List<CommissionLine> commissions, List<PayoutView> payouts, long unpaidCents, List<SupplierPaymentView> supplierPayments,
        List<ProposalView> proposals) {}

    // ----- bookings: payments and attribution (admin) -----
    public record PaymentView(UUID id, PaymentKind kind, int amountCents, String method, String externalReference, LocalDate receivedOn,
        String recordedBy, OffsetDateTime recordedAt) {}
    public record NewPayment(@NotNull UUID idempotencyKey, @NotNull PaymentKind kind, @Min(1) int amountCents, @NotBlank @Size(max = 60) String method,
        @NotBlank @Size(max = 120) String externalReference, @NotNull LocalDate receivedOn, @Size(max = 600) String notes) {}
    public record AttributionView(UUID partnerId, String partnerReference, String partnerName, String code, ReferralSource source,
        OffsetDateTime capturedAt, String commissionStatus) {}
    public record ChangeAttribution(UUID partnerId, @NotBlank @Size(max = 600) String reason) {}
    public record BookingFinance(String reference, String status, int totalAmountCents, int netPaidCents, List<PaymentView> payments,
        AttributionView attribution, List<CommissionLine> commissions, List<AttributionChange> attributionHistory) {}
    public record AttributionChange(String fromPartner, String toPartner, String reason, String changedBy, OffsetDateTime changedAt) {}

    // ----- supplier proposals -----
    public record ProposalInput(@NotBlank @Size(max = 180) String title, @NotNull Category category, @NotNull Area area,
        @NotBlank @Size(max = 400) String summary, @NotBlank @Size(max = 4000) String description, @Min(15) @Max(720) int durationMinutes,
        @Size(max = 300) String address, @Size(max = 500) @Pattern(regexp = "(https?://.+)?", message = "Use a full http(s) link") String website,
        @NotBlank @Size(max = 2000) String proposedPrices, @NotBlank @Size(max = 2000) String proposedSchedule,
        @NotBlank @Size(max = 1000) String participationInfo, @Size(max = 2000) String supplierNotes) {}
    public record ProposalView(UUID id, long version, String reference, String partnerName, ProposalStatus status, String title, Category category,
        Area area, String summary, String description, int durationMinutes, String address, String website, String proposedPrices,
        String proposedSchedule, String participationInfo, String supplierNotes, String decisionReason, OffsetDateTime decidedAt,
        String activitySlug, String activityStatus, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record ProposalDecisionRequest(@NotNull Long version, @NotNull ProposalDecision decision, @Size(max = 600) String reason) {}

    // ----- partner portal (own records only, no customer details) -----
    public record PortalAgreement(Route route, String agreementReference, LocalDate effectiveFrom, LocalDate effectiveTo, String terms) {}
    public record PortalProfile(String reference, String businessName, List<RouteView> routes, String referralCode, String referralLink,
        List<PortalAgreement> agreements) {}
    /** A referral as the partner sees it: reference and coarse status only. */
    public record PortalReferral(String kind, String reference, String product, LocalDate receivedOn, String status, String commission) {}
    public record PortalStatement(List<CommissionLine> commissions, List<PayoutView> payouts, long unpaidCents, List<SupplierPaymentView> supplierPayments) {}
    public record ProposalSubmit(@Valid @NotNull ProposalInput proposal) {}
}
