package com.sltours.privatetrips.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.assignments.service.*;
import com.sltours.bookings.model.*;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.bookings.service.BookingReferenceGenerator;
import com.sltours.enquiries.dto.EnquiryReceipt;
import com.sltours.enquiries.model.*;
import com.sltours.enquiries.service.EnquiryService;
import com.sltours.fleet.model.*;
import com.sltours.fleet.repository.*;
import com.sltours.fleet.service.FleetService;
import com.sltours.privatetrips.dto.PrivateTripDtos.*;
import com.sltours.privatetrips.model.*;
import com.sltours.privatetrips.repository.*;
import com.sltours.shared.error.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Public hourly private-trip flow. The backend is the only source of availability and price: the customer's
 * request is re-priced and re-checked on submission, and the quoted breakdown is stored with the booking.
 */
@Service
public class PrivateTripService {
    public static final ZoneId ZONE = AssignmentService.OPERATING_ZONE;
    private final VehicleOptionService options;
    private final AvailabilityService availability;
    private final VehicleRepository vehicles;
    private final FleetService fleet;
    private final PrivateTripRateRepository rates;
    private final PrivateTripFeeRepository fees;
    private final OperationsSettingsRepository settings;
    private final BookingRepository bookings;
    private final BookingReferenceGenerator references;
    private final PrivateTripDetailsRepository details;
    private final AccountRepository accounts;
    private final EnquiryService enquiries;
    private final ObjectMapper json;
    private final boolean allowSampleRates;
    private final com.sltours.shared.demo.DemoDataPolicy demo;
    private final com.sltours.partners.service.ReferralService referrals;

    public PrivateTripService(AvailabilityService availability, VehicleRepository vehicles, FleetService fleet, PrivateTripRateRepository rates,
            PrivateTripFeeRepository fees, OperationsSettingsRepository settings, BookingRepository bookings, BookingReferenceGenerator references,
            PrivateTripDetailsRepository details, AccountRepository accounts, EnquiryService enquiries, ObjectMapper json,
            @Value("${sltours.pricing.allow-sample-rates:false}") boolean allowSampleRates, com.sltours.shared.demo.DemoDataPolicy demo,
            com.sltours.partners.service.ReferralService referrals, VehicleOptionService options) {
        this.referrals = referrals; this.options = options;
        this.availability = availability; this.vehicles = vehicles; this.fleet = fleet; this.rates = rates; this.fees = fees;
        this.settings = settings; this.bookings = bookings; this.references = references; this.details = details; this.accounts = accounts;
        this.enquiries = enquiries; this.json = json; this.allowSampleRates = allowSampleRates; this.demo = demo;
    }

    /** The rate that applies to a vehicle, or empty when it can only be quoted manually. */
    public record ResolvedRate(int hourlyRateCents, int minimumMinutes, PrivateTripDetails.RateSource source, boolean sample) {}

    public Optional<ResolvedRate> resolve(Vehicle vehicle, boolean includeSample) {
        PrivateTripRate category = rates.findById(vehicle.getCategory()).orElse(null);
        if (vehicle.getHourlyRateCents() != null) {
            int minimum = vehicle.getMinimumMinutes() != null ? vehicle.getMinimumMinutes() : category != null ? category.getMinimumMinutes() : 60;
            return Optional.of(new ResolvedRate(vehicle.getHourlyRateCents(), minimum, PrivateTripDetails.RateSource.VEHICLE, false));
        }
        if (category == null || category.getHourlyRateCents() == null) return Optional.empty();
        boolean sample = category.getRateStatus() == PrivateTripRate.Status.SAMPLE;
        if (sample && !includeSample) return Optional.empty();
        return Optional.of(new ResolvedRate(category.getHourlyRateCents(), category.getMinimumMinutes(), PrivateTripDetails.RateSource.CATEGORY, sample));
    }

    @Transactional(readOnly = true)
    public FleetResult fleet() {
        var listed = vehicles.findAllByOrderByDisplayNameAsc().stream()
            .filter(v -> v.getListingStatus() == Vehicle.ListingStatus.LISTED && v.getOperationalStatus() != Vehicle.OperationalStatus.RETIRED)
            .filter(v -> !demo.excludes(v.isDemoData())).toList();
        return new FleetResult(cards(listed, null, null), disclosures());
    }

    @Transactional(readOnly = true)
    public SearchResult search(LocalDate date, LocalTime time, int durationMinutes, int passengers) {
        OffsetDateTime start = start(date, time, durationMinutes);
        OffsetDateTime end = start.plusMinutes(durationMinutes);
        var available = availability.vehicles(start, end, passengers, true).stream()
            .filter(AvailabilityService.VehicleOption::available).map(AvailabilityService.VehicleOption::vehicle).toList();
        return new SearchResult(start, end, durationMinutes, passengers, cards(available, durationMinutes, start), disclosures());
    }

