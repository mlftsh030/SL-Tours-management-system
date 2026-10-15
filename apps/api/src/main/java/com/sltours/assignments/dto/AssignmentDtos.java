package com.sltours.assignments.dto;
import com.sltours.assignments.model.AssignmentStatus;
import com.sltours.fleet.model.VehicleCategory;
import com.sltours.inspections.model.FuelLevel;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class AssignmentDtos {
    private AssignmentDtos() {}
    public record AdminAssignment(UUID id, long version, AssignmentStatus status, String bookingReference, String bookingType, String title,
        OffsetDateTime startsAt, OffsetDateTime endsAt, OffsetDateTime blockedUntil, int passengers, UUID vehicleId, String vehicleName,
        String registration, UUID driverId, String driverName, String collectionState, String returnState, List<String> problems) {}
    public record CreateAssignmentRequest(@NotBlank String bookingReference, @NotNull UUID vehicleId, @NotNull UUID driverId,
        @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt) {}
    public record ChangeAssignmentRequest(@NotNull @Min(0) Long version, @NotNull UUID vehicleId, @NotNull UUID driverId) {}
    public record CancelAssignmentRequest(@NotNull @Min(0) Long version, @NotBlank @Size(max = 500) String reason) {}
    public record ResourceOption(UUID id, String name, String detail, boolean available, List<String> reasons) {}
    public record ResourceOptions(List<ResourceOption> vehicles, List<ResourceOption> drivers, int turnaroundMinutes) {}

    // Driver portal: only what the driver needs to carry out their own assignment.
    public record DriverAssignmentSummary(UUID id, AssignmentStatus status, String bookingReference, String title, OffsetDateTime startsAt,
        OffsetDateTime endsAt, String pickupLocation, int passengers, String vehicleName, String registration, String nextAction) {}
    public record DriverTrip(String bookingReference, String type, String title, OffsetDateTime startsAt, OffsetDateTime endsAt,
        String pickupLocation, String dropOff, List<String> itinerary, int passengers, String customerName, String customerPhone,
        String specialRequests) {}
    public record DriverVehicle(String displayName, String registration, VehicleCategory category, int capacity, Integer luggageCapacity,
        List<String> amenities, String operatingNotes, String imageUrl) {}
    /** State: NOT_SUBMITTED, AWAITING_APPROVAL, RELEASED, BLOCKED, RESUBMIT, SUBMITTED or APPROVED. Review notes are never included. */
    public record DriverStep(String state, OffsetDateTime submittedAt, Integer odometerKm, FuelLevel fuelLevel, List<String> photoUrls) {}
    public record DriverAssignmentDetail(UUID id, AssignmentStatus status, DriverTrip trip, DriverVehicle vehicle, DriverStep collection,
        DriverStep returned, List<String> blockers, String nextAction, boolean canCollect, boolean canReturn, int minimumVehiclePhotos) {}
}
