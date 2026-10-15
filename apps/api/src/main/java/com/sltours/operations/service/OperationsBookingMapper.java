package com.sltours.operations.service;
import com.sltours.assignments.model.*;
import com.sltours.assignments.repository.AssignmentRepository;
import com.sltours.bookings.model.Booking;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.repository.DriverRepository;
import com.sltours.fleet.model.Vehicle;
import com.sltours.fleet.repository.VehicleRepository;
import com.sltours.operations.dto.OperationsBookingResponse;
import org.springframework.stereotype.Component;
@Component
public class OperationsBookingMapper {
    private final AssignmentRepository assignments;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    public OperationsBookingMapper(AssignmentRepository assignments, VehicleRepository vehicles, DriverRepository drivers) {
        this.assignments = assignments; this.vehicles = vehicles; this.drivers = drivers;
    }
    public OperationsBookingResponse toResponse(Booking b) {
        var assignment = assignments.findFirstByBookingIdAndStatusNot(b.getId(), AssignmentStatus.CANCELLED);
        String vehicle = assignment.map(Assignment::getVehicleId).flatMap(vehicles::findById)
            .map(v -> v.getDisplayName() + " · " + v.getRegistration()).orElse("Unassigned");
        String driver = assignment.map(Assignment::getDriverId).flatMap(drivers::findById).map(Driver::getName).orElse("Unassigned");
        return new OperationsBookingResponse(b.getReference(), b.getCustomerName(), b.getTitle(),
            b.getTravelDate(), b.getGuests(), b.getTotalAmountCents(), b.getAmountPaidCents(), b.getStatus(),
            "Unassigned", vehicle, b.getCustomerEmail(), b.getCustomerPhone(), b.getCustomerCountry(),
            b.getPickupLocation(), b.getNotes(), b.getAccountId() != null, b.getCreatedAt(), b.getVersion(),
            b.getBookingType().name(), driver, assignment.map(a -> a.getStatus().name()).orElse(null), b.isDemoData());
    }
}
