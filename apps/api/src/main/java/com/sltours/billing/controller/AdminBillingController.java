package com.sltours.billing.controller;
import com.sltours.billing.dto.BillingDtos.*;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.service.*;
import com.sltours.shared.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Quotations, invoices, payments and sharing for staff. Staff and administrators can view, draft, preview, download,
 * share and record payments; issuing, voiding, revising, converting, confirming payments and changing settings are
 * administrator actions, enforced here.
 */
@RestController @Validated
@RequestMapping("/api/v1/admin/billing")
public class AdminBillingController {
    private final BillingDocumentService documents;
    private final BillingPaymentService payments;
    private final BillingShareService sharing;
    private final BillingSettingsService settings;
    private final QuotationBookingService quotationBookings;
    public AdminBillingController(BillingDocumentService documents, BillingPaymentService payments, BillingShareService sharing, BillingSettingsService settings,
            QuotationBookingService quotationBookings) {
        this.quotationBookings = quotationBookings;
        this.documents = documents; this.payments = payments; this.sharing = sharing; this.settings = settings;
    }

    @GetMapping("/documents")
    PageResponse<DocumentSummary> list(@RequestParam(required = false) Kind kind, @RequestParam(required = false) Status status,
            @RequestParam(required = false) PaymentStatus paymentStatus, @RequestParam(required = false) @Size(max = 120) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return documents.list(kind, status, paymentStatus, q, from, to, overdue, page, size);
    }

    @PostMapping("/documents") @ResponseStatus(HttpStatus.CREATED)
    DocumentDetail create(@Valid @RequestBody SaveDocument body) { return documents.create(body); }
    @GetMapping("/documents/{id}") DocumentDetail get(@PathVariable UUID id) { return documents.get(id); }
    @PutMapping("/documents/{id}") DocumentDetail update(@PathVariable UUID id, @Valid @RequestBody SaveDocument body) { return documents.update(id, body); }
    @PostMapping("/preview") PreviewResult preview(@Valid @RequestBody Preview body) { return documents.preview(body); }
    @PostMapping("/documents/{id}/duplicate") @ResponseStatus(HttpStatus.CREATED) DocumentDetail duplicate(@PathVariable UUID id) { return documents.duplicate(id); }

    @PostMapping("/documents/{id}/issue") @PreAuthorize("hasRole('ADMIN')")
    DocumentDetail issue(@PathVariable UUID id, @Valid @RequestBody Versioned body) {
        try { return documents.issue(id, body.version()); }
        catch (org.springframework.dao.DataIntegrityViolationException raced) { return documents.get(id); }
    }
    @PostMapping("/documents/{id}/void") @PreAuthorize("hasRole('ADMIN')")
    DocumentDetail voidDocument(@PathVariable UUID id, @Valid @RequestBody VoidRequest body) { return documents.voidDocument(id, body); }
    @PostMapping("/documents/{id}/revise") @PreAuthorize("hasRole('ADMIN')")
    DocumentDetail revise(@PathVariable UUID id) { return documents.revise(id); }
    @PostMapping("/documents/{id}/decision") @PreAuthorize("hasRole('ADMIN')")
    DocumentDetail decide(@PathVariable UUID id, @Valid @RequestBody Decision body) { return documents.decide(id, body); }
    @PostMapping("/documents/{id}/convert") @PreAuthorize("hasRole('ADMIN')")
    ConversionResult convert(@PathVariable UUID id) {
        try { return documents.convert(id); }
        catch (org.springframework.dao.DataIntegrityViolationException raced) { return documents.convert(id); }
    }

    @GetMapping("/documents/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean download) {
        return file(documents.pdf(id), download);
    }
    @GetMapping("/documents/{id}/docx")
    ResponseEntity<byte[]> docx(@PathVariable UUID id) { return file(documents.docx(id), true); }

