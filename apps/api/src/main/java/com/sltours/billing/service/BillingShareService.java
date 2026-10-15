package com.sltours.billing.service;
import com.sltours.billing.dto.BillingDtos.*;
import com.sltours.billing.model.*;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.render.*;
import com.sltours.media.MediaService;
import com.sltours.shared.error.ResourceNotFoundException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Customer document links and prepared messages.
 *
 * <p>A link carries a random 256-bit token; only its SHA-256 hash is stored, it expires, it can be revoked, and it opens
 * exactly one issued document (or one receipt). Preparing a WhatsApp or email message only builds the text and link:
 * staff send it themselves, and the history records "prepared" or "opened", never "sent" or "delivered". There is no
 * WhatsApp Business API or email provider in this application, so nothing is sent by the server.</p>
 */
@Service
public class BillingShareService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final BillingDocumentRepository documents;
    private final BillingPaymentRepository payments;
    private final MediaService media;
    private final BillingSupport support;
    private final JdbcTemplate jdbc;
    private final String baseUrl;
    private final int defaultDays;
    private final int maxDays;

    public BillingShareService(BillingDocumentRepository documents, BillingPaymentRepository payments, MediaService media,
            BillingSupport support, JdbcTemplate jdbc,
            @Value("${sltours.billing.link-base-url:${sltours.public-base-url:https://sl-tours.com}}") String baseUrl,
            @Value("${sltours.billing.link-days:14}") int defaultDays, @Value("${sltours.billing.link-max-days:90}") int maxDays) {
        this.documents = documents; this.payments = payments; this.media = media; this.support = support; this.jdbc = jdbc;
        this.baseUrl = baseUrl.replaceAll("/+$", ""); this.defaultDays = defaultDays; this.maxDays = maxDays;
    }

    public Capabilities capabilities() {
        return new Capabilities(false, false, true, defaultDays, maxDays,
            "WhatsApp opens with the message ready; you choose the chat and press send. The WhatsApp Business API is not set up, so nothing is sent automatically.",
            "Your email app opens with the subject and message ready. No email service is set up, so attach the PDF yourself if needed.");
    }

    @Transactional
    public ShareResult share(UUID documentId, ShareRequest r) {
        BillingDocument doc = documents.findById(documentId).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (!BillingDocumentService.FINAL.contains(doc.getStatus())) throw new IllegalArgumentException("Issue the document before sharing it");
        BillingPayment payment = null;
        if (r.paymentId() != null) {
            payment = payments.findById(r.paymentId()).filter(p -> p.getDocumentId().equals(documentId))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
            if (payment.getStatus() != PaymentState.CONFIRMED) throw new IllegalArgumentException("A receipt can be shared once the payment is confirmed");
        }
        int days = r.expiresInDays() == null ? defaultDays : Math.min(r.expiresInDays(), maxDays);
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        UUID linkId = UUID.randomUUID();
        OffsetDateTime expires = OffsetDateTime.now().plusDays(days);
        jdbc.update("insert into billing_share_links (id, document_id, payment_id, token_hash, channel, created_by, created_at, expires_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
            linkId, documentId, payment == null ? null : payment.getId(), hash(token), r.channel().name(), BillingSupport.actor(), OffsetDateTime.now(), expires);
        String url = baseUrl + "/api/v1/public/documents/" + token;
        IssuedSnapshot snapshot = support.read(doc.getSnapshot(), IssuedSnapshot.class);
        Customer customer = snapshot.content().customer();
        String filename = payment == null ? DocumentViews.filenameBase(doc.getKind(), doc.getNumber(), doc.getId()) + ".pdf" : "REC-" + payment.getReceiptNumber() + ".pdf";
        String subject = payment == null
            ? "SL Tours " + (doc.getKind() == Kind.INVOICE ? "invoice " : "quotation ") + doc.getNumber()
            : "SL Tours receipt " + payment.getReceiptNumber() + " for invoice " + doc.getNumber();
        String message = message(doc, snapshot, payment, url, expires);
        String phone = whatsappDigits(customer.whatsapp());
        String whatsappUrl = "https://wa.me/" + (phone == null ? "" : phone) + "?text=" + encode(message);
        String mailto = "mailto:" + (customer.email() == null ? "" : customer.email()) + "?subject=" + encode(subject) + "&body=" + encode(message);
        support.event(documentId, payment == null ? null : payment.getId(), "LINK_CREATED", r.channel(),
            "Customer link created, expires " + expires.atZoneSameInstant(BillingSupport.ZONE).toLocalDate().format(DocumentViews.DATE));
        return new ShareResult(linkId, url, expires, message, subject, whatsappUrl, mailto, filename, phone == null, customer.email() == null);
    }

    /** Staff-side actions reported by the browser. They record what staff did, not whether the customer received anything. */
    @Transactional
    public void recordAction(UUID documentId, ShareEvent e) {
        documents.findById(documentId).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        String detail = switch (e.action()) {
            case WHATSAPP_OPENED -> "WhatsApp opened with the prepared message (sending is up to staff)";
            case EMAIL_OPENED -> "Email app opened with the prepared message (sending is up to staff)";
            case NATIVE_SHARED -> "PDF handed to the device's share sheet";
            case PDF_DOWNLOADED -> "PDF downloaded";
            case DOCX_DOWNLOADED -> "Word document downloaded";
            case LINK_COPIED -> "Customer link copied";
        };
        support.event(documentId, e.paymentId(), e.action().name(), e.channel(), detail);
    }

    @Transactional
    public void revoke(UUID documentId, UUID linkId) {
        int updated = jdbc.update("update billing_share_links set revoked_at = ?, revoked_by = ? where id = ? and document_id = ? and revoked_at is null",
            OffsetDateTime.now(), BillingSupport.actor(), linkId, documentId);
        if (updated == 0) throw new ResourceNotFoundException("Active link not found");
        support.event(documentId, null, "LINK_REVOKED", null, "Customer link withdrawn");
    }

    public record PublicFile(byte[] bytes, String filename) {}

    /** Opens the one document a customer link points to. Unknown, expired, withdrawn and voided links reveal nothing else. */
    @Transactional
    public PublicFile open(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This link is not valid");
        var row = jdbc.query("select id, document_id, payment_id, expires_at, revoked_at from billing_share_links where token_hash = ? for update",
            (rs, i) -> new Object[]{rs.getObject(1), rs.getObject(2), rs.getObject(3), rs.getObject(4, OffsetDateTime.class), rs.getObject(5, OffsetDateTime.class)},
            hash(token)).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This link is not valid"));
        if (row[4] != null) throw new ResponseStatusException(HttpStatus.GONE, "This link was withdrawn. Ask SL Tours for a new one.");
        if (((OffsetDateTime) row[3]).isBefore(OffsetDateTime.now())) throw new ResponseStatusException(HttpStatus.GONE, "This link has expired. Ask SL Tours for a new one.");
        BillingDocument doc = documents.findById((UUID) row[1]).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This link is not valid"));
        if (!BillingDocumentService.FINAL.contains(doc.getStatus())) throw new ResponseStatusException(HttpStatus.GONE, "This document is no longer valid. Contact SL Tours.");
        jdbc.update("update billing_share_links set open_count = open_count + 1, last_opened_at = ? where id = ?", OffsetDateTime.now(), row[0]);
        UUID paymentId = (UUID) row[2];
        support.event(doc.getId(), paymentId, "LINK_OPENED", null, "Customer link opened", "customer link");
        if (paymentId != null) {
            BillingPayment p = payments.findById(paymentId).filter(x -> x.getStatus() == PaymentState.CONFIRMED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "This receipt is no longer available"));
            return new PublicFile(media.readAll(media.find(p.getReceiptPdfMediaId())), "REC-" + p.getReceiptNumber() + ".pdf");
        }
        return new PublicFile(media.readAll(media.find(doc.getIssuedPdfMediaId())), DocumentViews.filenameBase(doc.getKind(), doc.getNumber(), doc.getId()) + ".pdf");
    }

    String message(BillingDocument doc, IssuedSnapshot s, BillingPayment payment, String url, OffsetDateTime expires) {
        Customer c = s.content().customer();
        String greeting = "Hello " + (c.contactPerson() != null ? c.contactPerson() : c.name()) + ",";
        String currency = doc.getCurrency();
        String expiry = "The link works until " + expires.atZoneSameInstant(BillingSupport.ZONE).toLocalDate().format(DocumentViews.DATE) + ".";
        String company = s.company().name();
        StringBuilder m = new StringBuilder(greeting).append("\n\n");
        if (payment != null) {
            m.append("Thank you for your payment of ").append(MoneyFormat.withCurrency(payment.getAmountCents(), currency)).append(" towards invoice ")
                .append(doc.getNumber()).append(". Your receipt ").append(payment.getReceiptNumber()).append(" is here:\n").append(url).append("\n\n")
                .append("Outstanding balance: ").append(MoneyFormat.withCurrency(doc.getTotalCents() - doc.getPaidCents(), currency)).append("\n\n");
        } else if (doc.getKind() == Kind.INVOICE) {
            m.append("Please find invoice ").append(doc.getNumber()).append(" from ").append(company).append(" here:\n").append(url).append("\n\n")
                .append("Total: ").append(MoneyFormat.withCurrency(doc.getTotalCents(), currency)).append("\n")
                .append("Outstanding balance: ").append(MoneyFormat.withCurrency(doc.getTotalCents() - doc.getPaidCents(), currency)).append("\n");
            if (doc.getDueDate() != null) m.append("Due date: ").append(doc.getDueDate().format(DocumentViews.DATE)).append("\n");
            m.append("\n").append("Once you have paid, please send us your proof of payment by WhatsApp or email.\n\n");
        } else {
            m.append("Please find quotation ").append(doc.getNumber()).append(" from ").append(company).append(" here:\n").append(url).append("\n\n")
                .append("Total: ").append(MoneyFormat.withCurrency(doc.getTotalCents(), currency)).append("\n");
            if (doc.getDueDate() != null) m.append("Valid until: ").append(doc.getDueDate().format(DocumentViews.DATE)).append("\n");
            m.append("\nLet us know if you would like to go ahead or change anything.\n\n");
        }
        m.append(expiry).append("\n\nThank you,\n").append(company);
        return m.toString();
    }

    /** Digits for wa.me: a South African number typed with a leading 0 becomes 27…; other numbers need their country code. */
    static String whatsappDigits(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        else if (digits.length() == 10 && digits.startsWith("0")) digits = "27" + digits.substring(1);
        return digits.length() >= 8 && digits.length() <= 15 ? digits : null;
    }

    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String encode(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20"); }
}
