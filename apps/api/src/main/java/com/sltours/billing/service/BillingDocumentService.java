package com.sltours.billing.service;
import com.sltours.billing.dto.BillingDtos.*;
import com.sltours.billing.model.*;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.render.*;
import com.sltours.bookings.model.Booking;
import com.sltours.bookings.model.BookingType;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.media.*;
import com.sltours.privatetrips.repository.PrivateTripDetailsRepository;
import com.sltours.shared.dto.PageResponse;
import com.sltours.shared.error.*;
import com.sltours.tours.model.Tour;
import com.sltours.tours.repository.TourRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Quotations and invoices from draft to issue, revision, conversion and voiding.
 *
 * <p>Drafts are edited freely and identified by their id. Issuing allocates the official number, freezes everything
 * printed into a snapshot and stores the generated PDF; from then on the document only changes status. Corrections
 * are made by issuing a revision, which voids the original and carries its payments across.</p>
 */
@Service
public class BillingDocumentService {
    static final Set<Status> FINAL = EnumSet.of(Status.ISSUED, Status.ACCEPTED, Status.DECLINED);
    private final BillingDocumentRepository documents;
    private final BillingPaymentRepository payments;
    private final BillingSettingsService settings;
    private final NumberAllocator numbers;
    private final BookingRepository bookings;
    private final PrivateTripDetailsRepository privateTrips;
    private final TourRepository tours;
    private final com.sltours.enquiries.repository.EnquiryRepository enquiries;
    private final MediaService media;
    private final BillingSupport support;
    private final JdbcTemplate jdbc;
    private final PdfRenderer pdf;
    private final DocxRenderer docx;

    public BillingDocumentService(BillingDocumentRepository documents, BillingPaymentRepository payments, BillingSettingsService settings,
            NumberAllocator numbers, BookingRepository bookings, PrivateTripDetailsRepository privateTrips, TourRepository tours, MediaService media,
            BillingSupport support, JdbcTemplate jdbc, com.sltours.enquiries.repository.EnquiryRepository enquiries) {
        this.enquiries = enquiries;
        this.documents = documents; this.payments = payments; this.settings = settings; this.numbers = numbers; this.bookings = bookings;
        this.privateTrips = privateTrips; this.tours = tours; this.media = media; this.support = support; this.jdbc = jdbc;
        this.pdf = new PdfRenderer(settings); this.docx = new DocxRenderer(settings);
    }

    // ---- Drafts --------------------------------------------------------------------------------------------------

    @Transactional
    public DocumentDetail create(SaveDocument r) {
        if (r.kind() == null) throw new IllegalArgumentException("Choose quotation or invoice");
        if (r.idempotencyKey() == null) throw new IllegalArgumentException("A request key is required");
        Optional<BillingDocument> existing = documents.findByIdempotencyKey(r.idempotencyKey());
        if (existing.isPresent()) return detail(existing.get());   // the same create submitted twice
        BillingDocument doc = new BillingDocument(r.kind(), BillingSupport.actor());
        doc.setIdempotencyKey(r.idempotencyKey());
        DocumentContent content = r.content();
        if (StringUtils.hasText(r.bookingReference())) content = link(doc, r.bookingReference().trim(), content);
        if (StringUtils.hasText(r.enquiryReference()))
            doc.setEnquiryId(enquiries.findByReference(r.enquiryReference().trim())
                .orElseThrow(() -> new IllegalArgumentException("Enquiry " + r.enquiryReference().trim() + " was not found")).getId());
        save(doc, content, r.internalNotes());
        documents.saveAndFlush(doc);
        support.event(doc.getId(), null, "CREATED", null, label(doc) + (doc.getBookingReference() == null ? "" : " for booking " + doc.getBookingReference())
            + (r.enquiryReference() == null || r.enquiryReference().isBlank() ? "" : " answering enquiry " + r.enquiryReference().trim()));
        return detail(doc);
    }

    /**
     * Starts a quotation from an enquiry: the customer, date, passengers and route come from the enquiry, and a tour
     * enquiry gets one line at today's catalogue price per person. Nothing is stored until the draft is saved.
     */
    @Transactional(readOnly = true)
    public EnquiryPrefill prefillFromEnquiry(String reference) {
        var e = enquiries.findByReference(reference).orElseThrow(() -> new ResourceNotFoundException("Enquiry not found"));
        CompanySettings s = settings.current();
        List<String> notes = new ArrayList<>();
        List<Leg> legs = new ArrayList<>();
        if (StringUtils.hasText(e.getPickupLocation()) || StringUtils.hasText(e.getDestination())) {
            List<String> stops = e.getDestination() == null ? List.of()
                : Arrays.stream(e.getDestination().split("\\R")).map(String::trim).filter(x -> !x.isEmpty()).toList();
            legs.add(new Leg(e.getPickupLocation(), stops));
        }
        Line line;
        if (e.getTour() != null) {
            int people = e.getPassengers() == null ? 1 : e.getPassengers();
            line = new Line(e.getTour().getTitle(), legs, null, BigDecimal.valueOf(people), "person",
                (long) e.getTour().getBasePriceCents(), LineKind.SERVICE, e.getTour().getSlug(), (long) e.getTour().getBasePriceCents(), null);
            notes.add("The tour line uses the catalogue price when the draft is saved. Check the number of people.");
        } else {
            line = new Line(null, legs, null, BigDecimal.ONE, null, 0L, LineKind.SERVICE, null, null, null);
            notes.add("Enter the services and prices for this enquiry.");
        }
        DocumentContent content = new DocumentContent(new Customer(e.getCustomerName(), null, e.getCustomerEmail(), whatsapp(e.getCustomerPhone())),
            null, null, e.getRequestedDate(), null, null, s.getCurrency(), e.getPassengers(), null, null, List.of(line), null, false, null, null,
            Template.STANDARD);
        return new EnquiryPrefill(e.getReference(), content, notes);
    }