    @PostMapping("/documents/{id}/payments")
    DocumentDetail recordPayment(@PathVariable UUID id, @Valid @RequestBody NewPayment body) {
        try { return payments.record(id, body); }
        catch (org.springframework.dao.DataIntegrityViolationException raced) { return documents.get(id); }
    }
    @PostMapping(path = "/payments/{paymentId}/proofs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    DocumentDetail attachProof(@PathVariable UUID paymentId, @RequestPart("file") MultipartFile file, @RequestParam(required = false) @Size(max = 160) String label) {
        return payments.attachProof(paymentId, file, label);
    }
    @PostMapping("/payments/{paymentId}/confirm") @PreAuthorize("hasRole('ADMIN')")
    DocumentDetail confirm(@PathVariable UUID paymentId, @Valid @RequestBody PaymentDecision body) { return payments.confirm(paymentId, body); }
    @PostMapping("/payments/{paymentId}/reverse") @PreAuthorize("hasRole('ADMIN')")
    DocumentDetail reverse(@PathVariable UUID paymentId, @Valid @RequestBody PaymentDecision body) { return payments.reverse(paymentId, body); }
    @PostMapping("/payments/{paymentId}/reject") @PreAuthorize("hasRole('ADMIN')")
    DocumentDetail reject(@PathVariable UUID paymentId, @Valid @RequestBody PaymentDecision body) { return payments.reject(paymentId, body); }
    @GetMapping("/payments/{paymentId}/receipt.pdf")
    ResponseEntity<byte[]> receiptPdf(@PathVariable UUID paymentId, @RequestParam(defaultValue = "false") boolean download) {
        return file(payments.receipt(paymentId, false), download);
    }
    @GetMapping("/payments/{paymentId}/receipt.docx")
    ResponseEntity<byte[]> receiptDocx(@PathVariable UUID paymentId) { return file(payments.receipt(paymentId, true), true); }

    @PostMapping("/documents/{id}/share") ShareResult share(@PathVariable UUID id, @Valid @RequestBody ShareRequest body) { return sharing.share(id, body); }
    @PostMapping("/documents/{id}/share-events") @ResponseStatus(HttpStatus.NO_CONTENT)
    void shareEvent(@PathVariable UUID id, @Valid @RequestBody ShareEvent body) { sharing.recordAction(id, body); }
    @DeleteMapping("/documents/{id}/links/{linkId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID id, @PathVariable UUID linkId) { sharing.revoke(id, linkId); }
    @GetMapping("/capabilities") Capabilities capabilities() { return sharing.capabilities(); }

    @GetMapping("/customers") List<CustomerSuggestion> customers(@RequestParam @Size(max = 120) String q) { return documents.customers(q); }
    @GetMapping("/bookings/{reference}/prefill") BookingPrefill prefill(@PathVariable String reference) { return documents.prefill(reference); }
    @GetMapping("/enquiries/{reference}/prefill") EnquiryPrefill prefillFromEnquiry(@PathVariable String reference) { return documents.prefillFromEnquiry(reference); }
    /** Creates the booking for an accepted quotation, once; retries return the same booking. */
    @PostMapping("/documents/{id}/booking") @PreAuthorize("hasRole('ADMIN')")
    BookingFromQuotation booking(@PathVariable UUID id, @RequestBody(required = false) QuotationBookingService.Request body) {
        var r = quotationBookings.convert(id, body);
        return new BookingFromQuotation(r.bookingId(), r.bookingReference(), r.created());
    }

    @GetMapping("/settings") SettingsView settings() { return settings.view(); }
    @PutMapping("/settings") @PreAuthorize("hasRole('ADMIN')")
    SettingsView updateSettings(@Valid @RequestBody SettingsUpdate body) { return settings.update(body); }
    @PostMapping(path = "/settings/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('ADMIN')")
    SettingsView uploadLogo(@RequestPart("file") MultipartFile file) { return settings.uploadLogo(file); }
    @DeleteMapping("/settings/logo") @PreAuthorize("hasRole('ADMIN')")
    SettingsView originalLogo() { return settings.useOriginalLogo(); }
    @PutMapping("/settings/numbering/{series}") @PreAuthorize("hasRole('ADMIN')")
    SettingsView numbering(@PathVariable NumberAllocator.Series series, @Valid @RequestBody SequenceUpdate body) { return settings.updateSequence(series, body); }

    static ResponseEntity<byte[]> file(BillingDocumentService.RenderedFile f, boolean download) {
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(f.filename()).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(f.bytes());
    }
}
