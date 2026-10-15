package com.sltours.privatetrips;
import com.sltours.assignments.model.*;
import com.sltours.assignments.repository.AssignmentRepository;
import com.sltours.bookings.model.*;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.drivers.model.Driver;
import com.sltours.enquiries.model.*;
import com.sltours.enquiries.service.EnquiryService;
import com.sltours.fleet.model.*;
import com.sltours.fleet.repository.VehicleRepository;
import com.sltours.fleet.service.RestrictionService;
import com.sltours.operations.service.OperationsService;
import com.sltours.privatetrips.dto.PrivateTripDtos.*;
import com.sltours.privatetrips.repository.PrivateTripDetailsRepository;
import com.sltours.privatetrips.service.*;
import com.sltours.shared.error.ConflictException;
import com.sltours.support.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import static org.assertj.core.api.Assertions.*;

@WithMockUser(username = "admin@test.local", roles = "ADMIN")
class PrivateTripIntegrationTest extends PostgresIntegrationTest {
    @Autowired Fixtures fixtures;
    @Autowired PrivateTripService trips;
    @Autowired VehicleOptionService vehicleOptions;
    @Autowired PrivateTripAdminService admin;
    @Autowired PricingAdminService pricing;
    @Autowired BookingRepository bookings;
    @Autowired PrivateTripDetailsRepository details;
    @Autowired AssignmentRepository assignments;
    @Autowired RestrictionService restrictions;
    @Autowired VehicleRepository vehicles;
    @Autowired com.sltours.fleet.service.FleetService fleetService;
    @Autowired OperationsService operations;
    @Autowired EnquiryService enquiries;
    @Autowired com.sltours.assignments.service.AssignmentService assignmentService;

    private LocalDate futureDate(int days) { return LocalDate.now().plusDays(days); }
    private PrivateTripRequest request(UUID vehicleId, LocalDate date, int minutes, int expectedTotal) {
        return new PrivateTripRequest(UUID.randomUUID(), vehicleId, date, LocalTime.of(10, 0), minutes, "Camps Bay", "Chapman's Peak\nCape Point",
            4, "Child seat please", "Lerato M", "Lerato@Example.com", "+27 82 111 2222", expectedTotal, null);
    }

    private int quoteFor(Vehicle vehicle, LocalDate date) {
        return trips.search(date, LocalTime.of(10, 0), 180, 4).vehicles().stream().filter(c -> c.id().equals(vehicle.getId()))
            .findFirst().orElseThrow().quote().totalCents();
    }

    @Test void searchShowsOnlyListedOperationalUnrestrictedFreeVehiclesThatFitWithBackendPrices() {
        LocalDate date = futureDate(40);
        Vehicle listed = fixtures.vehicle(VehicleCategory.LUXURY_VAN, 7, Vehicle.ListingStatus.LISTED);
        Vehicle hidden = fixtures.vehicle(VehicleCategory.LUXURY_VAN, 7, Vehicle.ListingStatus.HIDDEN);
        Vehicle tooSmall = fixtures.vehicle(VehicleCategory.SEDAN, 3, Vehicle.ListingStatus.LISTED);
        Vehicle restricted = fixtures.vehicle(VehicleCategory.LUXURY_VAN, 7, Vehicle.ListingStatus.LISTED);
        restrictions.open(restricted.getId(), VehicleRestriction.Type.MAINTENANCE, "Gearbox service", null);
        Vehicle busy = fixtures.vehicle(VehicleCategory.LUXURY_VAN, 7, Vehicle.ListingStatus.LISTED);
        Driver driver = fixtures.driver(true);
        // Busy until 13:00 plus 60 minutes turnaround, so a 13:30 start must not be offered.
        OffsetDateTime busyStart = date.atTime(9, 0).atZone(PrivateTripService.ZONE).toOffsetDateTime();
        fixtures.assignedAt(busy, driver, busyStart, busyStart.plusHours(4));

        SearchResult result = trips.search(date, LocalTime.of(13, 30), 210, 4);
        var ids = result.vehicles().stream().map(VehicleCard::id).toList();
        assertThat(ids).contains(listed.getId()).doesNotContain(hidden.getId(), tooSmall.getId(), restricted.getId(), busy.getId());
        VehicleCard card = result.vehicles().stream().filter(c -> c.id().equals(listed.getId())).findFirst().orElseThrow();
        // Sample LUXURY_VAN rate R850/h, 3.5 h rounds up to 4 h.
        assertThat(card.quote().billableMinutes()).isEqualTo(240);
        assertThat(card.quote().baseCents()).isEqualTo(340_000);
        assertThat(card.sampleRate()).isTrue();
        assertThat(trips.search(date, LocalTime.of(14, 0), 120, 4).vehicles()).extracting(VehicleCard::id).contains(busy.getId());
    }

