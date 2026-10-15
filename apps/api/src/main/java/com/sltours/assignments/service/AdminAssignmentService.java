package com.sltours.assignments.service;
import com.sltours.assignments.dto.AssignmentDtos.*;
import com.sltours.assignments.model.*;
import com.sltours.assignments.repository.AssignmentRepository;
import com.sltours.bookings.model.*;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.repository.DriverRepository;
import com.sltours.fleet.model.Vehicle;
import com.sltours.fleet.repository.*;
import com.sltours.inspections.model.Inspection;
import com.sltours.inspections.repository.InspectionRepository;
import com.sltours.shared.error.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAssignmentService {
    private final AssignmentRepository assignments;
    private final AssignmentService assignmentService;
    private final AvailabilityService availability;
    private final BookingRepository bookings;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final InspectionRepository inspections;
    private final OperationsSettingsRepository settings;
    private final AssignmentHealthService health;
    public AdminAssignmentService(AssignmentRepository assignments, AssignmentService assignmentService, AvailabilityService availability,
            BookingRepository bookings, VehicleRepository vehicles, DriverRepository drivers, InspectionRepository inspections,
            OperationsSettingsRepository settings, AssignmentHealthService health) {
        this.assignments = assignments; this.assignmentService = assignmentService; this.availability = availability; this.bookings = bookings;
        this.vehicles = vehicles; this.drivers = drivers; this.inspections = inspections; this.settings = settings; this.health = health;
    }

    /** "active" = not yet closed or cancelled; "recent" also includes the last 14 days of finished work. */
    @Transactional(readOnly = true)
    public List<AdminAssignment> list(String scope) {
        List<Assignment> rows = "active".equals(scope)
            ? assignments.findByStatusInOrderByStartsAtAsc(EnumSet.of(AssignmentStatus.SCHEDULED, AssignmentStatus.AWAITING_RELEASE,
                AssignmentStatus.RELEASED, AssignmentStatus.RETURNED))
            : assignments.findByStatusNotAndBlockedUntilAfterOrderByStartsAtAsc(AssignmentStatus.CANCELLED, OffsetDateTime.now().minusDays(14));
        return rows.stream().map(this::view).toList();
    }

    /** Upcoming trips that cannot go ahead as planned; reassign them with {@link #change}. */
    @Transactional(readOnly = true)
    public List<AdminAssignment> needingAttention() {
        return assignments.findByStatusInAndEndsAtAfterOrderByStartsAtAsc(AssignmentHealthService.REASSIGNABLE, OffsetDateTime.now()).stream()
            .map(this::view).filter(a -> !a.problems().isEmpty()).toList();
    }

    /** Upcoming, not yet released trips using this vehicle, with any problems the change caused. */
    @Transactional(readOnly = true)
    public List<AdminAssignment> upcomingForVehicle(UUID vehicleId) {
        return assignments.findByVehicleIdAndStatusInAndEndsAtAfterOrderByStartsAtAsc(vehicleId, AssignmentHealthService.REASSIGNABLE, OffsetDateTime.now())
            .stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAssignment> upcomingForDriver(UUID driverId) {
        return assignments.findByDriverIdAndStatusInAndEndsAtAfterOrderByStartsAtAsc(driverId, AssignmentHealthService.REASSIGNABLE, OffsetDateTime.now())
            .stream().map(this::view).toList();
    }

    @Transactional
    public AdminAssignment create(CreateAssignmentRequest request) {
        Booking booking = bookings.findByReference(request.bookingReference()).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getBookingType() == BookingType.PRIVATE_TRIP)
            throw new IllegalArgumentException("Private trips are assigned when you confirm them, so the trip window and price stay linked");
        return view(assignmentService.assign(booking, request.vehicleId(), request.driverId(), request.startsAt(), request.endsAt()));
    }

    @Transactional
    public AdminAssignment change(UUID id, ChangeAssignmentRequest request) {
        Assignment assignment = assignmentService.find(id);
        int guests = bookings.findById(assignment.getBookingId()).map(Booking::getGuests).orElse(1);
        return view(assignmentService.change(id, request.version(), request.vehicleId(), request.driverId(), guests));
    }

    @Transactional
    public AdminAssignment cancel(UUID id, CancelAssignmentRequest request) {
        return view(assignmentService.cancel(id, request.version(), request.reason().trim()));
    }

    @Transactional(readOnly = true)
    public ResourceOptions options(OffsetDateTime start, OffsetDateTime end, int passengers) {
        if (!end.isAfter(start)) throw new IllegalArgumentException("End must be after start");
        var vehicleOptions = availability.vehicles(start, end, passengers, false).stream().map(o -> new ResourceOption(o.vehicle().getId(),
            o.vehicle().getDisplayName(), o.vehicle().getRegistration() + " · " + o.vehicle().getCapacity() + " seats", o.available(), o.reasons())).toList();
        var driverOptions = availability.drivers(start, end).stream().map(o -> new ResourceOption(o.driver().getId(), o.driver().getName(),
            o.driver().getContact(), o.available(), o.reasons())).toList();
        return new ResourceOptions(vehicleOptions, driverOptions, settings.current().getTurnaroundMinutes());
    }

    public AdminAssignment view(Assignment a) {
        Booking booking = bookings.findById(a.getBookingId()).orElseThrow();
        Vehicle vehicle = a.getVehicleId() == null ? null : vehicles.findById(a.getVehicleId()).orElse(null);
        Driver driver = a.getDriverId() == null ? null : drivers.findById(a.getDriverId()).orElse(null);
        return new AdminAssignment(a.getId(), a.getVersion(), a.getStatus(), booking.getReference(), booking.getBookingType().name(), booking.getTitle(),
            a.getStartsAt(), a.getEndsAt(), a.getBlockedUntil(), booking.getGuests(), a.getVehicleId(), vehicle == null ? null : vehicle.getDisplayName(),
            vehicle == null ? null : vehicle.getRegistration(), a.getDriverId(), driver == null ? null : driver.getName(),
            state(a.getId(), Inspection.Type.COLLECTION), state(a.getId(), Inspection.Type.RETURN), health.problems(a));
    }

    private String state(UUID assignmentId, Inspection.Type type) {
        return inspections.findLive(assignmentId, type)
            .map(i -> i.isHasSafetyDefect() ? "SAFETY_DEFECT" : i.getReviewStatus().name())
            .orElse("NOT_SUBMITTED");
    }
}
