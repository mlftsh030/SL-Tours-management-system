package com.sltours.inspections.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "vehicle_inspections")
public class Inspection {
    public enum Type { COLLECTION, RETURN }
    public enum ReviewStatus { PENDING_REVIEW, APPROVED, REJECTED }
    @Id private UUID id = UUID.randomUUID();
    @Version private Long version;
    @Column(nullable = false) private UUID assignmentId;
    @Enumerated(EnumType.STRING) @Column(name = "inspection_type", nullable = false, length = 20) private Type type;
    @Column(nullable = false) private UUID driverId;
    @Column(nullable = false) private UUID vehicleId;
    @Column(nullable = false, length = 180) private String submittedBy;
    @Column(nullable = false) private OffsetDateTime submittedAt = OffsetDateTime.now();
    @Column(nullable = false) private int odometerKm;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FuelLevel fuelLevel;
    @Column(length = 2000) private String notes;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String checklist;
    @Column(nullable = false) private boolean hasDefect;
    @Column(nullable = false) private boolean hasSafetyDefect;
    private UUID selfieMediaId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReviewStatus reviewStatus = ReviewStatus.PENDING_REVIEW;
    @Column(length = 180) private String reviewedBy;
    private OffsetDateTime reviewedAt;
    @Column(length = 1000) private String reviewNotes;
    protected Inspection() {}
    public Inspection(UUID assignmentId, Type type, UUID driverId, UUID vehicleId, String submittedBy) {
        this.assignmentId = assignmentId; this.type = type; this.driverId = driverId; this.vehicleId = vehicleId; this.submittedBy = submittedBy;
    }
    public UUID getId() { return id; }
    public long getVersion() { return version == null ? 0 : version; }
    public UUID getAssignmentId() { return assignmentId; }
    public Type getType() { return type; }
    public UUID getDriverId() { return driverId; }
    public UUID getVehicleId() { return vehicleId; }
    public String getSubmittedBy() { return submittedBy; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public int getOdometerKm() { return odometerKm; }
    public FuelLevel getFuelLevel() { return fuelLevel; }
    public String getNotes() { return notes; }
    public String getChecklist() { return checklist; }
    public boolean isHasDefect() { return hasDefect; }
    public boolean isHasSafetyDefect() { return hasSafetyDefect; }
    public UUID getSelfieMediaId() { return selfieMediaId; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public String getReviewedBy() { return reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public String getReviewNotes() { return reviewNotes; }
    public void record(int odometerKm, FuelLevel fuelLevel, String notes, String checklistJson, boolean hasDefect, boolean hasSafetyDefect, UUID selfieMediaId) {
        this.odometerKm = odometerKm; this.fuelLevel = fuelLevel; this.notes = notes; this.checklist = checklistJson;
        this.hasDefect = hasDefect; this.hasSafetyDefect = hasSafetyDefect; this.selfieMediaId = selfieMediaId;
    }
    public void review(ReviewStatus status, String notes, String actor) {
        reviewStatus = status; reviewNotes = notes; reviewedBy = actor; reviewedAt = OffsetDateTime.now();
    }
}