    @Test void aFleetVehicleLinkedToAModelShowsThatModelAndItsCreditedPicture() {
        LocalDate date = futureDate(46);
        Vehicle staria = fixtures.vehicle(VehicleCategory.VAN, 8, Vehicle.ListingStatus.LISTED);
        staria.setVehicleOptionSlug("hyundai-staria");
        vehicles.saveAndFlush(staria);
        Vehicle unlinked = fixtures.vehicle(VehicleCategory.VAN, 8, Vehicle.ListingStatus.LISTED);
        var model = vehicleOptions.find("hyundai-staria").orElseThrow();

        var cards = trips.search(date, LocalTime.of(9, 0), 240, 4).vehicles();
        VehicleCard linked = cards.stream().filter(c -> c.id().equals(staria.getId())).findFirst().orElseThrow();
        assertThat(linked.optionSlug()).isEqualTo("hyundai-staria");
        assertThat(linked.imageUrl()).isIn(model.imageThumbnailUrl(), model.imageUrl());
        assertThat(linked.imageAlt()).isEqualTo(model.imageAlt());
        assertThat(linked.illustrativeImage()).isEqualTo(model.illustrativeImage());
        assertThat(linked.imageCredit()).isEqualTo(model.imageCredit());
        VehicleCard other = cards.stream().filter(c -> c.id().equals(unlinked.getId())).findFirst().orElseThrow();
        assertThat(other.optionSlug()).isNull();
        assertThat(other.imageUrl()).isNull();
    }

    @Test void aVehicleCanOnlyBeLinkedToAPublishedModelOfItsOwnCategory() {
        var request = new com.sltours.fleet.dto.FleetDtos.SaveVehicleRequest(0L, "TEST MODEL 1", "Staria", VehicleCategory.SEDAN, 8, null,
            Vehicle.OperationalStatus.ACTIVE, Vehicle.ListingStatus.HIDDEN, null, null, null, null, null, List.of(), "hyundai-staria");
        assertThatThrownBy(() -> fleetService.create(request)).hasMessageContaining("Hyundai Staria");
        var unknown = new com.sltours.fleet.dto.FleetDtos.SaveVehicleRequest(0L, "TEST MODEL 2", "Mystery", VehicleCategory.VAN, 8, null,
            Vehicle.OperationalStatus.ACTIVE, Vehicle.ListingStatus.HIDDEN, null, null, null, null, null, List.of(), "no-such-model");
        assertThatThrownBy(() -> fleetService.create(unknown)).hasMessageContaining("published vehicle model");
    }

    @Test void submissionRepricesOnTheServerAndStoresTheQuotedBreakdown() {
        Vehicle vehicle = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.LISTED);
        LocalDate date = futureDate(41);
        int total = trips.search(date, LocalTime.of(10, 0), 180, 4).vehicles().stream().filter(c -> c.id().equals(vehicle.getId()))
            .findFirst().orElseThrow().quote().totalCents();
        assertThatThrownBy(() -> trips.submit(request(vehicle.getId(), date, 180, total - 100), null))
            .isInstanceOf(ConflictException.class).hasMessageContaining("price has changed");
        PrivateTripRequest request = request(vehicle.getId(), date, 180, total);
        PrivateTripReceipt receipt = trips.submit(request, null);
        assertThat(receipt.status()).isEqualTo(BookingStatus.ENQUIRY);
        assertThat(receipt.message()).contains("not yet a confirmed booking");
        assertThat(trips.submit(request, null).duplicate()).isTrue();
        Booking booking = bookings.findByReference(receipt.reference()).orElseThrow();
        assertThat(booking.getBookingType()).isEqualTo(BookingType.PRIVATE_TRIP);
        assertThat(booking.getTotalAmountCents()).isEqualTo(total).isEqualTo(195_000);
        var stored = details.findById(booking.getId()).orElseThrow();
        assertThat(stored.getHourlyRateCents()).isEqualTo(65_000);
        assertThat(stored.isRateIsSample()).isTrue();
        assertThat(stored.getPriceBreakdown()).containsPattern("\"totalCents\":\\s*195000");

