package com.sltours.assignments.service;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.assignments.model.*;
import com.sltours.assignments.repository.AssignmentRepository;
import com.sltours.audit.AuditService;
import com.sltours.bookings.model.*;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.repository.DriverRepository;
import com.sltours.drivers.service.DriverEligibility;
import com.sltours.fleet.model.Vehicle;
import com.sltours.fleet.repository.*;
import com.sltours.inspections.model.*;
import com.sltours.inspections.repository.InspectionRepository;
import com.sltours.shared.error.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and changes vehicle/driver assignments. Service checks give clear messages; the PostgreSQL exclusion
 * constraints are the final guard when two administrators confirm overlapping work at the same moment.
 */
@Service
public class AssignmentService {
    public static final ZoneId OPERATING_ZONE = ZoneId.of("Africa/Johannesburg");
    private static final Set<BookingStatus> CLOSED_BOOKINGS = EnumSet.of(BookingStatus.CANCELLED, BookingStatus.REFUNDED, BookingStatus.COMPLETED);
    private static final Set<AssignmentStatus> NOT_STARTED = EnumSet.of(AssignmentStatus.SCHEDULED, AssignmentStatus.AWAITING_RELEASE);
    private final AssignmentRepository assignments;
    private final VehicleRepository vehicles;
    private final VehicleRestrictionRepository restrictions;
    private final DriverRepository drivers;
    private final AccountRepository accounts;
    private final DriverEligibility eligibility;
    private final OperationsSettingsRepository settings;
    private final InspectionRepository inspections;
    private final AuditService audit;
    private final com.sltours.shared.demo.DemoDataPolicy demo;
    public AssignmentService(AssignmentRepository assignments, VehicleRepository vehicles, VehicleRestrictionRepository restrictions,
            DriverRepository drivers, AccountRepository accounts, DriverEligibility eligibility, OperationsSettingsRepository settings,
            InspectionRepository inspections, AuditService audit, com.sltours.shared.demo.DemoDataPolicy demo) {
        this.assignments = assignments; this.vehicles = vehicles; this.restrictions = restrictions; this.drivers = drivers;
        this.accounts = accounts; this.eligibility = eligibility; this.settings = settings; this.inspections = inspections; this.audit = audit; this.demo = demo;
    }

    @Transactional
    public Assignment assign(Booking booking, UUID vehicleId, UUID driverId, OffsetDateTime startsAt, OffsetDateTime endsAt) {
        if (CLOSED_BOOKINGS.contains(booking.getStatus())) throw new IllegalArgumentException("This booking is " + booking.getStatus() + " and cannot be assigned");
        if (!endsAt.isAfter(startsAt)) throw new IllegalArgumentException("The assignment must end after it starts");
        if (assignments.findFirstByBookingIdAndStatusNot(booking.getId(), AssignmentStatus.CANCELLED).isPresent())
            throw new ConflictException("This booking already has a vehicle and driver. Change the existing assignment instead.");
        Assignment assignment = new Assignment();
        assignment.setBookingId(booking.getId());
        assignment.setWindow(startsAt, endsAt, settings.current().getTurnaroundMinutes());
        checkResources(vehicleId, driverId, booking.getGuests(), assignment, AssignmentRepository.NONE);
        assignment.setVehicleId(vehicleId); assignment.setDriverId(driverId);
        saveGuarded(assignment);
        audit.record("ASSIGNMENT_CREATED", assignment.getId().toString(),
            "booking=" + booking.getReference() + "; vehicle=" + vehicleId + "; driver=" + driverId);
        return assignment;
    }

    @Transactional
    public Assignment change(UUID id, long version, UUID vehicleId, UUID driverId, int guests) {
        Assignment assignment = find(id);
        if (assignment.getVersion() != version) throw new ConflictException("This assignment changed. Reload before saving.");
        if (!NOT_STARTED.contains(assignment.getStatus()))
            throw new IllegalArgumentException("The vehicle has already been released; record the return before changing it");
        // A changed assignment takes the current turnaround setting (see docs/operations/DEPLOYMENT.md).
        assignment.setWindow(assignment.getStartsAt(), assignment.getEndsAt(), settings.current().getTurnaroundMinutes());
        checkResources(vehicleId, driverId, guests, assignment, assignment.getId());
        boolean changed = !Objects.equals(vehicleId, assignment.getVehicleId()) || !Objects.equals(driverId, assignment.getDriverId());
        if (changed) {
            // A collection inspection belongs to the vehicle and driver it was made for.
            inspections.findLive(assignment.getId(), Inspection.Type.COLLECTION)
                .ifPresent(i -> i.review(Inspection.ReviewStatus.REJECTED, "Superseded: vehicle or driver changed by the office", actor()));
            assignment.setStatus(AssignmentStatus.SCHEDULED);
        }
        String previous = "vehicle=" + assignment.getVehicleId() + "; driver=" + assignment.getDriverId();
        assignment.setVehicleId(vehicleId); assignment.setDriverId(driverId);
        saveGuarded(assignment);
        audit.record("ASSIGNMENT_CHANGED", id.toString(), previous + " -> vehicle=" + vehicleId + "; driver=" + driverId);
        return assignment;
    }

