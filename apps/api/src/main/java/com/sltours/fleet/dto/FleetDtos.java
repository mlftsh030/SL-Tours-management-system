package com.sltours.fleet.dto;
import com.sltours.fleet.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class FleetDtos {
    private FleetDtos() {}
    public record Image(UUID id, UUID mediaId, String url, String altText, int sortOrder) {}
    public record Restriction(UUID id, long version, VehicleRestriction.Type type, String reason, UUID sourceInspectionId, String openedBy,
        OffsetDateTime openedAt, String clearedBy, OffsetDateTime clearedAt, String clearanceNote) {}
    public record VehicleSummary(UUID id, String registration, String displayName, VehicleCategory category, int capacity,
        Integer luggageCapacity, Vehicle.OperationalStatus operationalStatus, Vehicle.ListingStatus listingStatus,
        List<Restriction> openRestrictions, String imageUrl, Integer hourlyRateCents, boolean demoData) {}
    public record VehicleDetail(UUID id, long version, String registration, String displayName, VehicleCategory category, int capacity,
        Integer luggageCapacity, Vehicle.OperationalStatus operationalStatus, Vehicle.ListingStatus listingStatus, String publicDescription,
        String operatingNotes, String notes, Integer hourlyRateCents, Integer minimumMinutes, List<String> amenities, List<Image> images,
        List<Restriction> restrictions, boolean demoData, String vehicleOptionSlug,
        List<com.sltours.assignments.dto.AssignmentDtos.AdminAssignment> upcomingAssignments) {}
    public record SaveVehicleRequest(@NotNull @Min(0) Long version,
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9 -]+", message = "Use letters, numbers, spaces and dashes") String registration,
        @NotBlank @Size(max = 120) String displayName, @NotNull VehicleCategory category, @Min(1) @Max(80) int capacity,
        @Min(0) @Max(80) Integer luggageCapacity, @NotNull Vehicle.OperationalStatus operationalStatus,
        @NotNull Vehicle.ListingStatus listingStatus, @Size(max = 500) String publicDescription, @Size(max = 1000) String operatingNotes,
        @Size(max = 2000) String notes, @Min(100) @Max(10_000_000) Integer hourlyRateCents, @Min(60) @Max(1440) Integer minimumMinutes,
        @NotNull @Size(max = 12) List<@NotBlank @Size(max = 80) String> amenities,
        @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String vehicleOptionSlug) {}
    public record OpenRestrictionRequest(@NotNull VehicleRestriction.Type type, @NotBlank @Size(min = 5, max = 1000) String reason) {}
    public record ClearRestrictionRequest(@NotNull @Min(0) Long version, @NotBlank @Size(min = 10, max = 1000) String note) {}
    public record ImageUpdateRequest(@NotBlank @Size(max = 200) String altText, @Min(0) @Max(1000) int sortOrder) {}
}
