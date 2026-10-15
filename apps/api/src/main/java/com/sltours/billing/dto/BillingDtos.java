package com.sltours.billing.dto;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.render.DocumentView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class BillingDtos {
    private BillingDtos() {}

    // ---- Documents ---------------------------------------------------------------------------------------------

    /** Create (kind and idempotency key required) or update a draft (version required). */
    /** {@code enquiryReference}: the enquiry this document answers (optional; links the enquiry to the quotation and its booking). */
    public record SaveDocument(Kind kind, UUID idempotencyKey, Long version, @Size(max = 40) String bookingReference,
                               @Valid @NotNull DocumentContent content, @Size(max = 4000) String internalNotes, @Size(max = 32) String enquiryReference) {
        public SaveDocument(Kind kind, UUID idempotencyKey, Long version, String bookingReference, DocumentContent content, String internalNotes) {
            this(kind, idempotencyKey, version, bookingReference, content, internalNotes, null);
        }
    }
    public record BookingFromQuotation(UUID bookingId, String bookingReference, boolean created) {}

    public record Preview(@NotNull Kind kind, @Valid @NotNull DocumentContent content, UUID id) {}
    public record PreviewResult(Totals totals, DocumentView view, List<String> warnings) {}

    public record Versioned(@NotNull @Min(0) Long version) {}
    public record VoidRequest(@NotNull @Min(0) Long version, @NotBlank(message = "Give a reason for voiding") @Size(max = 500) String reason) {}
    public record Decision(@NotNull @Min(0) Long version, @NotNull Status decision) {}

    public record DocumentSummary(UUID id, Kind kind, Status status, Long number, String displayNumber, String customerName,
                                  String customerEmail, String bookingReference, LocalDate issueDate, LocalDate dueDate,
                                  LocalDate serviceStart, String currency, long totalCents, long paidCents, long balanceCents,
                                  PaymentStatus paymentStatus, boolean overdue, OffsetDateTime updatedAt) {}

    public record Related(UUID id, Kind kind, Status status, String displayNumber) {}

    public record DocumentDetail(DocumentSummary summary, long version, DocumentContent content, String internalNotes, Totals totals,
                                 DocumentView view, List<PaymentView> payments, long pendingCents, List<AttachmentView> attachments,
                                 List<ShareLinkView> links, List<EventView> events, Related sourceQuotation, Related convertedInvoice,
                                 Related revisionOf, Related supersededBy, Related pendingRevision, String createdBy,
                                 OffsetDateTime createdAt, String issuedBy, OffsetDateTime issuedAt, String voidedBy, String voidReason,
                                 String pdfFilename, String docxFilename, boolean numberingConfirmed,
                                 /** The enquiry this answers and the booking it belongs to (for a quotation: the booking created from it). */
                                 String enquiryReference, String linkedBookingReference, UUID customerId) {}

    public record ConversionResult(UUID invoiceId, boolean created) {}

    // ---- Payments ----------------------------------------------------------------------------------------------

    public record NewPayment(@NotNull UUID idempotencyKey,
                             @NotNull @Positive(message = "Enter the amount received") Long amountCents,
                             @NotNull(message = "Enter the payment date") LocalDate paidOn,
                             @NotNull PaymentMethod method,
                             @Size(max = 120) String reference, @Size(max = 500) String notes) {}
    public record PaymentDecision(@NotNull @Min(0) Long version, @Size(max = 500) String reason) {}

    public record PaymentView(UUID id, long version, long amountCents, LocalDate paidOn, PaymentMethod method, String reference,
                              String notes, PaymentState status, Long receiptNumber, String receiptFilename, String recordedBy,
                              OffsetDateTime recordedAt, String decidedBy, OffsetDateTime decidedAt, String rejectReason,
                              List<AttachmentView> attachments, String reversedBy, OffsetDateTime reversedAt, String reverseReason) {}
    public record AttachmentView(UUID id, UUID paymentId, String url, String filename, String contentType, long sizeBytes,
                                 String label, String uploadedBy, OffsetDateTime uploadedAt) {}

    // ---- Sharing -----------------------------------------------------------------------------------------------

    public record ShareRequest(@NotNull Channel channel, @Min(1) @Max(90) Integer expiresInDays, UUID paymentId) {}
    public record ShareResult(UUID linkId, String url, OffsetDateTime expiresAt, String message, String subject,
                              String whatsappUrl, String mailtoUrl, String filename, boolean customerNumberMissing,
                              boolean customerEmailMissing) {}
    public enum ShareAction { WHATSAPP_OPENED, EMAIL_OPENED, NATIVE_SHARED, PDF_DOWNLOADED, DOCX_DOWNLOADED, LINK_COPIED }
    public record ShareEvent(@NotNull ShareAction action, Channel channel, UUID paymentId) {}
    public record ShareLinkView(UUID id, UUID paymentId, Channel channel, String createdBy, OffsetDateTime createdAt,
                                OffsetDateTime expiresAt, OffsetDateTime revokedAt, String revokedBy, OffsetDateTime lastOpenedAt,
                                int openCount, boolean active) {}
    public record EventView(String event, Channel channel, String actor, String detail, OffsetDateTime createdAt, UUID paymentId) {}

    /** What the server can do itself. Nothing is sent automatically unless a provider is configured. */
    public record Capabilities(boolean whatsappApiConfigured, boolean emailConfigured, boolean nativeShareRecommended,
                               int defaultLinkDays, int maxLinkDays, String whatsappNote, String emailNote) {}

    // ---- Settings ----------------------------------------------------------------------------------------------

    public record SequenceView(String kind, long nextNumber, boolean confirmed, long highestUsed, String updatedBy, OffsetDateTime updatedAt) {}

    public record SettingsView(long version, String companyName, String tagline, String phone, String whatsapp, String email,
                               String website, String address, boolean addressNeedsReview, String logoUrl, boolean customLogo,
                               String bankAccountName, String bankName, String bankAccountNumber, String bankAccountType,
                               String bankBranchCode, String bankSwiftCode, String paymentInstructions, String invoiceTerms,
                               String quotationTerms, String currency, boolean taxEnabled, String taxLabel,
                               Integer taxRateBasisPoints, String taxNumber, Integer defaultDueDays, Integer quotationValidDays,
                               List<SequenceView> numbering, String updatedBy, OffsetDateTime updatedAt) {}

    public record SettingsUpdate(
        @NotNull @Min(0) Long version,
        @NotBlank @Size(max = 160) String companyName, @Size(max = 240) String tagline,
        @Size(max = 60) String phone, @Size(max = 60) String whatsapp,
        @Email @Size(max = 180) String email, @Size(max = 180) String website,
        @NotBlank @Size(max = 400) String address, boolean addressNeedsReview,
        @Size(max = 160) String bankAccountName, @Size(max = 120) String bankName,
        @Size(max = 40) @Pattern(regexp = "^$|^[0-9 ]{4,40}$", message = "Use digits only") String bankAccountNumber,
        @Size(max = 60) String bankAccountType, @Size(max = 20) String bankBranchCode, @Size(max = 20) String bankSwiftCode,
        @Size(max = 500) String paymentInstructions,
        @NotBlank @Size(max = 4000) String invoiceTerms, @NotBlank @Size(max = 4000) String quotationTerms,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        boolean taxEnabled, @Size(max = 20) String taxLabel, @Min(0) @Max(10000) Integer taxRateBasisPoints, @Size(max = 40) String taxNumber,
        @Min(0) @Max(365) Integer defaultDueDays, @Min(1) @Max(365) Integer quotationValidDays) {}

    public record SequenceUpdate(@NotNull @Min(1) @Max(999_999_999) Long nextNumber, boolean confirmed) {}

    public record CustomerSuggestion(String name, String contactPerson, String email, String whatsapp, String source, String bookingReference) {}

    public record BookingPrefill(UUID bookingId, String bookingReference, DocumentContent content, List<String> notes) {}
    public record EnquiryPrefill(String enquiryReference, DocumentContent content, List<String> notes) {}

    // ---- Customer account --------------------------------------------------------------------------------------

    public record CustomerDocument(UUID id, Kind kind, Status status, String displayNumber, LocalDate issueDate, LocalDate dueDate,
                                   String currency, long totalCents, long paidCents, long balanceCents, PaymentStatus paymentStatus,
                                   String bookingReference, String filename, boolean canUploadProof, int proofsUploaded,
                                   OffsetDateTime lastProofAt) {}
}