    @Transactional
    public Assignment cancel(UUID id, long version, String reason) {
        Assignment assignment = find(id);
        if (assignment.getVersion() != version) throw new ConflictException("This assignment changed. Reload before saving.");
        cancelOne(assignment, reason);
        return assignment;
    }

    /** Booking cancelled or refunded: release its resources. A vehicle already out must be returned first. */
    @Transactional
    public void cancelForBooking(Booking booking, String reason) {
        for (Assignment assignment : assignments.findByBookingIdAndStatusIn(booking.getId(), EnumSet.of(AssignmentStatus.RELEASED)))
            throw new IllegalArgumentException("Vehicle for assignment " + assignment.getId() + " is out; record its return before cancelling");
        assignments.findByBookingIdAndStatusIn(booking.getId(), NOT_STARTED).forEach(a -> cancelOne(a, reason));
    }

    private void cancelOne(Assignment assignment, String reason) {
        if (!NOT_STARTED.contains(assignment.getStatus()))
            throw new IllegalArgumentException("Only assignments whose vehicle has not been released can be cancelled");
        inspections.findLive(assignment.getId(), Inspection.Type.COLLECTION)
            .filter(i -> i.getReviewStatus() == Inspection.ReviewStatus.PENDING_REVIEW)
            .ifPresent(i -> i.review(Inspection.ReviewStatus.REJECTED, "Assignment cancelled", actor()));
        assignment.cancel(reason);
        assignments.flush();
        audit.record("ASSIGNMENT_CANCELLED", assignment.getId().toString(), reason);
    }

    /** Vehicle must be operational, unrestricted, large enough and free; driver must be eligible and free. */
    private void checkResources(UUID vehicleId, UUID driverId, int passengers, Assignment window, UUID ignore) {
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        if (demo.excludes(vehicle.isDemoData()))
            throw new IllegalArgumentException(vehicle.getDisplayName() + " is a development sample record. Mark it as genuine first if it is a real vehicle.");
        if (vehicle.getOperationalStatus() != Vehicle.OperationalStatus.ACTIVE)
            throw new IllegalArgumentException(vehicle.getDisplayName() + " is not in operational service");
        if (restrictions.existsByVehicleIdAndClearedAtIsNull(vehicleId))
            throw new IllegalArgumentException(vehicle.getDisplayName() + " has an open safety or maintenance restriction");
        if (vehicle.getCapacity() < passengers)
            throw new IllegalArgumentException(vehicle.getDisplayName() + " seats " + vehicle.getCapacity() + ", fewer than " + passengers + " passengers");
        Driver driver = drivers.findById(driverId).orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
        if (demo.excludes(driver.isDemoData()))
            throw new IllegalArgumentException(driver.getName() + " is a development sample record. Mark them as genuine first if they are a real driver.");
        boolean enabled = driver.getAccountId() != null && accounts.findById(driver.getAccountId()).map(Account::isEnabled).orElse(false);
        eligibility.require(driver, enabled, window.getStartsAt().atZoneSameInstant(OPERATING_ZONE).toLocalDate());
        if (!assignments.vehicleConflicts(vehicleId, window.getStartsAt(), window.getBlockedUntil(), ignore).isEmpty())
            throw new ConflictException(vehicle.getDisplayName() + " is already assigned during this time (including turnaround)");
        if (!assignments.driverConflicts(driverId, window.getStartsAt(), window.getBlockedUntil(), ignore).isEmpty())
            throw new ConflictException(driver.getName() + " is already assigned during this time (including turnaround)");
    }

    private void saveGuarded(Assignment assignment) {
        try {
            assignments.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException e) {
            String detail = String.valueOf(e.getMostSpecificCause().getMessage());
            if (detail.contains("assignments_vehicle_no_overlap")) throw new ConflictException("That vehicle was just assigned to an overlapping trip. Choose another vehicle.");
            if (detail.contains("assignments_driver_no_overlap")) throw new ConflictException("That driver was just assigned to an overlapping trip. Choose another driver.");
            if (detail.contains("ux_assignments_active_booking")) throw new ConflictException("This booking was just assigned by someone else. Reload.");
            throw e;
        }
    }

    public Assignment find(UUID id) { return assignments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Assignment not found")); }
    private static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
