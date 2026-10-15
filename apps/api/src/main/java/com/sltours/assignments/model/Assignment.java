package com.sltours.assignments.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A booking's vehicle and driver for a time window. Overlaps are rejected by PostgreSQL exclusion constraints. */
@Entity
@Table(name = "assignments")
public class Assignment {
    @Id private UUID id = UUID.randomUUID();
    @Version private Long version;
    @Column(nullable = false) private UUID bookingId;
    private UUID guideId;
    private UUID driverId;
    private UUID vehicleId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private AssignmentStatus status = AssignmentStatus.SCHEDULED;
    @Column(nullable = false) private OffsetDateTime startsAt;
    @Column(nullable = false) private OffsetDateTime endsAt;
    @Column(nullable = false) private OffsetDateTime blockedUntil;
    private OffsetDateTime releasedAt;
    @Column(length = 180) private String releasedBy;
    @Column(length = 500) private String cancellationReason;
    @Column(nullable = false) private OffsetDateTime createdAt;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }
    public UUID getId() { return id; }
    public long getVersion() { return version == null ? 0 : version; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID value) { bookingId = value; }
    public UUID getDriverId() { return driverId; }
    public void setDriverId(UUID value) { driverId = value; }
    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID value) { vehicleId = value; }
    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus value) { status = value; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public OffsetDateTime getBlockedUntil() { return blockedUntil; }
    public void setWindow(OffsetDateTime startsAt, OffsetDateTime endsAt, int turnaroundMinutes) {
        this.startsAt = startsAt; this.endsAt = endsAt; this.blockedUntil = endsAt.plusMinutes(turnaroundMinutes);
    }
    public OffsetDateTime getReleasedAt() { return releasedAt; }
    public String getReleasedBy() { return releasedBy; }
    public void release(String actor) { status = AssignmentStatus.RELEASED; releasedAt = OffsetDateTime.now(); releasedBy = actor; }
    public String getCancellationReason() { return cancellationReason; }
    public void cancel(String reason) { status = AssignmentStatus.CANCELLED; cancellationReason = reason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