        // Later rate changes do not alter the stored quote.
        var rate = pricing.get().rates().stream().filter(r -> r.category() == VehicleCategory.VAN).findFirst().orElseThrow();
        pricing.saveRate(VehicleCategory.VAN, new SaveRateRequest(rate.version(), 70_000, 180));
        assertThat(admin.get(receipt.reference()).quote().totalCents()).isEqualTo(195_000);
        pricing.saveRate(VehicleCategory.VAN, new SaveRateRequest(rate.version() + 1, 65_000, 180));
    }

    @Test void concurrentConfirmationsCannotDoubleBookAVehicle() throws Exception {
        Vehicle vehicle = fixtures.vehicle(VehicleCategory.MINIBUS, 13, Vehicle.ListingStatus.LISTED);
        Driver driverA = fixtures.driver(true), driverB = fixtures.driver(true);
        LocalDate date = futureDate(42);
        int total = trips.search(date, LocalTime.of(10, 0), 180, 4).vehicles().stream().filter(c -> c.id().equals(vehicle.getId()))
            .findFirst().orElseThrow().quote().totalCents();
        String first = trips.submit(request(vehicle.getId(), date, 180, total), null).reference();
        String second = trips.submit(request(vehicle.getId(), date, 180, total), null).reference();

        var auth = new UsernamePasswordAuthenticationToken("admin@test.local", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (var pair : List.of(Map.entry(first, driverA), Map.entry(second, driverB))) {
            results.add(pool.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(auth);
                start.await();
                try {
                    admin.confirm(pair.getKey(), new ConfirmRequest(bookings.findByReference(pair.getKey()).orElseThrow().getVersion(),
                        vehicle.getId(), pair.getValue().getId()));
                    return "confirmed";
                } catch (ConflictException e) { return "conflict"; }
                finally { SecurityContextHolder.clearContext(); }
            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (var result : results) outcomes.add(result.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertThat(outcomes).containsExactlyInAnyOrder("confirmed", "conflict");
        long live = assignments.findAll().stream().filter(a -> vehicle.getId().equals(a.getVehicleId()) && a.getStatus() != AssignmentStatus.CANCELLED).count();
        assertThat(live).isEqualTo(1);
    }

    @Test void theSameDriverCannotBeConfirmedOnOverlappingTripsAndCancellationFreesResources() {
        Vehicle a = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.LISTED);
        Vehicle b = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.LISTED);
        Driver driver = fixtures.driver(true);
        LocalDate date = futureDate(43);
        int total = quoteFor(a, date);
        String first = trips.submit(request(a.getId(), date, 180, total), null).reference();
        String second = trips.submit(request(b.getId(), date, 180, total), null).reference();
        admin.confirm(first, new ConfirmRequest(0L, a.getId(), driver.getId()));
        assertThatThrownBy(() -> admin.confirm(second, new ConfirmRequest(0L, b.getId(), driver.getId())))
            .isInstanceOf(ConflictException.class).hasMessageContaining("already assigned");
        assertThatThrownBy(() -> operations.updateStatus(second, BookingStatus.CONFIRMED, 0))
            .hasMessageContaining("Confirm & assign");

        Booking confirmed = bookings.findByReference(first).orElseThrow();
        operations.updateStatus(first, BookingStatus.CANCELLED, confirmed.getVersion());
        assertThat(assignments.findFirstByBookingIdAndStatusNot(confirmed.getId(), AssignmentStatus.CANCELLED)).isEmpty();
        assertThat(admin.confirm(second, new ConfirmRequest(0L, b.getId(), driver.getId())).status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test void cancellingAConfirmedTripsAssignmentFreesTheVehicleAndAllowsReassignment() {
        Vehicle first = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.LISTED);
        Vehicle second = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.LISTED);
        Driver driver = fixtures.driver(true);
        LocalDate date = futureDate(45);
        int total = quoteFor(first, date);
        String reference = trips.submit(request(first.getId(), date, 180, total), null).reference();
        AdminPrivateTrip confirmed = admin.confirm(reference, new ConfirmRequest(0L, first.getId(), driver.getId()));
        assertThatThrownBy(() -> admin.confirm(reference, new ConfirmRequest(confirmed.bookingVersion(), second.getId(), driver.getId())))
            .hasMessageContaining("CONFIRMED");
        var assignment = assignments.findById(confirmed.assignment().id()).orElseThrow();
        assignmentService.cancel(assignment.getId(), assignment.getVersion(), "Vehicle needed elsewhere");
        // The first vehicle is free again for the same window.
        assertThat(trips.search(date, LocalTime.of(10, 0), 180, 4).vehicles()).extracting(VehicleCard::id).contains(first.getId());
        AdminPrivateTrip reassigned = admin.confirm(reference, new ConfirmRequest(confirmed.bookingVersion(), second.getId(), driver.getId()));
        assertThat(reassigned.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reassigned.assignment().vehicleId()).isEqualTo(second.getId());
    }

    @Test void quoteRequestsAndManualPricingGoThroughTheEnquiryInbox() {
        Vehicle vehicle = fixtures.vehicle(VehicleCategory.COACH, 40, Vehicle.ListingStatus.LISTED);
        // COACH has no rate configured, so it is priced on request.
        LocalDate date = futureDate(44);
        VehicleCard card = trips.search(date, LocalTime.of(8, 0), 480, 30).vehicles().stream()
            .filter(c -> c.id().equals(vehicle.getId())).findFirst().orElseThrow();
        assertThat(card.quote()).isNull();
        assertThat(card.quoteOnlyReason()).isEqualTo("Priced on request");
        assertThatThrownBy(() -> trips.submit(request(vehicle.getId(), date, 480, 0), null)).hasMessageContaining("priced on request");

        var receipt = trips.requestQuote(new QuoteRequest(UUID.randomUUID(), date, LocalTime.of(8, 0), 480, "Waterfront",
            "Full-day Winelands and Franschhoek", 30, VehicleCategory.COACH, null, "Group Lead", "group@example.com", "+27 21 000 0000", null, null));
        var enquiry = enquiries.get(receipt.reference());
        assertThat(enquiry.source()).isEqualTo(EnquirySource.PRIVATE_TRIP_QUOTE);
        assertThat(enquiry.durationMinutes()).isEqualTo(480);
        AdminPrivateTrip converted = admin.convertEnquiry(receipt.reference(), new ConvertEnquiryToTripRequest(enquiry.version(), vehicle.getId(),
            date, LocalTime.of(8, 0), 480, "Waterfront", "Full-day Winelands and Franschhoek", 30, 1_250_000));
        assertThat(converted.quote().totalCents()).isEqualTo(1_250_000);
        assertThat(converted.customerEmail()).isEqualTo("group@example.com");
        assertThat(enquiries.get(receipt.reference()).status()).isEqualTo(EnquiryStatus.CONVERTED);
    }

    @Test void allRequestedVehiclesAreListedWithPicturesAndCarriedIntoQuotes() {
        List<VehicleOption> options = vehicleOptions.published();
        assertThat(options).extracting(VehicleOption::slug).containsExactly("volkswagen-polo-sedan", "toyota-rumion", "hyundai-staria",
            "mercedes-benz-v-class", "toyota-quantum", "minibus-22-seater", "coach-42-seater", "coach-72-seater");
        assertThat(options).extracting(VehicleOption::passengerCapacity).containsExactly(3, 5, 8, 8, 13, 22, 42, 72);
        assertThat(options).allSatisfy(o -> {
            assertThat(o.imageUrl()).startsWith("/assets/vehicles/");
            assertThat(o.imageCredit()).isNotBlank();
            assertThat(o.imageLicence()).isNotBlank();
        });
        // Capacities not yet checked against a specific vehicle's seating are flagged, never presented as confirmed.
        assertThat(options).filteredOn(o -> !o.capacityConfirmed()).extracting(VehicleOption::slug)
            .containsExactly("mercedes-benz-v-class", "minibus-22-seater", "coach-42-seater", "coach-72-seater");

        var receipt = trips.requestQuote(new QuoteRequest(UUID.randomUUID(), LocalDate.now().plusDays(20), LocalTime.of(8, 0), 480, "Waterfront",
            "Winelands day", 20, VehicleCategory.MINIBUS, null, "Group Lead", "group@example.com", null, null, "minibus-22-seater"));
        assertThat(enquiries.get(receipt.reference()).message()).contains("Preferred vehicle: Minibus or midibus", "up to 22 passengers", "capacity to be confirmed",
            "Availability to be confirmed");
        assertThatThrownBy(() -> trips.requestQuote(new QuoteRequest(UUID.randomUUID(), LocalDate.now().plusDays(20), null, null, "Waterfront", "Day", 2, null,
            null, "Guest", "guest@example.com", null, null, "unknown-vehicle"))).hasMessageContaining("Choose a vehicle from the list");
    }
}