    @Transactional
    public PrivateTripReceipt submit(PrivateTripRequest request, String authenticatedEmail) {
        var previous = bookings.findByIdempotencyKey(request.idempotencyKey());
        if (previous.isPresent()) return receipt(previous.get(), true);
        OffsetDateTime start = start(request.date(), request.startTime(), request.durationMinutes());
        OffsetDateTime end = start.plusMinutes(request.durationMinutes());
        Vehicle vehicle = vehicles.findById(request.vehicleId()).filter(v -> v.getListingStatus() == Vehicle.ListingStatus.LISTED)
            .filter(v -> !demo.excludes(v.isDemoData()))
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        var option = availability.vehicles(start, end, request.passengers(), true).stream()
            .filter(o -> o.vehicle().getId().equals(vehicle.getId())).findFirst().orElseThrow();
        if (!option.available()) throw new ConflictException("Sorry, " + vehicle.getDisplayName() + " is no longer available for this time. Please choose another vehicle.");
        ResolvedRate rate = resolve(vehicle, allowSampleRates).orElseThrow(() ->
            new IllegalArgumentException("This vehicle is priced on request. Please ask for a quote instead."));
        PriceCalculator.Breakdown price = price(rate, request.durationMinutes());
        if (price.totalCents() != request.expectedTotalCents())
            throw new ConflictException("The price has changed since you started. Please review the updated price before submitting.");

        Booking booking = new Booking();
        booking.setReference(references.nextReference(bookings));
        booking.setBookingType(BookingType.PRIVATE_TRIP);
        booking.setIdempotencyKey(request.idempotencyKey());
        booking.setTravelDate(request.date());
        booking.setGuests(request.passengers());
        booking.setCustomerName(request.customerName().trim());
        booking.setCustomerEmail(request.customerEmail().trim().toLowerCase(Locale.ROOT));
        booking.setCustomerPhone(request.customerPhone().trim());
        booking.setPickupLocation(request.pickupLocation().trim());
        booking.setNotes(trim(request.specialRequests()));
        booking.setStatus(BookingStatus.ENQUIRY);
        booking.setTotalAmountCents(price.totalCents());
        booking.setAmountPaidCents(0);
        if (authenticatedEmail != null) {
            accounts.findByEmail(authenticatedEmail).filter(Account::isEnabled).filter(a -> a.getRole() == Account.Role.CUSTOMER).ifPresent(a -> {
                booking.setAccountId(a.getId()); booking.setCustomerEmail(a.getEmail()); booking.setCustomerName(a.getName());
            });
        }
        bookings.saveAndFlush(booking);
        referrals.attachToBooking(booking.getId(), request.referral());
        saveDetails(booking, vehicle, start, request.durationMinutes(), request.destination(), request.specialRequests(), rate, price);
        return receipt(booking, false);
    }

    /** Manual pricing: the itinerary goes to the enquiries inbox as a private-trip quote request. */
    @Transactional
    public EnquiryReceipt requestQuote(QuoteRequest request) {
        var previous = enquiries.existing(request.idempotencyKey());
        if (previous.isPresent()) return previous.get();
        Enquiry enquiry = new Enquiry();
        EnquiryService.applyContact(enquiry, request.customerName(), request.customerEmail(), request.customerPhone());
        enquiry.setRequestedDate(request.date());
        enquiry.setRequestedTime(request.startTime());
        enquiry.setDurationMinutes(request.durationMinutes());
        enquiry.setPickupLocation(request.pickupLocation().trim());
        enquiry.setDestination(request.destination().trim());
        enquiry.setPassengers(request.passengers());
        enquiry.setIdempotencyKey(request.idempotencyKey());
        StringBuilder message = new StringBuilder("Private trip quote request");
        Optional<VehicleOption> option = options.find(request.preferredVehicle());
        if (request.preferredVehicle() != null && option.isEmpty()) throw new IllegalArgumentException("Choose a vehicle from the list");
        if (option.isPresent()) {
            VehicleOption o = option.get();
            message.append("\nPreferred vehicle: ").append(o.name()).append(" (").append(o.typeLabel()).append(", up to ").append(o.passengerCapacity())
                .append(" passengers").append(o.capacityConfirmed() ? "" : ", capacity to be confirmed").append("). Availability to be confirmed.");
        } else if (request.preferredCategory() != null)
            message.append("\nPreferred vehicle: ").append(request.preferredCategory().name().replace('_', ' ').toLowerCase(Locale.ROOT));
        if (StringUtils.hasText(request.specialRequests())) message.append("\n").append(request.specialRequests().trim());
        enquiry.setMessage(message.toString());
        Enquiry saved = enquiries.recordPrivateTripQuote(enquiry);
        referrals.attachToEnquiry(saved.getId(), request.referral());
        return new EnquiryReceipt(saved.getReference(), saved.getStatus(),
            "Thank you. SL Tours will price your itinerary and reply with a quote.", false);
    }