    @Transactional
    public DocumentDetail update(UUID id, SaveDocument r) {
        BillingDocument doc = lockDraft(id, r.version());
        DocumentContent content = r.content();
        if (StringUtils.hasText(r.bookingReference()) && !r.bookingReference().trim().equals(doc.getBookingReference()))
            content = link(doc, r.bookingReference().trim(), content);
        else if (!StringUtils.hasText(r.bookingReference())) { doc.setBookingId(null); doc.setAccountId(null); }
        save(doc, content, r.internalNotes());
        documents.flush();
        support.event(doc.getId(), null, "UPDATED", null, "Draft saved");
        return detail(doc);
    }

    /** Totals and printed layout for unsaved edits, using today's settings. Nothing is stored. */
    @Transactional(readOnly = true)
    public PreviewResult preview(Preview r) {
        CompanySettings s = settings.current();
        DocumentContent content = normalise(r.content(), s);
        Totals totals = BillingCalculator.compute(content, settings.tax(s));
        List<String> terms = content.terms() == null ? settings.terms(s, r.kind()) : content.terms();
        Long number = null;
        UUID id = r.id() == null ? UUID.randomUUID() : r.id();
        if (r.id() != null) number = documents.findById(r.id()).map(BillingDocument::getNumber).orElse(null);
        DocumentView view = DocumentViews.document(r.kind(), number, id, number == null ? "DRAFT" : null, content, totals,
            settings.company(s), settings.banking(s), terms);
        return new PreviewResult(totals, view, warnings(content, s));
    }

    @Transactional
    public DocumentDetail duplicate(UUID id) {
        BillingDocument original = find(id);
        DocumentContent source = contentOf(original);
        BillingDocument copy = new BillingDocument(original.getKind(), BillingSupport.actor());
        copy.setBookingId(original.getBookingId()); copy.setAccountId(original.getAccountId());
        save(copy, withDates(source, null, null), original.getInternalNotes());
        documents.saveAndFlush(copy);
        support.event(copy.getId(), null, "CREATED", null, "Copied from " + label(original));
        support.event(original.getId(), null, "DUPLICATED", null, "Copied to a new draft");
        return detail(copy);
    }

    /** Starts a correction of an issued document. Issuing the revision voids the original and moves its payments. */
    @Transactional
    public DocumentDetail revise(UUID id) {
        BillingDocument original = documents.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (original.getStatus() != Status.ISSUED && original.getStatus() != Status.ACCEPTED)
            throw new IllegalArgumentException("Only an issued document can be revised");
        Optional<BillingDocument> open = documents.findFirstByRevisionOfIdAndStatusNot(id, Status.VOID);
        if (open.isPresent()) return detail(open.get());
        if (original.getKind() == Kind.QUOTATION && documents.findFirstBySourceQuotationIdAndStatusNot(id, Status.VOID).isPresent())
            throw new IllegalArgumentException("This quotation has already been converted to an invoice. Revise the invoice instead.");
        BillingDocument revision = new BillingDocument(original.getKind(), BillingSupport.actor());
        revision.setRevisionOfId(original.getId());
        revision.setBookingId(original.getBookingId()); revision.setAccountId(original.getAccountId());
        revision.setSourceQuotationId(null);
        save(revision, withDates(contentOf(original), null, null), original.getInternalNotes());
        documents.saveAndFlush(revision);
        support.event(revision.getId(), null, "CREATED", null, "Revision of " + label(original));
        support.event(original.getId(), null, "REVISION_STARTED", null, "A revision draft was created");
        return detail(revision);
    }

    // ---- Issue and status ----------------------------------------------------------------------------------------

