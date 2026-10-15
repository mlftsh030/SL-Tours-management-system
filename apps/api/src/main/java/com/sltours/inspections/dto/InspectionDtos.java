package com.sltours.inspections.dto;
import com.sltours.assignments.model.AssignmentStatus;
import com.sltours.inspections.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class InspectionDtos {
    private InspectionDtos() {}
    /**
     * Submitted as the JSON part "inspection" of a multipart request. {@code photoTags} has one entry per uploaded
     * photo, in order: "VEHICLE" for an overview photo or the checklist item a defect photo shows.
     */
    public record InspectionForm(@NotNull @Min(0) @Max(2_000_000) Integer odometerKm, @NotNull FuelLevel fuelLevel,
        @Size(max = 2000) String notes, @NotNull @Size(min = 1, max = 40) List<@Valid @NotNull ChecklistAnswer> checklist,
        @NotNull @Size(max = 24) List<@NotBlank @Size(max = 40) String> photoTags) {}
    public record SubmissionResult(UUID inspectionId, Inspection.Type type, AssignmentStatus assignmentStatus, String outcome) {}
    public record ChecklistDefinition(ChecklistItem item, String label, boolean safetyCritical) {}
    public record Photo(UUID mediaId, String url, InspectionPhoto.Kind kind, ChecklistItem checklistItem) {}
    public record ChecklistLine(ChecklistItem item, String label, ChecklistAnswer.Result result, ChecklistAnswer.Severity severity, String note) {}
    /** Full record for administrators, including review details. */
    public record InspectionView(UUID id, long version, Inspection.Type type, UUID assignmentId, UUID driverId, String driverName,
        UUID vehicleId, String vehicleName, String registration, String submittedBy, OffsetDateTime submittedAt, int odometerKm,
        FuelLevel fuelLevel, String notes, List<ChecklistLine> checklist, boolean hasDefect, boolean hasSafetyDefect, String selfieUrl,
        List<Photo> photos, Inspection.ReviewStatus reviewStatus, String reviewedBy, OffsetDateTime reviewedAt, String reviewNotes) {}
    public record Comparison(UUID assignmentId, AssignmentStatus assignmentStatus, InspectionView collection, InspectionView returned,
        Integer distanceKm, Integer fuelChangePercent, List<ChecklistLine> newlyReportedDefects, List<ChecklistLine> persistingDefects) {}
    public record ReviewRequest(@NotNull @Min(0) Long version, @Size(max = 1000) String notes) {}
    public record PendingReview(UUID inspectionId, Inspection.Type type, UUID assignmentId, String bookingReference, String driverName,
        String vehicleName, OffsetDateTime submittedAt, boolean hasDefect, boolean hasSafetyDefect) {}
}
