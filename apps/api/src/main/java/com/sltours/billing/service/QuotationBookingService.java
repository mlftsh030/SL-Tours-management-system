package com.sltours.billing.service;

import com.sltours.billing.model.*;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.bookings.model.*;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.bookings.service.BookingReferenceGenerator;
import com.sltours.enquiries.repository.EnquiryRepository;
import com.sltours.enquiries.service.EnquiryService;
import com.sltours.enquiries.service.EnquiryStatusPolicy;
import com.sltours.shared.error.ResourceNotFoundException;
import com.sltours.tours.repository.TourRepository;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Turns an accepted quotation into a booking without retyping: the customer, date, passengers, services, prices and stops
 * come from the quotation's frozen snapshot, so later catalogue or customer edits cannot change what was agreed.
 *
 * <p>Each quotation gives at most one booking. The quotation row is locked while converting and {@code bookings.source_quotation_id}
 * is unique, so a retry or a second click returns the booking already created. A quotation prepared for an existing
 * booking is simply accepted and keeps that booking. The enquiry the quotation answered, if any, is marked converted.
 */
@Service
public class QuotationBookingService {
    public record Result(UUID bookingId, String bookingReference, boolean created) {}
    public record Request(LocalDate travelDate, Integer guests) {}

    private final BillingDocumentRepository documents;
    private final BookingRepository bookings;
    private final BookingReferenceGenerator references;
    private final TourRepository tours;
    private final EnquiryRepository enquiries;
    private final EnquiryService enquiryService;
    private final EnquiryStatusPolicy policy;
    private final BillingSupport support;
    private final JdbcTemplate jdbc;

    public QuotationBookingService(BillingDocumentRepository documents, BookingRepository bookings, BookingReferenceGenerator references, TourRepository tours,
            EnquiryRepository enquiries, EnquiryService enquiryService, EnquiryStatusPolicy policy, BillingSupport support, JdbcTemplate jdbc) {
        this.documents = documents; this.bookings = bookings; this.references = references; this.tours = tours; this.enquiries = enquiries;
        this.enquiryService = enquiryService; this.policy = policy; this.support = support; this.jdbc = jdbc;
    }

