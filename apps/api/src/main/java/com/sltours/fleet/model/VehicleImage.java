package com.sltours.fleet.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vehicle_images")
public class VehicleImage {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false) private UUID vehicleId;
    @Column(nullable = false, unique = true) private UUID mediaId;
    @Column(nullable = false, length = 200) private String altText;
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    protected VehicleImage() {}
    public VehicleImage(UUID vehicleId, UUID mediaId, String altText, int sortOrder) {
        this.vehicleId = vehicleId; this.mediaId = mediaId; this.altText = altText; this.sortOrder = sortOrder;
    }
    public UUID getId() { return id; }
    public UUID getVehicleId() { return vehicleId; }
    public UUID getMediaId() { return mediaId; }
    public String getAltText() { return altText; }
    public void setAltText(String value) { altText = value; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int value) { sortOrder = value; }
}
