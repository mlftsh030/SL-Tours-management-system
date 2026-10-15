package com.sltours.billing.model;
import com.sltours.billing.model.BillingTypes.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A payment against an invoice. It counts towards the balance only once an administrator confirms the funds. */
@Entity
@Table(name = "billing_payments")
public class BillingPayment {
    @Id private UUID id = UUID.randomUUID();
    @Version private long version;
    @Column(nullable = false) private UUID documentId;
    @Column(nullable = false) private long amountCents;
    @Column(nullable = false) private LocalDate paidOn;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PaymentMethod method;
    @Column(length = 120) private String reference;
    @Column(length = 500) private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PaymentState status = PaymentState.PENDING;
    @Column(unique = true) private Long receiptNumber;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String receiptSnapshot;
    private UUID receiptPdfMediaId;
    @Column(nullable = false, unique = true) private UUID idempotencyKey;
    @Column(nullable = false, length = 180) private String recordedBy;
    @Column(nullable = false) private OffsetDateTime recordedAt = OffsetDateTime.now();
    @Column(length = 180) private String decidedBy;
    private OffsetDateTime decidedAt;
    @Column(length = 500) private String rejectReason;
    @Column(length = 180) private String reversedBy;
    private OffsetDateTime reversedAt;
    @Column(length = 500) private String reverseReason;

    protected BillingPayment() {}
    public BillingPayment(UUID documentId, long amountCents, LocalDate paidOn, PaymentMethod method, String reference, String notes,
                          UUID idempotencyKey, String actor) {
        this.documentId = documentId; this.amountCents = amountCents; this.paidOn = paidOn; this.method = method;
        this.reference = reference; this.notes = notes; this.idempotencyKey = idempotencyKey; recordedBy = actor;
    }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID v) { documentId = v; }
    public long getAmountCents() { return amountCents; }
    public LocalDate getPaidOn() { return paidOn; }
    public PaymentMethod getMethod() { return method; }
    public String getReference() { return reference; }
    public String getNotes() { return notes; }
    public PaymentState getStatus() { return status; }
    public Long getReceiptNumber() { return receiptNumber; }
    public String getReceiptSnapshot() { return receiptSnapshot; }
    public UUID getReceiptPdfMediaId() { return receiptPdfMediaId; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public String getRecordedBy() { return recordedBy; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public String getDecidedBy() { return decidedBy; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public String getRejectReason() { return rejectReason; }

    public void confirm(long receiptNumber, String snapshotJson, UUID pdfMediaId, String actor) {
        status = PaymentState.CONFIRMED; this.receiptNumber = receiptNumber; receiptSnapshot = snapshotJson; receiptPdfMediaId = pdfMediaId;
        decidedBy = actor; decidedAt = OffsetDateTime.now();
    }
    public void reverse(String reason, String actor) { status = PaymentState.REVERSED; reverseReason = reason; reversedBy = actor; reversedAt = OffsetDateTime.now(); }
    public String getReverseReason() { return reverseReason; }
    public String getReversedBy() { return reversedBy; }
    public OffsetDateTime getReversedAt() { return reversedAt; }
    public void reject(String reason, String actor) { status = PaymentState.REJECTED; rejectReason = reason; decidedBy = actor; decidedAt = OffsetDateTime.now(); }
}