    /** Creates an unconfirmed private-trip booking on behalf of a customer (enquiry conversion). Admins may price manually. */
    @Transactional
    public Booking createForEnquiry(Enquiry enquiry, ConvertEnquiryToTripRequest request) {
        OffsetDateTime start = start(request.date(), request.startTime(), request.durationMinutes());
        Vehicle vehicle = fleet.find(request.vehicleId());
        Optional<ResolvedRate> rate = resolve(vehicle, allowSampleRates);
        PriceCalculator.Breakdown price = null;
        if (request.manualTotalCents() == null) {
            if (rate.isEmpty()) throw new IllegalArgumentException("This vehicle has no live hourly rate. Enter a manual total for this quote.");
            price = price(rate.get(), request.durationMinutes());
        }
        Booking booking = new Booking();
        booking.setReference(references.nextReference(bookings));
        booking.setBookingType(BookingType.PRIVATE_TRIP);
        booking.setTravelDate(request.date());
        booking.setGuests(request.passengers());
        booking.setCustomerName(enquiry.getCustomerName());
        booking.setCustomerEmail(enquiry.getCustomerEmail());
        booking.setCustomerPhone(enquiry.getCustomerPhone());
        booking.setPickupLocation(request.pickupLocation().trim());
        booking.setNotes("Converted from enquiry " + enquiry.getReference());
        booking.setStatus(BookingStatus.ENQUIRY);
        booking.setTotalAmountCents(price == null ? request.manualTotalCents() : price.totalCents());
        booking.setAmountPaidCents(0);
        bookings.saveAndFlush(booking);
        if (price == null) saveManualDetails(booking, vehicle, start, request);
        else saveDetails(booking, vehicle, start, request.durationMinutes(), request.destination(), null, rate.get(), price);
        return booking;
    }

    public PriceCalculator.Breakdown price(ResolvedRate rate, int durationMinutes) {
        var activeFees = fees.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
            .map(f -> new PriceCalculator.Fee(f.getName(), f.getType().name(), f.getAmountCents())).toList();
        return PriceCalculator.price(rate.hourlyRateCents(), durationMinutes, rate.minimumMinutes(), settings.current().getBillingIncrementMinutes(), activeFees);
    }

    public static PriceQuote quote(ResolvedRate rate, PriceCalculator.Breakdown b) {
        return new PriceQuote(rate.hourlyRateCents(), rate.source(), rate.sample(), b.requestedMinutes(), b.minimumMinutes(), b.billableMinutes(),
            b.incrementMinutes(), b.minimumApplied(), b.roundedUp(), b.baseCents(), b.fees(), b.feesCents(), b.totalCents());
    }

