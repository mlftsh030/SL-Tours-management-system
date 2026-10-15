package com.sltours.fleet.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Blocks a vehicle while open. Only an explicit, audited administrator action clears it. */
@Entity
@Table(name = "vehicle_restrictions")
public class VehicleRestriction {
    public enum Type { SAFETY, MAINTENANCE }
    @Id private UUID id = UUID.randomUUID();
    @Version private Long version;
    @Column(nullable = false) private UUID vehicleId;
    @Enumerated(EnumType.STRING) @Column(name = "restriction_type", nullable = false, length = 20) private Type type;
    @Column(nullable = false, length = 1000) private String reason;
    private UUID sourceInspectionId;
    @Column(nullable = false, length = 180) private String openedBy;
    @Column(nullable = false) private OffsetDateTime openedAt = OffsetDateTime.now();
    @Column(length = 180) private String clearedBy;
    private OffsetDateTime clearedAt;
    @Column(length = 1000) private String clearanceNote;
    protected VehicleRestriction() {}
    public VehicleRestriction(UUID vehicleId, Type type, String reason, UUID sourceInspectionId, String openedBy) {
        this.vehicleId = vehicleId; this.type = type; this.reason = reason; this.sourceInspectionId = sourceInspectionId; this.openedBy = openedBy;
    }
    public UUID getId() { return id; }
    public long getVersion() { return version == null ? 0 : version; }
    public UUID getVehicleId() { return vehicleId; }
    public Type getType() { return type; }
    public String getReason() { return reason; }
    public UUID getSourceInspectionId() { return sourceInspectionId; }
    public String getOpenedBy() { return openedBy; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public String getClearedBy() { return clearedBy; }
    public OffsetDateTime getClearedAt() { return clearedAt; }
    public String getClearanceNote() { return clearanceNote; }
    public boolean isOpen() { return clearedAt == null; }
    public void clear(String actor, String note) { clearedBy = actor; clearedAt = OffsetDateTime.now(); clearanceNote = note; }
}
