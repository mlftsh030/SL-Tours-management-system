package com.sltours.privatetrips.service;
import com.sltours.assignments.model.*;
import com.sltours.assignments.repository.AssignmentRepository;
import com.sltours.assignments.service.*;
import com.sltours.audit.AuditService;
import com.sltours.bookings.model.*;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.enquiries.model.*;
import com.sltours.enquiries.service.EnquiryService;
import com.sltours.fleet.model.Vehicle;
import com.sltours.fleet.repository.VehicleRepository;
import com.sltours.privatetrips.dto.PrivateTripDtos.*;
import com.sltours.privatetrips.model.PrivateTripDetails;
import com.sltours.privatetrips.repository.PrivateTripDetailsRepository;
import com.sltours.shared.error.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivateTripAdminService {
    private static final Set<BookingStatus> CONFIRMABLE = EnumSet.of(BookingStatus.ENQUIRY, BookingStatus.QUOTE_PENDING, BookingStatus.QUOTE_SENT,
        BookingStatus.AWAITING_PAYMENT);
    private final BookingRepository bookings;
    private final PrivateTripDetailsRepository details;
    private final AssignmentService assignmentService;
    private final AssignmentRepository assignments;
    private final AdminAssignmentService assignmentViews;
    private final VehicleRepository vehicles;
    private final PrivateTripService privateTrips;
    private final EnquiryService enquiries;
    private final AuditService audit;
    public PrivateTripAdminService(BookingRepository bookings, PrivateTripDetailsRepository details, AssignmentService assignmentService,
            AssignmentRepository assignments, AdminAssignmentService assignmentViews, VehicleRepository vehicles, PrivateTripService privateTrips,
            EnquiryService enquiries, AuditService audit) {
        this.bookings = bookings; this.details = details; this.assignmentService = assignmentService; this.assignments = assignments;
        this.assignmentViews = assignmentViews; this.vehicles = vehicles; this.privateTrips = privateTrips; this.enquiries = enquiries; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdminPrivateTrip get(String reference) {
        Booking booking = privateTrip(reference);
        return view(booking, details.findById(booking.getId()).orElseThrow());
    }

    /**
     * Confirms a request: availability, restrictions and driver eligibility are rechecked now, and the database
     * exclusion constraints reject a concurrent overlapping confirmation.
     */
    @Transactional
    public AdminPrivateTrip confirm(String reference, ConfirmRequest request) {
        Booking booking = privateTrip(reference);
        if (booking.getVersion() != request.version()) throw new ConflictException("This booking changed. Reload before confirming.");
        // A confirmed trip whose assignment was cancelled can be given a new vehicle and driver through the same checks.
        boolean reassigning = booking.getStatus() == BookingStatus.CONFIRMED
            && assignments.findFirstByBookingIdAndStatusNot(booking.getId(), AssignmentStatus.CANCELLED).isEmpty();
        if (!CONFIRMABLE.contains(booking.getStatus()) && !reassigning)
            throw new IllegalArgumentException("This booking is " + booking.getStatus() + " and cannot be confirmed");
        PrivateTripDetails trip = details.findById(booking.getId()).orElseThrow();
        assignmentService.assign(booking, request.vehicleId(), request.driverId(), trip.getStartsAt(), trip.getEndsAt());
        BookingStatus previous = booking.getStatus();
        booking.setStatus(BookingStatus.CONFIRMED);
        bookings.flush();
        audit.record(reassigning ? "PRIVATE_TRIP_REASSIGNED" : "PRIVATE_TRIP_CONFIRMED", reference,
            previous + "->CONFIRMED; vehicle=" + request.vehicleId() + "; driver=" + request.driverId());
        return view(booking, trip);
    }

    @Transactional
    public AdminPrivateTrip convertEnquiry(String enquiryReference, ConvertEnquiryToTripRequest request) {
        Enquiry enquiry = enquiries.find(enquiryReference);
        enquiries.checkVersion(enquiry, request.version());
        Booking booking = privateTrips.createForEnquiry(enquiry, request);
        enquiries.linkConversion(enquiry, booking);
        return view(booking, details.findById(booking.getId()).orElseThrow());
    }

    private AdminPrivateTrip view(Booking b, PrivateTripDetails d) {
        var assignment = assignments.findFirstByBookingIdAndStatusNot(b.getId(), AssignmentStatus.CANCELLED).map(assignmentViews::view).orElse(null);
        String vehicleName = d.getRequestedVehicleId() == null ? null : vehicles.findById(d.getRequestedVehicleId()).map(Vehicle::getDisplayName).orElse(null);
        return new AdminPrivateTrip(b.getReference(), b.getVersion(), b.getStatus(), b.getCustomerName(), b.getCustomerEmail(), b.getCustomerPhone(),
            b.getGuests(), d.getStartsAt(), d.getEndsAt(), d.getDurationMinutes(), b.getPickupLocation(), d.getDestination(), d.getSpecialRequests(),
            d.getRequestedVehicleId(), vehicleName, d.getRequestedCategory(), privateTrips.storedQuote(d), assignment);
    }

    private Booking privateTrip(String reference) {
        return bookings.findByReference(reference).filter(b -> b.getBookingType() == BookingType.PRIVATE_TRIP)
            .orElseThrow(() -> new ResourceNotFoundException("Private trip not found"));
    }
}
