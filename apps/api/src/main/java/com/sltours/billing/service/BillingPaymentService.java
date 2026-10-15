package com.sltours.billing.service;
import com.sltours.billing.dto.BillingDtos.*;
import com.sltours.billing.model.*;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.render.*;
import com.sltours.media.*;
import com.sltours.shared.error.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Payments against issued invoices. Staff record a payment and attach proof; it stays PENDING and does not reduce the
 * balance until an administrator confirms the funds have cleared. Confirming allocates a receipt number and freezes
 * the receipt. Amounts can never take the paid total above the invoice total.
 */
@Service
public class BillingPaymentService {
    private final BillingDocumentRepository documents;
    private final BillingPaymentRepository payments;
    private final BillingDocumentService documentService;
    private final BillingSettingsService settings;
    private final NumberAllocator numbers;
    private final MediaService media;
    private final BillingSupport support;
    private final JdbcTemplate jdbc;
    private final PdfRenderer pdf;
    private final DocxRenderer docx;
    private final com.sltours.payments.service.BookingPaymentService bookingLedger;

    public BillingPaymentService(BillingDocumentRepository documents, BillingPaymentRepository payments, BillingDocumentService documentService,
            BillingSettingsService settings, NumberAllocator numbers, MediaService media, BillingSupport support, JdbcTemplate jdbc,
            com.sltours.payments.service.BookingPaymentService bookingLedger) {
        this.bookingLedger = bookingLedger;
        this.documents = documents; this.payments = payments; this.documentService = documentService; this.settings = settings;
        this.numbers = numbers; this.media = media; this.support = support; this.jdbc = jdbc;
        this.pdf = new PdfRenderer(settings); this.docx = new DocxRenderer(settings);
    }