    @Transactional
    public Result convert(UUID quotationId, Request request) {
        BillingDocument quote = documents.lockById(quotationId).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (quote.getKind() != Kind.QUOTATION) throw new IllegalArgumentException("Only quotations become bookings");
        Optional<Booking> existing = bookings.findBySourceQuotationId(quotationId);
        if (existing.isPresent()) return new Result(existing.get().getId(), existing.get().getReference(), false);
        if (quote.getStatus() != Status.ISSUED && quote.getStatus() != Status.ACCEPTED)
            throw new IllegalArgumentException(quote.getStatus() == Status.DRAFT ? "Issue the quotation before creating the booking"
                : "This quotation is " + quote.getStatus().name().toLowerCase(Locale.ROOT));
        accept(quote);
        if (quote.getBookingId() != null) {
            Booking b = bookings.findById(quote.getBookingId()).orElseThrow();
            closeEnquiry(quote, b);
            return new Result(b.getId(), b.getReference(), false);
        }

        IssuedSnapshot snap = support.read(quote.getSnapshot(), IssuedSnapshot.class);
        DocumentContent c = snap.content();
        if (!"ZAR".equals(quote.getCurrency()))
            throw new IllegalArgumentException("Bookings are kept in rand; this quotation is in " + quote.getCurrency());
        if (!StringUtils.hasText(c.customer().email()))
            throw new IllegalArgumentException("The quotation has no customer email. Revise it with the customer's email before creating the booking.");
        LocalDate date = c.serviceStart() != null ? c.serviceStart() : request == null ? null : request.travelDate();
        if (date == null) throw new IllegalArgumentException("Enter the travel date: the quotation has no service date");
        Integer guests = c.passengers() != null ? c.passengers() : request == null ? null : request.guests();
        if (guests == null || guests < 1 || guests > 60) throw new IllegalArgumentException("Enter the number of passengers (1 to 60): the quotation has none");
        if (snap.totals().total() > Integer.MAX_VALUE) throw new IllegalArgumentException("The quotation total is too large for a booking");

        Booking b = new Booking();
        b.setReference(references.nextReference(bookings));
        b.setBookingType(BookingType.QUOTED);
        b.setSourceQuotationId(quotationId);
        b.setTitle(title(c.lines()));
        tour(c.lines()).ifPresent(b::setTour);
        b.setTravelDate(date);
        b.setGuests(guests);
        b.setCustomerName(c.customer().name().trim());
        b.setCustomerEmail(c.customer().email().trim().toLowerCase(Locale.ROOT));
        b.setCustomerPhone(StringUtils.hasText(c.customer().whatsapp()) ? c.customer().whatsapp().trim() : null);
        b.setAccountId(quote.getAccountId() != null ? quote.getAccountId() : verifiedAccount(quote));
        b.setPickupLocation(c.lines().stream().flatMap(l -> l.legs() == null ? java.util.stream.Stream.empty() : l.legs().stream())
            .map(Leg::pickup).filter(StringUtils::hasText).findFirst().orElse(null));
        b.setNotes("Created from quotation " + quote.getNumber());
        b.setStatus(BookingStatus.AWAITING_PAYMENT);
        b.setTotalAmountCents((int) snap.totals().total());
        b.setAmountPaidCents(0);
        bookings.saveAndFlush(b);

        for (int i = 0; i < c.lines().size(); i++) {
            Line l = c.lines().get(i);
            UUID tourId = StringUtils.hasText(l.tourSlug()) ? tours.findBySlug(l.tourSlug()).map(t -> t.getId()).orElse(null) : null;
            jdbc.update("""
                insert into booking_services (id, booking_id, line_order, title, details, quantity, unit, unit_price_cents, line_total_cents, tour_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), b.getId(), i + 1, StringUtils.hasText(l.title()) ? l.title().trim() : "Service " + (i + 1),
                l.details(), l.quantity(), l.unit(), l.unitPriceCents(), snap.totals().lineTotals().get(i), tourId);
        }
        int leg = 0;
        for (Line l : c.lines()) {
            if (l.legs() == null) continue;
            for (Leg g : l.legs()) {
                leg++;
                int stop = 0;
                if (StringUtils.hasText(g.pickup())) insertStop(b.getId(), leg, ++stop, "PICKUP", g.pickup());
                if (g.dropOffs() != null) for (String d : g.dropOffs()) if (StringUtils.hasText(d)) insertStop(b.getId(), leg, ++stop, "DROP_OFF", d);
            }
        }
        quote.setBookingId(b.getId());
        if (quote.getAccountId() == null) quote.setAccountId(b.getAccountId());
        documents.flush();
        support.event(quotationId, null, "BOOKING_CREATED", null, "Booking " + b.getReference() + " created at the quoted total");
        closeEnquiry(quote, b);
        return new Result(b.getId(), b.getReference(), true);
    }

    private void accept(BillingDocument quote) {
        if (quote.getStatus() == Status.ISSUED) {
            quote.decide(Status.ACCEPTED, BillingSupport.actor());
            support.event(quote.getId(), null, "ACCEPTED", null, "Accepted when the booking was created");
        }
    }

    private void closeEnquiry(BillingDocument quote, Booking booking) {
        if (quote.getEnquiryId() == null) return;
        enquiries.findById(quote.getEnquiryId()).filter(e -> policy.canConvert(e.getStatus()))
            .ifPresent(e -> enquiryService.linkConversion(e, booking));
    }

    /** The account of the quotation's customer, only when that customer has verified their login. */
    private UUID verifiedAccount(BillingDocument quote) {
        return jdbc.query("select c.account_id from billing_documents d join customers c on c.id = d.customer_id where d.id = ?",
            (rs, i) -> (UUID) rs.getObject(1), quote.getId()).stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Optional<com.sltours.tours.model.Tour> tour(List<Line> lines) {
        Set<String> slugs = new HashSet<>();
        for (Line l : lines) if (StringUtils.hasText(l.tourSlug())) slugs.add(l.tourSlug());
        return slugs.size() == 1 ? tours.findBySlug(slugs.iterator().next()) : Optional.empty();
    }

    static String title(List<Line> lines) {
        String first = lines.stream().map(Line::title).filter(StringUtils::hasText).map(String::trim).findFirst().orElse("Quoted trip");
        String t = lines.size() > 1 ? first + " and " + (lines.size() - 1) + " more" : first;
        return t.length() > 180 ? t.substring(0, 179) + "…" : t;
    }

    private void insertStop(UUID bookingId, int leg, int stop, String kind, String location) {
        jdbc.update("insert into booking_stops (id, booking_id, leg_order, stop_order, kind, location) values (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), bookingId, leg, stop, kind, location.trim().length() > 240 ? location.trim().substring(0, 240) : location.trim());
    }
}