    public PriceQuote storedQuote(PrivateTripDetails d) {
        try {
            if (d.getRateSource() == PrivateTripDetails.RateSource.MANUAL)
                return new PriceQuote(null, d.getRateSource(), false, d.getDurationMinutes(), d.getMinimumMinutes(), d.getBillableMinutes(),
                    d.getBillingIncrementMinutes(), false, false, d.getBaseAmountCents(), List.of(), d.getFeesCents(), d.getTotalCents());
            return json.readValue(d.getPriceBreakdown(), PriceQuote.class);
        } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private void saveDetails(Booking booking, Vehicle vehicle, OffsetDateTime start, int duration, String destination, String specialRequests,
            ResolvedRate rate, PriceCalculator.Breakdown price) {
        PrivateTripDetails d = new PrivateTripDetails(booking.getId());
        d.setWindow(start, duration); d.setDestination(destination.trim()); d.setSpecialRequests(trim(specialRequests));
        d.setRequestedVehicleId(vehicle.getId()); d.setRequestedCategory(vehicle.getCategory());
        try {
            d.setPrice(rate.hourlyRateCents(), rate.source(), rate.sample(), price.minimumMinutes(), price.billableMinutes(), price.incrementMinutes(),
                price.baseCents(), price.feesCents(), price.totalCents(), json.writeValueAsString(quote(rate, price)));
        } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        details.saveAndFlush(d);
    }

    private void saveManualDetails(Booking booking, Vehicle vehicle, OffsetDateTime start, ConvertEnquiryToTripRequest r) {
        PrivateTripDetails d = new PrivateTripDetails(booking.getId());
        d.setWindow(start, r.durationMinutes()); d.setDestination(r.destination().trim());
        d.setRequestedVehicleId(vehicle.getId()); d.setRequestedCategory(vehicle.getCategory());
        d.setPrice(null, PrivateTripDetails.RateSource.MANUAL, false, r.durationMinutes(), r.durationMinutes(),
            settings.current().getBillingIncrementMinutes(), r.manualTotalCents(), 0, r.manualTotalCents(),
            "{\"manual\":true,\"totalCents\":" + r.manualTotalCents() + "}");
        details.saveAndFlush(d);
    }

    private List<VehicleCard> cards(List<Vehicle> list, Integer durationMinutes, OffsetDateTime start) {
        Map<UUID, String> images = fleet.firstImageUrls(list.stream().map(Vehicle::getId).toList());
        Map<String, VehicleOption> models = new HashMap<>();
        options.published().forEach(o -> models.put(o.slug(), o));
        List<VehicleCard> cards = new ArrayList<>();
        for (Vehicle v : list) {
            Optional<ResolvedRate> rate = resolve(v, allowSampleRates);
            PriceQuote quote = rate.isPresent() && durationMinutes != null ? quote(rate.get(), price(rate.get(), durationMinutes)) : null;
            VehicleOption model = v.getVehicleOptionSlug() == null ? null : models.get(v.getVehicleOptionSlug());
            // The vehicle's own photo first; otherwise its model's picture, labelled and credited as that picture is.
            String imageUrl = images.get(v.getId()), alt, credit = null;
            boolean illustrative = false;
            if (imageUrl != null) alt = fleet.imagesFor(v.getId()).stream().findFirst().map(i -> i.altText()).orElse(v.getDisplayName());
            else if (model != null) {
                imageUrl = model.imageThumbnailUrl() != null ? model.imageThumbnailUrl() : model.imageUrl();
                alt = model.imageAlt(); illustrative = model.illustrativeImage(); credit = model.imageCredit();
            } else alt = v.getDisplayName();
            cards.add(new VehicleCard(v.getId(), v.getDisplayName(), v.getCategory(), v.getCapacity(), v.getLuggageCapacity(), List.copyOf(v.getAmenities()),
                v.getPublicDescription(), imageUrl, alt, rate.map(ResolvedRate::hourlyRateCents).orElse(null),
                rate.map(ResolvedRate::minimumMinutes).orElse(null), rate.map(ResolvedRate::sample).orElse(false), quote,
                rate.isEmpty() ? "Priced on request" : null, v.getVehicleOptionSlug(), illustrative, credit));
        }
        // Priced vehicles first, then by capacity so the smallest suitable vehicle leads.
        cards.sort(Comparator.comparing((VehicleCard c) -> c.quote() == null && c.hourlyRateCents() == null).thenComparingInt(VehicleCard::capacity));
        return cards;
    }

    private Disclosures disclosures() {
        OperationsSettings s = settings.current();
        return new Disclosures(s.getBillingIncrementMinutes(), s.getIncludedKmPerHour(), s.getExcessKmRateCents(), s.getOvertimeHourlyRateCents(),
            fees.findByActiveTrueOrderBySortOrderAscNameAsc().stream().map(f -> new FeeDisclosure(f.getName(), f.getType(), f.getAmountCents())).toList(),
            allowSampleRates);
    }

    private PrivateTripReceipt receipt(Booking booking, boolean duplicate) {
        PrivateTripDetails d = details.findById(booking.getId()).orElseThrow();
        String vehicleName = d.getRequestedVehicleId() == null ? null : vehicles.findById(d.getRequestedVehicleId()).map(Vehicle::getDisplayName).orElse(null);
        return new PrivateTripReceipt(booking.getReference(), booking.getStatus(),
            "Request received. This is not yet a confirmed booking: SL Tours will check the vehicle and a driver and confirm with you.",
            storedQuote(d), d.getStartsAt(), d.getEndsAt(), vehicleName, duplicate);
    }

    static OffsetDateTime start(LocalDate date, LocalTime time, int durationMinutes) {
        if (durationMinutes % 30 != 0) throw new IllegalArgumentException("Choose a duration in whole or half hours");
        OffsetDateTime start = date.atTime(time).atZone(ZONE).toOffsetDateTime();
        if (!start.isAfter(OffsetDateTime.now())) throw new IllegalArgumentException("Choose a start time in the future");
        return start;
    }
    private static String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