    @Transactional
    public DocumentDetail record(UUID documentId, NewPayment r) {
        BillingDocument doc = documents.lockById(documentId).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        Optional<BillingPayment> existing = payments.findByIdempotencyKey(r.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().getDocumentId().equals(documentId)) throw new ConflictException("This payment was already recorded on another document");
            return documentService.detail(doc);
        }
        if (doc.getKind() != Kind.INVOICE || doc.getStatus() != Status.ISSUED) throw new IllegalArgumentException("Payments are recorded against issued invoices");
        if (r.paidOn().isAfter(LocalDate.now(BillingSupport.ZONE))) throw new IllegalArgumentException("The payment date cannot be in the future");
        long pending = sum(documentId, PaymentState.PENDING);
        long open = doc.getTotalCents() - doc.getPaidCents() - pending;
        if (r.amountCents() > open)
            throw new IllegalArgumentException("This is more than the " + MoneyFormat.withCurrency(Math.max(open, 0), doc.getCurrency()) + " still open"
                + (pending > 0 ? " (" + MoneyFormat.withCurrency(pending, doc.getCurrency()) + " is already waiting for confirmation)" : ""));
        BillingPayment p = new BillingPayment(documentId, r.amountCents(), r.paidOn(), r.method(), trim(r.reference()), trim(r.notes()),
            r.idempotencyKey(), BillingSupport.actor());
        payments.saveAndFlush(p);
        support.event(documentId, p.getId(), "PAYMENT_RECORDED", null, MoneyFormat.withCurrency(r.amountCents(), doc.getCurrency()) + " "
            + DocumentViews.methodLabel(r.method()) + (p.getReference() == null ? "" : " ref " + p.getReference()) + "; waiting for confirmation");
        return documentService.detail(doc);
    }

    @Transactional
    public DocumentDetail attachProof(UUID paymentId, MultipartFile file, String label) {
        BillingPayment p = payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (p.getStatus() == PaymentState.REJECTED) throw new IllegalArgumentException("This payment was rejected");
        MediaFile stored = media.store(file, MediaPurpose.PAYMENT_PROOF, MediaFile.Visibility.PRIVATE, MediaService.DOCUMENTS);
        jdbc.update("insert into billing_attachments (id, document_id, payment_id, media_id, label, uploaded_by, uploaded_at) values (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), p.getDocumentId(), p.getId(), stored.getId(), trim(label), BillingSupport.actor(), OffsetDateTime.now());
        support.event(p.getDocumentId(), p.getId(), "PROOF_ATTACHED", null, "Proof of payment attached (" + stored.getContentType() + "). Evidence only; funds still need confirming.");
        return documentService.get(p.getDocumentId());
    }

    @Transactional
    public DocumentDetail confirm(UUID paymentId, PaymentDecision r) {
        BillingPayment p = payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        BillingDocument doc = documents.lockById(p.getDocumentId()).orElseThrow();
        if (p.getStatus() == PaymentState.CONFIRMED) return documentService.detail(doc);
        if (p.getStatus() != PaymentState.PENDING) throw new IllegalArgumentException("This payment was " + p.getStatus().name().toLowerCase(Locale.ROOT));
        if (p.getVersion() != r.version()) throw new ConflictException("This payment changed. Reload and try again.");
        if (doc.getStatus() != Status.ISSUED) throw new IllegalArgumentException("The invoice is " + doc.getStatus().name().toLowerCase(Locale.ROOT));
        long paid = doc.getPaidCents() + p.getAmountCents();
        if (paid > doc.getTotalCents()) throw new IllegalArgumentException("Confirming this would take payments above the invoice total");
        long receiptNumber = numbers.next(NumberAllocator.Series.RECEIPT);
        IssuedSnapshot invoice = support.read(doc.getSnapshot(), IssuedSnapshot.class);
        ReceiptSnapshot receipt = new ReceiptSnapshot(receiptNumber, LocalDate.now(BillingSupport.ZONE), doc.getKind(), doc.getNumber(),
            invoice.content().customer(), doc.getBookingReference(), doc.getCurrency(), p.getAmountCents(), p.getPaidOn(), p.getMethod(), p.getReference(),
            doc.getTotalCents(), paid, doc.getTotalCents() - paid, settings.company(settings.current()), BillingSupport.actor(), OffsetDateTime.now());
        byte[] file = pdf.render(DocumentViews.receipt(receipt, null));
        MediaFile stored = media.storeGenerated(file, "REC-" + receiptNumber + ".pdf", MediaPurpose.BILLING_DOCUMENT, MediaService.PDF_ONLY);
        p.confirm(receiptNumber, support.write(receipt), stored.getId(), BillingSupport.actor());
        doc.setPaid(paid);
        doc.touch(BillingSupport.actor());
        payments.flush(); documents.flush();
        support.event(doc.getId(), p.getId(), "PAYMENT_CONFIRMED", null, MoneyFormat.withCurrency(p.getAmountCents(), doc.getCurrency())
            + " confirmed as received; receipt " + receiptNumber + "; balance " + MoneyFormat.withCurrency(doc.getTotalCents() - paid, doc.getCurrency()));
        allocate(doc, p, false);
        return documentService.detail(doc);
    }

    /**
     * Reverses a confirmed payment that did not arrive after all (bounced, charged back, or recorded in error). The
     * invoice balance goes back up, the receipt stays on file marked as reversed, and the booking ledger takes back what
     * the payment had allocated. Administrators only; the reason is kept in the history.
     */
    @Transactional
    public DocumentDetail reverse(UUID paymentId, PaymentDecision r) {
        if (!StringUtils.hasText(r.reason())) throw new IllegalArgumentException("Say why the payment is reversed, for example that it bounced");
        BillingPayment p = payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        BillingDocument doc = documents.lockById(p.getDocumentId()).orElseThrow();
        if (p.getStatus() == PaymentState.REVERSED) return documentService.detail(doc);
        if (p.getStatus() != PaymentState.CONFIRMED) throw new IllegalArgumentException("Only a confirmed payment can be reversed. Reject a payment that is still waiting.");
        if (p.getVersion() != r.version()) throw new ConflictException("This payment changed. Reload and try again.");
        if (doc.getStatus() != Status.ISSUED) throw new IllegalArgumentException("The invoice is " + doc.getStatus().name().toLowerCase(Locale.ROOT));
        p.reverse(r.reason().trim(), BillingSupport.actor());
        doc.setPaid(doc.getPaidCents() - p.getAmountCents());
        doc.touch(BillingSupport.actor());
        payments.flush(); documents.flush();
        support.event(doc.getId(), p.getId(), "PAYMENT_REVERSED", null, MoneyFormat.withCurrency(p.getAmountCents(), doc.getCurrency()) + " reversed (receipt "
            + p.getReceiptNumber() + "): " + r.reason().trim() + "; balance " + MoneyFormat.withCurrency(doc.getTotalCents() - doc.getPaidCents(), doc.getCurrency()));
        allocate(doc, p, true);
        return documentService.detail(doc);
    }

    /** Invoice payments on a booking also update the booking's ledger (rand bookings only), in the same transaction. */
    private void allocate(BillingDocument doc, BillingPayment p, boolean reversal) {
        if (doc.getBookingId() == null) return;
        if (!"ZAR".equals(doc.getCurrency())) {
            support.event(doc.getId(), p.getId(), "BOOKING_NOT_UPDATED", null, "The booking is kept in rand; update its payments by hand for this "
                + doc.getCurrency() + " invoice");
            return;
        }
        long moved = bookingLedger.allocateFromInvoice(doc.getBookingId(), p.getId(), reversal, p.getAmountCents(), p.getMethod().name(),
            "REC-" + p.getReceiptNumber(), p.getPaidOn());
        if (moved != p.getAmountCents())
            support.event(doc.getId(), p.getId(), "BOOKING_PARTLY_UPDATED", null, MoneyFormat.withCurrency(moved, doc.getCurrency()) + " of "
                + MoneyFormat.withCurrency(p.getAmountCents(), doc.getCurrency()) + (reversal ? " taken back from" : " applied to") + " booking "
                + doc.getBookingReference() + (reversal ? "" : " (the rest is above the booking total)"));
    }

    @Transactional
    public DocumentDetail reject(UUID paymentId, PaymentDecision r) {
        if (!StringUtils.hasText(r.reason())) throw new IllegalArgumentException("Say why the payment is rejected, for example that the funds did not arrive");
        BillingPayment p = payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        BillingDocument doc = documents.lockById(p.getDocumentId()).orElseThrow();
        if (p.getStatus() == PaymentState.REJECTED) return documentService.detail(doc);
        if (p.getStatus() != PaymentState.PENDING) throw new IllegalArgumentException("A confirmed payment cannot be rejected. Issue a revision to correct the invoice.");
        if (p.getVersion() != r.version()) throw new ConflictException("This payment changed. Reload and try again.");
        p.reject(r.reason().trim(), BillingSupport.actor());
        payments.flush();
        support.event(doc.getId(), p.getId(), "PAYMENT_REJECTED", null, r.reason().trim());
        return documentService.detail(doc);
    }

    @Transactional(readOnly = true)
    public BillingDocumentService.RenderedFile receipt(UUID paymentId, boolean word) {
        BillingPayment p = payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (p.getStatus() != PaymentState.CONFIRMED && p.getStatus() != PaymentState.REVERSED)
            throw new IllegalArgumentException("A receipt is available once the payment is confirmed");
        String base = "REC-" + p.getReceiptNumber();
        if (!word && p.getReceiptPdfMediaId() != null)
            return new BillingDocumentService.RenderedFile(media.readAll(media.find(p.getReceiptPdfMediaId())), base + ".pdf", "application/pdf");
        DocumentView view = DocumentViews.receipt(support.read(p.getReceiptSnapshot(), ReceiptSnapshot.class), null);
        return word
            ? new BillingDocumentService.RenderedFile(docx.render(view), base + ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            : new BillingDocumentService.RenderedFile(pdf.render(view), base + ".pdf", "application/pdf");
    }

    private long sum(UUID documentId, PaymentState state) {
        return payments.findByDocumentIdOrderByRecordedAtAsc(documentId).stream().filter(x -> x.getStatus() == state).mapToLong(BillingPayment::getAmountCents).sum();
    }
    private static String trim(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