    @Transactional
    public DocumentDetail issue(UUID id, long version) {
        BillingDocument doc = documents.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (FINAL.contains(doc.getStatus())) return detail(doc);   // a retried issue returns the same number
        if (doc.getStatus() != Status.DRAFT) throw new IllegalArgumentException("This document is " + doc.getStatus().name().toLowerCase(Locale.ROOT));
        if (doc.getVersion() != version) throw new ConflictException("This draft changed since you opened it. Reload, check it and issue again.");
        CompanySettings s = settings.current();
        DocumentContent content = normalise(contentOf(doc), s);
        Totals totals = BillingCalculator.compute(content, settings.tax(s));
        if (doc.getKind() == Kind.INVOICE && totals.total() <= 0) throw new IllegalArgumentException("An invoice needs a total above zero");
        LocalDate today = LocalDate.now(BillingSupport.ZONE);
        LocalDate issueDate = content.issueDate() == null ? today : content.issueDate();
        if (issueDate.isAfter(today)) throw new IllegalArgumentException("The issue date cannot be in the future");
        LocalDate due = content.dueDate();
        if (due == null && doc.getKind() == Kind.INVOICE && s.getDefaultDueDays() != null) due = issueDate.plusDays(s.getDefaultDueDays());
        if (due == null && doc.getKind() == Kind.QUOTATION && s.getQuotationValidDays() != null) due = issueDate.plusDays(s.getQuotationValidDays());
        if (due != null && due.isBefore(issueDate)) throw new IllegalArgumentException("The due date cannot be before the issue date");

        BillingDocument original = null;
        long carried = 0;
        if (doc.getRevisionOfId() != null) {
            original = documents.lockById(doc.getRevisionOfId()).orElseThrow();
            if (!FINAL.contains(original.getStatus())) throw new IllegalArgumentException("The original document is no longer issued");
            carried = confirmedTotal(original.getId());
            if (carried > totals.total()) throw new IllegalArgumentException("The revised total is less than the "
                + MoneyFormat.withCurrency(carried, doc.getCurrency()) + " already paid on the original");
            if (payments.findByDocumentIdOrderByRecordedAtAsc(original.getId()).stream().anyMatch(p -> p.getStatus() == PaymentState.PENDING))
                throw new IllegalArgumentException("Confirm or reject the payments waiting on the original before issuing the revision");
        }

        long number = numbers.next(doc.getKind() == Kind.INVOICE ? NumberAllocator.Series.INVOICE : NumberAllocator.Series.QUOTATION);
        List<String> terms = content.terms() == null ? settings.terms(s, doc.getKind()) : content.terms();
        DocumentContent frozen = withDates(content, issueDate, due);
        IssuedSnapshot snapshot = new IssuedSnapshot(doc.getKind(), number, issueDate, due, frozen, totals, settings.company(s), settings.banking(s),
            s.getPaymentInstructions(), terms, BillingSupport.actor(), OffsetDateTime.now());
        byte[] file = pdf.render(view(snapshot, doc.getId(), null));
        String filename = DocumentViews.filenameBase(doc.getKind(), number, doc.getId()) + ".pdf";
        MediaFile stored = media.storeGenerated(file, filename, MediaPurpose.BILLING_DOCUMENT, MediaService.PDF_ONLY);
        doc.applyContent(support.write(frozen), frozen, totals, BillingSupport.actor());
        doc.issue(number, support.write(snapshot), stored.getId(), issueDate, due, BillingSupport.actor());
        documents.flush();
        support.event(doc.getId(), null, "ISSUED", null, label(doc) + " issued for " + MoneyFormat.withCurrency(totals.total(), doc.getCurrency()));

        if (original != null) {
            jdbc.update("update billing_payments set document_id = ?, version = version + 1 where document_id = ?", doc.getId(), original.getId());
            jdbc.update("update billing_attachments set document_id = ? where document_id = ? and payment_id is not null", doc.getId(), original.getId());
            doc.setPaid(carried);
            original.setPaid(0);
            original.setSupersededById(doc.getId());
            original.voidDocument("Replaced by " + label(doc), BillingSupport.actor());
            revokeLinks(original.getId());
            documents.flush();
            support.event(original.getId(), null, "VOIDED", null, "Replaced by " + label(doc) + (carried > 0 ? "; payments moved to the revision" : ""));
            support.event(doc.getId(), null, "REVISION_ISSUED", null, "Replaces " + label(original)
                + (carried > 0 ? "; " + MoneyFormat.withCurrency(carried, doc.getCurrency()) + " of payments carried over" : ""));
        }
        return detail(doc);
    }

    @Transactional
    public DocumentDetail voidDocument(UUID id, VoidRequest r) {
        BillingDocument doc = documents.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (doc.getStatus() == Status.VOID) return detail(doc);
        if (doc.getVersion() != r.version()) throw new ConflictException("This document changed since you opened it. Reload and try again.");
        List<BillingPayment> list = payments.findByDocumentIdOrderByRecordedAtAsc(id);
        if (list.stream().anyMatch(p -> p.getStatus() == PaymentState.CONFIRMED))
            throw new IllegalArgumentException("This invoice has confirmed payments. Issue a revision to correct it instead of voiding it.");
        if (list.stream().anyMatch(p -> p.getStatus() == PaymentState.PENDING))
            throw new IllegalArgumentException("Reject the payments waiting for confirmation before voiding this invoice");
        if (doc.getKind() == Kind.QUOTATION && documents.findFirstBySourceQuotationIdAndStatusNot(id, Status.VOID).isPresent())
            throw new IllegalArgumentException("This quotation was converted to an invoice. Void the invoice first.");
        boolean draft = doc.getStatus() == Status.DRAFT;
        doc.voidDocument(r.reason().trim(), BillingSupport.actor());
        revokeLinks(id);
        documents.flush();
        support.event(id, null, draft ? "DISCARDED" : "VOIDED", null, r.reason().trim());
        return detail(doc);
    }

