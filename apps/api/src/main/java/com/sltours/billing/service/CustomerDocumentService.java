package com.sltours.billing.service;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.billing.dto.BillingDtos.CustomerDocument;
import com.sltours.billing.model.*;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.render.DocumentViews;
import com.sltours.media.*;
import com.sltours.shared.error.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A signed-in customer's own issued quotations and invoices: those linked to their account, directly or through one of
 * their bookings. Drafts and internal notes are never included. Customers can upload proof of payment against their own
 * unpaid invoices; it is evidence for staff only and never changes the balance (staff record and confirm the payment).
 */
@Service
public class CustomerDocumentService {
    private final AccountRepository accounts;
    private final BillingDocumentRepository documents;
    private final BillingDocumentService documentService;
    private final JdbcTemplate jdbc;
    private final MediaService media;
    private final BillingSupport support;
    static final int MAX_PROOFS_PER_DOCUMENT = 10;
    static final String CUSTOMER_PROOF_LABEL = "Uploaded by the customer";
    public CustomerDocumentService(AccountRepository accounts, BillingDocumentRepository documents, BillingDocumentService documentService, JdbcTemplate jdbc,
            MediaService media, BillingSupport support) {
        this.accounts = accounts; this.documents = documents; this.documentService = documentService; this.jdbc = jdbc;
        this.media = media; this.support = support;
    }
    private static final String OWNED = """
        select count(*) from billing_documents d left join bookings b on b.id = d.booking_id
        where d.id = ? and d.status in ('ISSUED', 'ACCEPTED', 'DECLINED') and (d.account_id = ? or b.account_id = ?)""";
    private record Proofs(int count, OffsetDateTime last) {}

    @Transactional(readOnly = true)
    public List<CustomerDocument> list(String email) {
        UUID account = account(email);
        List<UUID> ids = jdbc.query("""
            select d.id from billing_documents d left join bookings b on b.id = d.booking_id
            where d.status in ('ISSUED', 'ACCEPTED', 'DECLINED') and (d.account_id = ? or b.account_id = ?)
            order by d.issue_date desc, d.number desc""", (rs, i) -> (UUID) rs.getObject(1), account, account);
        return documents.findAllById(ids).stream().sorted(Comparator.comparing(BillingDocument::getIssueDate).reversed()).map(d -> new CustomerDocument(
            d.getId(), d.getKind(), d.getStatus(), String.valueOf(d.getNumber()), d.getIssueDate(), d.getDueDate(), d.getCurrency(), d.getTotalCents(),
            d.getPaidCents(), d.getKind() == Kind.INVOICE ? d.getTotalCents() - d.getPaidCents() : 0, d.getPaymentStatus(), d.getBookingReference(),
            DocumentViews.filenameBase(d.getKind(), d.getNumber(), d.getId()) + ".pdf", acceptsProof(d), proofs(d.getId()).count(), proofs(d.getId()).last())).toList();
    }

    @Transactional
    public CustomerDocument uploadProof(String email, UUID id, org.springframework.web.multipart.MultipartFile file) {
        UUID account = account(email);
        Integer owned = jdbc.queryForObject(OWNED, Integer.class, id, account, account);
        if (owned == null || owned == 0) throw new ResourceNotFoundException("Document not found");
        BillingDocument d = documents.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (!acceptsProof(d)) throw new IllegalArgumentException("Proof of payment can only be sent for an issued invoice with a balance to pay");
        if (proofs(id).count() >= MAX_PROOFS_PER_DOCUMENT)
            throw new IllegalArgumentException("You have already sent " + MAX_PROOFS_PER_DOCUMENT + " files for this invoice. Contact SL Tours if you need to send more.");
        MediaFile stored = media.store(file, MediaPurpose.PAYMENT_PROOF, MediaFile.Visibility.PRIVATE, MediaService.DOCUMENTS);
        jdbc.update("insert into billing_attachments (id, document_id, payment_id, media_id, label, uploaded_by, uploaded_at) values (?, ?, null, ?, ?, ?, ?)",
            UUID.randomUUID(), id, stored.getId(), CUSTOMER_PROOF_LABEL, email, OffsetDateTime.now());
        support.event(id, null, "CUSTOMER_PROOF_UPLOADED", null, "The customer uploaded proof of payment (" + stored.getContentType()
            + "). Check it, then record and confirm the payment.", email);
        return list(email).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static boolean acceptsProof(BillingDocument d) {
        return d.getKind() == Kind.INVOICE && d.getStatus() == Status.ISSUED && d.getTotalCents() > d.getPaidCents();
    }

    private Proofs proofs(UUID documentId) {
        return jdbc.queryForObject("select count(*), max(uploaded_at) from billing_attachments where document_id = ? and payment_id is null and label = ?",
            (rs, i) -> new Proofs(rs.getInt(1), rs.getObject(2, OffsetDateTime.class)), documentId, CUSTOMER_PROOF_LABEL);
    }

    @Transactional(readOnly = true)
    public BillingDocumentService.RenderedFile pdf(String email, UUID id) {
        UUID account = account(email);
        Integer owned = jdbc.queryForObject(OWNED, Integer.class, id, account, account);
        // Another customer's document looks exactly like a missing one.
        if (owned == null || owned == 0) throw new ResourceNotFoundException("Document not found");
        return documentService.pdf(id);
    }

    private UUID account(String email) {
        return accounts.findByEmail(email).filter(a -> a.isEnabled() && a.getRole() == Account.Role.CUSTOMER)
            .orElseThrow(() -> new AccessDeniedException("Account unavailable")).getId();
    }
}
