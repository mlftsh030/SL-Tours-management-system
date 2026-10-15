package com.sltours.billing.model;
import com.sltours.billing.model.BillingTypes.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A quotation or invoice. {@code content} is the editable draft as JSON; {@code snapshot} is the frozen issued copy.
 * The list columns (customer, dates, totals) are copied from the content so documents can be searched and filtered.
 */
@Entity
@Table(name = "billing_documents")
public class BillingDocument {
    @Id private UUID id = UUID.randomUUID();
    @Version private long version;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.DRAFT;
    private Long number;
    private UUID bookingId;
    private UUID accountId;
    private UUID sourceQuotationId;
    /** The enquiry this document answers, when it was created from one. */
    private UUID enquiryId;
    private UUID revisionOfId;
    private UUID supersededById;
    @Column(nullable = false, length = 200) private String customerName;
    @Column(length = 180) private String customerEmail;
    @Column(length = 40) private String customerWhatsapp;
    @Column(length = 40) private String bookingReference;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDate serviceStart;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false) private long subtotalCents;
    @Column(nullable = false) private long discountCents;
    @Column(nullable = false) private long taxCents;
    @Column(nullable = false) private long totalCents;
    @Column(nullable = false) private long paidCents;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PaymentStatus paymentStatus;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String content;
    @Column(columnDefinition = "text") private String internalNotes;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String snapshot;
    private UUID issuedPdfMediaId;
    @Column(unique = true) private UUID idempotencyKey;
    @Column(nullable = false, length = 180) private String createdBy;
    @Column(nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(nullable = false, length = 180) private String updatedBy;
    @Column(nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();
    @Column(length = 180) private String issuedBy;
    private OffsetDateTime issuedAt;
    @Column(length = 180) private String decidedBy;
    private OffsetDateTime decidedAt;
    @Column(length = 180) private String voidedBy;
    private OffsetDateTime voidedAt;
    @Column(length = 500) private String voidReason;

    protected BillingDocument() {}
    public BillingDocument(Kind kind, String actor) {
        this.kind = kind; createdBy = actor; updatedBy = actor;
        paymentStatus = kind == Kind.INVOICE ? PaymentStatus.UNPAID : PaymentStatus.NOT_APPLICABLE;
    }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public Kind getKind() { return kind; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { status = v; }
    public Long getNumber() { return number; }
    public UUID getEnquiryId() { return enquiryId; }
    public void setEnquiryId(UUID v) { enquiryId = v; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID v) { bookingId = v; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID v) { accountId = v; }
    public UUID getSourceQuotationId() { return sourceQuotationId; }
    public void setSourceQuotationId(UUID v) { sourceQuotationId = v; }
    public UUID getRevisionOfId() { return revisionOfId; }
    public void setRevisionOfId(UUID v) { revisionOfId = v; }
    public UUID getSupersededById() { return supersededById; }
    public void setSupersededById(UUID v) { supersededById = v; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerWhatsapp() { return customerWhatsapp; }
    public String getBookingReference() { return bookingReference; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getServiceStart() { return serviceStart; }
    public String getCurrency() { return currency; }
    public long getSubtotalCents() { return subtotalCents; }
    public long getDiscountCents() { return discountCents; }
    public long getTaxCents() { return taxCents; }
    public long getTotalCents() { return totalCents; }
    public long getPaidCents() { return paidCents; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public String getContent() { return content; }
    public String getInternalNotes() { return internalNotes; }
    public void setInternalNotes(String v) { internalNotes = v; }
    public String getSnapshot() { return snapshot; }
    public UUID getIssuedPdfMediaId() { return issuedPdfMediaId; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID v) { idempotencyKey = v; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getIssuedBy() { return issuedBy; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public String getDecidedBy() { return decidedBy; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public String getVoidedBy() { return voidedBy; }
    public OffsetDateTime getVoidedAt() { return voidedAt; }
    public String getVoidReason() { return voidReason; }

    /** Stores the edited content and the list columns derived from it. */
    public void applyContent(String json, DocumentContent c, Totals totals, String actor) {
        content = json;
        customerName = c.customer().name().trim();
        customerEmail = blankToNull(c.customer().email());
        customerWhatsapp = blankToNull(c.customer().whatsapp());
        bookingReference = blankToNull(c.bookingReference());
        issueDate = c.issueDate(); dueDate = c.dueDate(); serviceStart = c.serviceStart();
        currency = c.currency();
        if (totals != null) { subtotalCents = totals.subtotal(); discountCents = totals.discount(); taxCents = totals.tax(); totalCents = totals.total(); }
        touch(actor);
    }

    public void issue(long number, String snapshotJson, UUID pdfMediaId, LocalDate issueDate, LocalDate dueDate, String actor) {
        this.number = number; snapshot = snapshotJson; issuedPdfMediaId = pdfMediaId; this.issueDate = issueDate; this.dueDate = dueDate;
        status = Status.ISSUED; issuedBy = actor; issuedAt = OffsetDateTime.now(); touch(actor);
    }

    public void decide(Status decision, String actor) { status = decision; decidedBy = actor; decidedAt = OffsetDateTime.now(); touch(actor); }

    public void voidDocument(String reason, String actor) {
        status = Status.VOID; voidReason = reason; voidedBy = actor; voidedAt = OffsetDateTime.now(); touch(actor);
    }

    public void setPaid(long paid) {
        paidCents = paid;
        if (kind == Kind.INVOICE) paymentStatus = paid <= 0 ? PaymentStatus.UNPAID : paid >= totalCents ? PaymentStatus.PAID : PaymentStatus.PARTIALLY_PAID;
    }

    public void touch(String actor) { updatedBy = actor; updatedAt = OffsetDateTime.now(); }

    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