    @Transactional
    public DocumentDetail decide(UUID id, Decision r) {
        BillingDocument doc = documents.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (doc.getKind() != Kind.QUOTATION) throw new IllegalArgumentException("Only quotations are accepted or declined");
        if (r.decision() != Status.ACCEPTED && r.decision() != Status.DECLINED) throw new IllegalArgumentException("Choose accepted or declined");
        if (doc.getStatus() == r.decision()) return detail(doc);
        if (doc.getStatus() != Status.ISSUED) throw new IllegalArgumentException("Only an issued quotation can be marked " + r.decision().name().toLowerCase(Locale.ROOT));
        if (doc.getVersion() != r.version()) throw new ConflictException("This quotation changed since you opened it. Reload and try again.");
        doc.decide(r.decision(), BillingSupport.actor());
        documents.flush();
        support.event(id, null, r.decision().name(), null, "Customer " + r.decision().name().toLowerCase(Locale.ROOT) + " the quotation");
        return detail(doc);
    }

    /** Creates the invoice for a quotation, once. Converting again returns the invoice already created. */
    @Transactional
    public ConversionResult convert(UUID quotationId) {
        BillingDocument quote = documents.lockById(quotationId).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (quote.getKind() != Kind.QUOTATION) throw new IllegalArgumentException("Only quotations can be converted");
        Optional<BillingDocument> existing = documents.findFirstBySourceQuotationIdAndStatusNot(quotationId, Status.VOID);
        if (existing.isPresent()) return new ConversionResult(existing.get().getId(), false);
        if (quote.getStatus() != Status.ISSUED && quote.getStatus() != Status.ACCEPTED)
            throw new IllegalArgumentException("Issue the quotation before converting it");
        if (quote.getStatus() == Status.ISSUED) {
            quote.decide(Status.ACCEPTED, BillingSupport.actor());
            support.event(quotationId, null, "ACCEPTED", null, "Accepted when converted to an invoice");
        }
        IssuedSnapshot snapshot = support.read(quote.getSnapshot(), IssuedSnapshot.class);
        DocumentContent c = snapshot.content();
        DocumentContent invoiceContent = new DocumentContent(c.customer(), null, null, c.serviceStart(), c.serviceEnd(), c.bookingReference(),
            c.currency(), c.passengers(), c.vehicle(), c.itineraryNotes(), c.lines(), c.discount(), c.applyTax(), c.customerNotes(), null, c.template());
        BillingDocument invoice = new BillingDocument(Kind.INVOICE, BillingSupport.actor());
        invoice.setSourceQuotationId(quotationId);
        invoice.setBookingId(quote.getBookingId()); invoice.setAccountId(quote.getAccountId());
        save(invoice, invoiceContent, quote.getInternalNotes());
        documents.saveAndFlush(invoice);
        support.event(invoice.getId(), null, "CREATED", null, "Converted from " + label(quote));
        support.event(quotationId, null, "CONVERTED", null, "Invoice draft created");
        return new ConversionResult(invoice.getId(), true);
    }

