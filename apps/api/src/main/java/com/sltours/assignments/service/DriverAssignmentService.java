package com.sltours.assignments.service;
import com.sltours.assignments.dto.AssignmentDtos.*;
import com.sltours.assignments.model.*;
import com.sltours.assignments.repository.AssignmentRepository;
import com.sltours.bookings.model.*;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.service.DriverEligibility;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.fleet.model.Vehicle;
import com.sltours.fleet.repository.*;
import com.sltours.fleet.service.FleetService;
import com.sltours.inspections.model.Inspection;
import com.sltours.inspections.repository.*;
import com.sltours.inspections.service.InspectionService;
import com.sltours.privatetrips.repository.PrivateTripDetailsRepository;
import com.sltours.shared.error.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the driver's view of their own assignments. Every lookup is scoped by the driver's id. */
@Service
public class DriverAssignmentService {
    private final AssignmentRepository assignments;
    private final BookingRepository bookings;
    private final PrivateTripDetailsRepository privateTrips;
    private final VehicleRepository vehicles;
    private final VehicleRestrictionRepository restrictions;
    private final FleetService fleet;
    private final InspectionRepository inspections;
    private final InspectionPhotoRepository photos;
    private final InspectionService inspectionService;
    private final DriverEligibility eligibility;
    private final AccountRepository accounts;
    public DriverAssignmentService(AssignmentRepository assignments, BookingRepository bookings, PrivateTripDetailsRepository privateTrips,
            VehicleRepository vehicles, VehicleRestrictionRepository restrictions, FleetService fleet, InspectionRepository inspections,
            InspectionPhotoRepository photos, InspectionService inspectionService, DriverEligibility eligibility, AccountRepository accounts) {
        this.assignments = assignments; this.bookings = bookings; this.privateTrips = privateTrips; this.vehicles = vehicles;
        this.restrictions = restrictions; this.fleet = fleet; this.inspections = inspections; this.photos = photos;
        this.inspectionService = inspectionService; this.eligibility = eligibility; this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<DriverAssignmentSummary> list(Driver driver) {
        return assignments.findByDriverIdAndStatusNotAndBlockedUntilAfterOrderByStartsAtAsc(driver.getId(), AssignmentStatus.CANCELLED,
                OffsetDateTime.now().minusDays(2)).stream()
            .filter(a -> a.getStatus() != AssignmentStatus.CLOSED || a.getEndsAt().isAfter(OffsetDateTime.now().minusDays(1)))
            .map(a -> {
                Booking booking = bookings.findById(a.getBookingId()).orElseThrow();
                Vehicle vehicle = a.getVehicleId() == null ? null : vehicles.findById(a.getVehicleId()).orElse(null);
                return new DriverAssignmentSummary(a.getId(), a.getStatus(), booking.getReference(), booking.getTitle(), a.getStartsAt(), a.getEndsAt(),
                    booking.getPickupLocation(), booking.getGuests(), vehicle == null ? null : vehicle.getDisplayName(),
                    vehicle == null ? null : vehicle.getRegistration(), nextAction(a, driver).action());
            }).toList();
    }

    @Transactional(readOnly = true)
    public DriverAssignmentDetail get(Driver driver, UUID id) {
        Assignment a = assignments.findByIdAndDriverId(id, driver.getId())
            .filter(x -> x.getStatus() != AssignmentStatus.CANCELLED)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        Booking booking = bookings.findById(a.getBookingId()).orElseThrow();
        Vehicle vehicle = vehicles.findById(a.getVehicleId()).orElseThrow();
        var privateTrip = booking.getBookingType() == BookingType.PRIVATE_TRIP ? privateTrips.findById(booking.getId()) : Optional.<com.sltours.privatetrips.model.PrivateTripDetails>empty();
        List<String> itinerary = privateTrip.map(p -> Arrays.stream(p.getDestination().split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList())
            .orElseGet(() -> booking.getTour() == null ? List.of() : List.copyOf(booking.getTour().getItinerary()));
        DriverTrip trip = new DriverTrip(booking.getReference(), booking.getBookingType().name(), booking.getTitle(), a.getStartsAt(), a.getEndsAt(),
            booking.getPickupLocation(), privateTrip.map(p -> p.getDestination().lines().reduce((x, y) -> y).orElse(null)).orElse(booking.getPickupLocation()),
            itinerary, booking.getGuests(), booking.getCustomerName(), booking.getCustomerPhone(),
            privateTrip.map(p -> p.getSpecialRequests()).orElse(booking.getNotes()));
        DriverVehicle vehicleView = new DriverVehicle(vehicle.getDisplayName(), vehicle.getRegistration(), vehicle.getCategory(), vehicle.getCapacity(),
            vehicle.getLuggageCapacity(), List.copyOf(vehicle.getAmenities()), vehicle.getOperatingNotes(), fleet.firstImageUrls(List.of(vehicle.getId())).get(vehicle.getId()));
        Next next = nextAction(a, driver);
        return new DriverAssignmentDetail(a.getId(), a.getStatus(), trip, vehicleView, step(a, Inspection.Type.COLLECTION), step(a, Inspection.Type.RETURN),
            next.blockers(), next.action(), next.canCollect(), next.canReturn(), inspectionService.minimumVehiclePhotos());
    }

    private record Next(String action, List<String> blockers, boolean canCollect, boolean canReturn) {}

    private Next nextAction(Assignment a, Driver driver) {
        List<String> blockers = new ArrayList<>();
        switch (a.getStatus()) {
            case SCHEDULED -> {
                Optional<Inspection> live = inspections.findLive(a.getId(), Inspection.Type.COLLECTION);
                if (live.isPresent() && live.get().isHasSafetyDefect())
                    return new Next("Do not take this vehicle. A safety defect was reported; the office will arrange a replacement.", blockers, false, false);
                boolean enabled = driver.getAccountId() != null && accounts.findById(driver.getAccountId()).map(x -> x.isEnabled()).orElse(false);
                blockers.addAll(eligibility.problems(driver, enabled, a.getStartsAt().atZoneSameInstant(AssignmentService.OPERATING_ZONE).toLocalDate()));
                Vehicle vehicle = vehicles.findById(a.getVehicleId()).orElseThrow();
                if (vehicle.getOperationalStatus() != Vehicle.OperationalStatus.ACTIVE || restrictions.existsByVehicleIdAndClearedAtIsNull(vehicle.getId()))
                    blockers.add("This vehicle is not cleared for use. Contact the office before collecting it.");
                inspectionService.vehicleNotReturned(a).ifPresent(blockers::add);
                String notYet = inspectionService.collectionNotYetOpen(a);
                if (blockers.isEmpty() && notYet != null) return new Next(notYet, blockers, false, false);
                return new Next(blockers.isEmpty() ? "Complete the collection inspection before taking the vehicle." : "Collection is blocked. Contact the office.",
                    blockers, blockers.isEmpty(), false);
            }
            case AWAITING_RELEASE -> { return new Next("Waiting for the office to approve the vehicle release. Do not drive yet.", blockers, false, false); }
            case RELEASED -> { return new Next("Complete the return inspection when you bring the vehicle back.", blockers, false, true); }
            case RETURNED -> { return new Next("Return recorded. The office will review it.", blockers, false, false); }
            case CLOSED -> { return new Next("Completed.", blockers, false, false); }
            default -> { return new Next("Cancelled.", blockers, false, false); }
        }
    }

    private DriverStep step(Assignment a, Inspection.Type type) {
        List<Inspection> all = inspections.findByAssignmentIdOrderBySubmittedAtAsc(a.getId()).stream().filter(i -> i.getType() == type).toList();
        if (all.isEmpty()) return new DriverStep("NOT_SUBMITTED", null, null, null, List.of());
        Inspection latest = all.get(all.size() - 1);
        String state = switch (latest.getReviewStatus()) {
            case REJECTED -> "RESUBMIT";
            case APPROVED -> "APPROVED";
            case PENDING_REVIEW -> type == Inspection.Type.RETURN ? "SUBMITTED"
                : latest.isHasSafetyDefect() ? "BLOCKED"
                : a.getStatus() == AssignmentStatus.RELEASED || a.getStatus() == AssignmentStatus.RETURNED || a.getStatus() == AssignmentStatus.CLOSED ? "RELEASED"
                : "AWAITING_APPROVAL";
        };
        if (state.equals("RESUBMIT")) return new DriverStep(state, latest.getSubmittedAt(), null, null, List.of());
        List<String> urls = new ArrayList<>();
        if (latest.getSelfieMediaId() != null) urls.add("/api/v1/driver/media/" + latest.getSelfieMediaId());
        photos.findByInspectionIdOrderBySortOrderAsc(latest.getId()).forEach(p -> urls.add("/api/v1/driver/media/" + p.getMediaId()));
        return new DriverStep(state, latest.getSubmittedAt(), latest.getOdometerKm(), latest.getFuelLevel(), urls);
    }
}