    // ---- Reading -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<DocumentSummary> list(Kind kind, Status status, PaymentStatus paymentStatus, String q, LocalDate from, LocalDate to,
                                              boolean overdueOnly, int page, int size) {
        LocalDate today = LocalDate.now(BillingSupport.ZONE);
        Specification<BillingDocument> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (kind != null) p.add(cb.equal(root.get("kind"), kind));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            else p.add(cb.notEqual(root.get("status"), Status.VOID));
            if (paymentStatus != null) p.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                List<Predicate> any = new ArrayList<>(List.of(cb.like(cb.lower(root.get("customerName")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("customerEmail"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("bookingReference"), "")), like)));
                String digits = q.replaceAll("\\D", "");
                if (!digits.isEmpty() && digits.length() <= 12) any.add(cb.equal(root.get("number"), Long.parseLong(digits)));
                p.add(cb.or(any.toArray(Predicate[]::new)));
            }
            if (from != null || to != null) {
                var date = cb.<LocalDate>coalesce(root.get("issueDate"), root.get("createdAt").as(LocalDate.class));
                if (from != null) p.add(cb.greaterThanOrEqualTo(date, from));
                if (to != null) p.add(cb.lessThanOrEqualTo(date, to));
            }
            if (overdueOnly) {
                p.add(cb.equal(root.get("status"), Status.ISSUED));
                p.add(cb.equal(root.get("kind"), Kind.INVOICE));
                p.add(cb.notEqual(root.get("paymentStatus"), PaymentStatus.PAID));
                p.add(cb.lessThan(root.get("dueDate"), today));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<BillingDocument> result = documents.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"))));
        return PageResponse.from(result.map(this::summary));
    }

    @Transactional(readOnly = true)
    public DocumentDetail get(UUID id) { return detail(find(id)); }

    /** The prefilled content for an invoice or quotation from a booking, at the price agreed on the booking. */
    @Transactional(readOnly = true)
    public BookingPrefill prefill(String reference) {
        Booking b = bookings.findByReference(reference).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        CompanySettings s = settings.current();
        List<String> notes = new ArrayList<>();
        List<Leg> legs = new ArrayList<>();
        LocalDate end = null;
        String details = null;
        if (b.getBookingType() == BookingType.PRIVATE_TRIP) {
            var trip = privateTrips.findById(b.getId());
            if (trip.isPresent()) {
                List<String> stops = Arrays.stream(trip.get().getDestination().split("\\R")).map(String::trim).filter(x -> !x.isEmpty()).toList();
                legs.add(new Leg(b.getPickupLocation(), stops));
                end = trip.get().getEndsAt().atZoneSameInstant(BillingSupport.ZONE).toLocalDate();
                details = trip.get().getStartsAt().atZoneSameInstant(BillingSupport.ZONE).toLocalTime() + " to "
                    + trip.get().getEndsAt().atZoneSameInstant(BillingSupport.ZONE).toLocalTime();
            }
        } else if (StringUtils.hasText(b.getPickupLocation())) {
            legs.add(new Leg(b.getPickupLocation(), List.of()));
        }
        if (b.getTotalAmountCents() <= 0) notes.add("This booking has no agreed price yet. Enter the price for each service.");
        // One line at the booking's agreed total; it is not tied to the catalogue, so later price changes cannot affect it.
        Line line = new Line(b.getTitle(), legs, details, BigDecimal.ONE, null, (long) b.getTotalAmountCents(), LineKind.SERVICE, null, null, null);
        DocumentContent content = new DocumentContent(new Customer(b.getCustomerName(), null, b.getCustomerEmail(), whatsapp(b.getCustomerPhone())),
            null, null, b.getTravelDate(), end, b.getReference(), s.getCurrency(), b.getGuests(), null, null, List.of(line), null, false, null, null,
            Template.STANDARD);
        notes.add("The price is the booking's agreed total (" + MoneyFormat.withCurrency(b.getTotalAmountCents(), s.getCurrency())
            + "). Later tour price changes do not affect it.");
        return new BookingPrefill(b.getId(), b.getReference(), content, notes);
    }

    @Transactional(readOnly = true)
    public List<CustomerSuggestion> customers(String q) {
        if (!StringUtils.hasText(q) || q.trim().length() < 2) return List.of();
        String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query("""
            select name, contact, email, phone, source, ref from (
              select distinct on (lower(customer_name), lower(coalesce(customer_email, '')))
                customer_name as name, null as contact, customer_email as email, customer_whatsapp as phone, 'Previous document' as source,
                booking_reference as ref, updated_at as at
              from billing_documents where status <> 'VOID' and (lower(customer_name) like ? or lower(coalesce(customer_email, '')) like ?)
              order by lower(customer_name), lower(coalesce(customer_email, '')), updated_at desc) d
            union all
            select name, contact, email, phone, source, ref from (
              select distinct on (lower(customer_email)) customer_name as name, null as contact, customer_email as email, customer_phone as phone,
                'Booking' as source, reference as ref, created_at as at
              from bookings where demo_data = false and (lower(customer_name) like ? or lower(customer_email) like ? or lower(reference) like ?)
              order by lower(customer_email), created_at desc) b
            limit 12""", (rs, i) -> new CustomerSuggestion(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
            rs.getString(6)), like, like, like, like, like);
    }

    public record RenderedFile(byte[] bytes, String filename, String contentType) {}

    @Transactional(readOnly = true)
    public RenderedFile pdf(UUID id) {
        BillingDocument doc = find(id);
        String filename = DocumentViews.filenameBase(doc.getKind(), doc.getNumber(), doc.getId()) + ".pdf";
        if (FINAL.contains(doc.getStatus()) && doc.getIssuedPdfMediaId() != null)
            return new RenderedFile(media.readAll(media.find(doc.getIssuedPdfMediaId())), filename, "application/pdf");
        return new RenderedFile(pdf.render(printable(doc)), filename, "application/pdf");
    }

    @Transactional(readOnly = true)
    public RenderedFile docx(UUID id) {
        BillingDocument doc = find(id);
        String filename = DocumentViews.filenameBase(doc.getKind(), doc.getNumber(), doc.getId()) + ".docx";
        return new RenderedFile(docx.render(printable(doc)), filename, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    /** The printed view: the issued snapshot when there is one (VOID-marked once voided), otherwise the live draft. */
    DocumentView printable(BillingDocument doc) {
        if (doc.getSnapshot() != null)
            return view(support.read(doc.getSnapshot(), IssuedSnapshot.class), doc.getId(), doc.getStatus() == Status.VOID ? "VOID" : null);
        CompanySettings s = settings.current();
        DocumentContent content = contentOf(doc);
        Totals totals;
        try { totals = BillingCalculator.compute(content, settings.tax(s)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Fix the draft before downloading it: " + e.getMessage()); }
        List<String> terms = content.terms() == null ? settings.terms(s, doc.getKind()) : content.terms();
        return DocumentViews.document(doc.getKind(), null, doc.getId(), doc.getStatus() == Status.VOID ? "VOID" : "DRAFT", content, totals,
            settings.company(s), settings.banking(s), terms);
    }

    DocumentView view(IssuedSnapshot s, UUID id, String watermark) {
        return DocumentViews.document(s.kind(), s.number(), id, watermark, s.content(), s.totals(), s.company(), s.banking(), s.terms());
    }

    // ---- Internals -----------------------------------------------------------------------------------------------

    BillingDocument find(UUID id) { return documents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Document not found")); }

    DocumentContent contentOf(BillingDocument doc) { return support.read(doc.getContent(), DocumentContent.class); }

    long confirmedTotal(UUID documentId) {
        return payments.findByDocumentIdOrderByRecordedAtAsc(documentId).stream().filter(p -> p.getStatus() == PaymentState.CONFIRMED)
            .mapToLong(BillingPayment::getAmountCents).sum();
    }

    void revokeLinks(UUID documentId) {
        jdbc.update("update billing_share_links set revoked_at = ?, revoked_by = ? where document_id = ? and revoked_at is null",
            OffsetDateTime.now(), BillingSupport.actor(), documentId);
    }

    static String label(BillingDocument doc) {
        String kind = doc.getKind() == Kind.INVOICE ? "invoice" : "quotation";
        return doc.getNumber() == null ? kind + " draft" : kind + " " + doc.getNumber();
    }

    private BillingDocument lockDraft(UUID id, Long version) {
        BillingDocument doc = documents.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (doc.getStatus() != Status.DRAFT)
            throw new IllegalArgumentException("Issued documents cannot be edited. Use Revise to correct an issued document.");
        if (version == null || doc.getVersion() != version) throw new ConflictException("This draft was changed elsewhere. Reload to see the latest version.");
        return doc;
    }

    private void save(BillingDocument doc, DocumentContent content, String internalNotes) {
        CompanySettings s = settings.current();
        DocumentContent normal = normalise(content, s);
        Totals totals = BillingCalculator.compute(normal, settings.tax(s));
        doc.applyContent(support.write(normal), normal, totals, BillingSupport.actor());
        doc.setInternalNotes(internalNotes == null || internalNotes.isBlank() ? null : internalNotes.trim());
    }

    private DocumentContent link(BillingDocument doc, String reference, DocumentContent content) {
        Booking booking = bookings.findByReference(reference).orElseThrow(() -> new IllegalArgumentException("Booking " + reference + " was not found"));
        doc.setBookingId(booking.getId());
        doc.setAccountId(booking.getAccountId());
        return new DocumentContent(content.customer(), content.issueDate(), content.dueDate(), content.serviceStart(), content.serviceEnd(),
            booking.getReference(), content.currency(), content.passengers(), content.vehicle(), content.itineraryNotes(), content.lines(),
            content.discount(), content.applyTax(), content.customerNotes(), content.terms(), content.template());
    }

    /** Trims text, applies defaults, looks up catalogue prices on the server and checks dates and overrides. */
    DocumentContent normalise(DocumentContent c, CompanySettings s) {
        if (c.applyTax() && !s.isTaxEnabled())
            throw new IllegalArgumentException("Tax is not configured. An administrator can set it up in billing settings.");
        if (c.serviceStart() != null && c.serviceEnd() != null && c.serviceEnd().isBefore(c.serviceStart()))
            throw new IllegalArgumentException("The service end date cannot be before the start date");
        if (c.issueDate() != null && c.dueDate() != null && c.dueDate().isBefore(c.issueDate()))
            throw new IllegalArgumentException("The due date cannot be before the issue date");
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < c.lines().size(); i++) {
            Line l = c.lines().get(i);
            Long catalogue = null;
            String slug = trim(l.tourSlug());
            if (slug != null) {
                Tour tour = tours.findBySlug(slug).orElseThrow(() -> new IllegalArgumentException("Tour " + slug + " was not found"));
                catalogue = (long) tour.getBasePriceCents();
                if (!catalogue.equals(l.unitPriceCents()) && trim(l.priceOverrideReason()) == null)
                    throw new IllegalArgumentException("Service " + (i + 1) + ": the price differs from the catalogue price of "
                        + MoneyFormat.withCurrency(catalogue, "ZAR") + ". Give a reason for the agreed price.");
            }
            List<Leg> legs = l.legs() == null ? List.of() : l.legs().stream()
                .map(g -> new Leg(trim(g.pickup()), g.dropOffs() == null ? List.of() : g.dropOffs().stream().map(BillingDocumentService::trim).filter(Objects::nonNull).toList()))
                .filter(g -> g.pickup() != null || !g.dropOffs().isEmpty()).toList();
            if (trim(l.title()) == null && legs.isEmpty() && trim(l.details()) == null)
                throw new IllegalArgumentException("Service " + (i + 1) + ": describe the service");
            lines.add(new Line(trim(l.title()), legs, l.details() == null ? null : l.details().strip(), l.quantity(), trim(l.unit()), l.unitPriceCents(),
                l.kindOrDefault(), slug, catalogue, catalogue != null && !catalogue.equals(l.unitPriceCents()) ? trim(l.priceOverrideReason()) : null));
        }
        List<String> terms = c.terms() == null ? null : c.terms().stream().map(BillingDocumentService::trim).filter(Objects::nonNull).toList();
        Discount d = c.discount() == null || c.discount().type() == null || c.discount().type() == DiscountType.NONE || c.discount().value() == 0
            ? null : new Discount(c.discount().type(), c.discount().value(), trim(c.discount().label()));
        Customer customer = new Customer(c.customer().name().trim(), trim(c.customer().contactPerson()), trim(c.customer().email()), trim(c.customer().whatsapp()));
        return new DocumentContent(customer, c.issueDate(), c.dueDate(), c.serviceStart(), c.serviceEnd(), trim(c.bookingReference()),
            c.currency() == null ? s.getCurrency() : c.currency(), c.passengers(), trim(c.vehicle()), c.itineraryNotes() == null ? null : c.itineraryNotes().strip(),
            lines, d, c.applyTax(), c.customerNotes() == null ? null : c.customerNotes().strip(), terms, c.templateOrDefault());
    }

    private List<String> warnings(DocumentContent c, CompanySettings s) {
        List<String> w = new ArrayList<>();
        if (s.isAddressNeedsReview()) w.add("The company address is printed as \"" + s.getAddress() + "\". Ask an administrator to confirm its spelling in billing settings.");
        if (c.customer().whatsapp() == null && c.customer().email() == null) w.add("Add the customer's WhatsApp number or email to share the document.");
        for (Line l : c.lines()) if (l.cataloguePriceCents() != null && !l.cataloguePriceCents().equals(l.unitPriceCents()))
            w.add("\"" + Objects.toString(l.title(), "Service") + "\" is priced differently from the catalogue (" + MoneyFormat.withCurrency(l.cataloguePriceCents(), "ZAR") + ").");
        return w;
    }

    static DocumentContent withDates(DocumentContent c, LocalDate issue, LocalDate due) {
        return new DocumentContent(c.customer(), issue, due, c.serviceStart(), c.serviceEnd(), c.bookingReference(), c.currency(), c.passengers(),
            c.vehicle(), c.itineraryNotes(), c.lines(), c.discount(), c.applyTax(), c.customerNotes(), c.terms(), c.template());
    }

    DocumentSummary summary(BillingDocument d) {
        boolean overdue = d.getKind() == Kind.INVOICE && d.getStatus() == Status.ISSUED && d.getPaymentStatus() != PaymentStatus.PAID
            && d.getDueDate() != null && d.getDueDate().isBefore(LocalDate.now(BillingSupport.ZONE));
        return new DocumentSummary(d.getId(), d.getKind(), d.getStatus(), d.getNumber(), displayNumber(d), d.getCustomerName(), d.getCustomerEmail(),
            d.getBookingReference(), d.getIssueDate(), d.getDueDate(), d.getServiceStart(), d.getCurrency(), d.getTotalCents(), d.getPaidCents(),
            d.getKind() == Kind.INVOICE && d.getStatus() != Status.VOID ? d.getTotalCents() - d.getPaidCents() : 0, d.getPaymentStatus(), overdue, d.getUpdatedAt());
    }

    static String displayNumber(BillingDocument d) {
        return d.getNumber() != null ? String.valueOf(d.getNumber()) : "Draft " + d.getId().toString().substring(0, 8);
    }

    private Related related(UUID id) {
        if (id == null) return null;
        return documents.findById(id).map(d -> new Related(d.getId(), d.getKind(), d.getStatus(), displayNumber(d))).orElse(null);
    }

    DocumentDetail detail(BillingDocument doc) {
        DocumentContent content = contentOf(doc);
        Totals totals;
        DocumentView view;
        if (doc.getSnapshot() != null) {
            IssuedSnapshot s = support.read(doc.getSnapshot(), IssuedSnapshot.class);
            totals = s.totals();
            view = view(s, doc.getId(), doc.getStatus() == Status.VOID ? "VOID" : null);
        } else {
            CompanySettings s = settings.current();
            Totals computed;
            try { computed = BillingCalculator.compute(content, settings.tax(s)); }
            catch (IllegalArgumentException e) { computed = new Totals(List.of(), 0, 0, 0, 0, 0, null, null); }
            totals = computed;
            view = computed.lineTotals().size() == content.lines().size() ? printable(doc) : null;
        }
        List<AttachmentView> attachments = attachments(doc.getId());
        List<PaymentView> paymentViews = payments.findByDocumentIdOrderByRecordedAtAsc(doc.getId()).stream()
            .map(p -> new PaymentView(p.getId(), p.getVersion(), p.getAmountCents(), p.getPaidOn(), p.getMethod(), p.getReference(), p.getNotes(),
                p.getStatus(), p.getReceiptNumber(), p.getReceiptNumber() == null ? null : "REC-" + p.getReceiptNumber() + ".pdf", p.getRecordedBy(),
                p.getRecordedAt(), p.getDecidedBy(), p.getDecidedAt(), p.getRejectReason(),
                attachments.stream().filter(a -> p.getId().equals(a.paymentId())).toList(), p.getReversedBy(), p.getReversedAt(), p.getReverseReason())).toList();
        long pending = paymentViews.stream().filter(p -> p.status() == PaymentState.PENDING).mapToLong(PaymentView::amountCents).sum();
        List<ShareLinkView> links = jdbc.query("""
            select id, payment_id, channel, created_by, created_at, expires_at, revoked_at, revoked_by, last_opened_at, open_count
            from billing_share_links where document_id = ? order by created_at desc""", (rs, i) -> {
            OffsetDateTime expires = rs.getObject(6, OffsetDateTime.class), revoked = rs.getObject(7, OffsetDateTime.class);
            return new ShareLinkView((UUID) rs.getObject(1), (UUID) rs.getObject(2), Channel.valueOf(rs.getString(3)), rs.getString(4),
                rs.getObject(5, OffsetDateTime.class), expires, revoked, rs.getString(8), rs.getObject(9, OffsetDateTime.class), rs.getInt(10),
                revoked == null && expires.isAfter(OffsetDateTime.now()));
        }, doc.getId());
        List<EventView> events = jdbc.query("select event, channel, actor, detail, created_at, payment_id from billing_events where document_id = ? order by created_at desc, id",
            (rs, i) -> new EventView(rs.getString(1), rs.getString(2) == null ? null : Channel.valueOf(rs.getString(2)), rs.getString(3), rs.getString(4),
                rs.getObject(5, OffsetDateTime.class), (UUID) rs.getObject(6)), doc.getId());
        Related converted = doc.getKind() == Kind.QUOTATION
            ? documents.findFirstBySourceQuotationIdAndStatusNot(doc.getId(), Status.VOID).map(d -> related(d.getId())).orElse(null) : null;
        Related pendingRevision = documents.findFirstByRevisionOfIdAndStatusNot(doc.getId(), Status.VOID).map(d -> related(d.getId())).orElse(null);
        boolean numberingConfirmed = Boolean.TRUE.equals(jdbc.queryForObject("select confirmed from document_sequences where kind = ?", Boolean.class,
            doc.getKind().name()));
        String base = DocumentViews.filenameBase(doc.getKind(), doc.getNumber(), doc.getId());
        return new DocumentDetail(summary(doc), doc.getVersion(), content, doc.getInternalNotes(), totals, view, paymentViews, pending, attachments,
            links, events, related(doc.getSourceQuotationId()), converted, related(doc.getRevisionOfId()), related(doc.getSupersededById()),
            pendingRevision, doc.getCreatedBy(), doc.getCreatedAt(), doc.getIssuedBy(), doc.getIssuedAt(), doc.getVoidedBy(), doc.getVoidReason(),
            base + ".pdf", base + ".docx", numberingConfirmed,
            doc.getEnquiryId() == null ? null : jdbc.queryForObject("select reference from enquiries where id = ?", String.class, doc.getEnquiryId()),
            doc.getBookingId() == null ? null : jdbc.queryForObject("select reference from bookings where id = ?", String.class, doc.getBookingId()),
            customerId(doc.getId()));
    }

    /** Set by the database (see V25); null for a document without a customer email. */
    private UUID customerId(UUID documentId) {
        List<UUID> ids = jdbc.queryForList("select customer_id from billing_documents where id = ?", UUID.class, documentId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private List<AttachmentView> attachments(UUID documentId) {
        return jdbc.query("""
            select a.id, a.payment_id, a.media_id, m.original_filename, m.content_type, m.size_bytes, a.label, a.uploaded_by, a.uploaded_at
            from billing_attachments a join media_files m on m.id = a.media_id where a.document_id = ? and m.deleted_at is null order by a.uploaded_at""",
            (rs, i) -> new AttachmentView((UUID) rs.getObject(1), (UUID) rs.getObject(2), "/api/v1/admin/media/" + rs.getObject(3), rs.getString(4),
                rs.getString(5), rs.getLong(6), rs.getString(7), rs.getString(8), rs.getObject(9, OffsetDateTime.class)), documentId);
    }

    /** South African numbers are stored for WhatsApp as typed; leading 0 is converted when links are built. */
    private static String whatsapp(String phone) {
        if (phone == null) return null;
        String cleaned = phone.replaceAll("[^0-9+ ()-]", "").trim();
        return cleaned.length() >= 7 ? cleaned : null;
    }

    static String trim(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
