package com.sltours.fleet.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * A fleet vehicle. Operational status (can it be driven), listing status (is it shown publicly) and
 * assignment state (is it booked) are separate; availability is derived, never stored.
 */
@Entity
@Table(name = "vehicles")
public class Vehicle {
    public enum OperationalStatus { ACTIVE, OUT_OF_SERVICE, RETIRED }
    public enum ListingStatus { LISTED, HIDDEN }
    @Id private UUID id = UUID.randomUUID();
    @Version private Long version;
    @Column(nullable = false, unique = true, length = 40) private String registration;
    /** Legacy free-text type column, kept in step with the display name. */
    @Column(name = "type", nullable = false, length = 80) private String legacyType;
    @Column(nullable = false, length = 120) private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private VehicleCategory category;
    @Column(nullable = false) private int capacity;
    private Integer luggageCapacity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OperationalStatus operationalStatus = OperationalStatus.ACTIVE;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ListingStatus listingStatus = ListingStatus.HIDDEN;
    @Column(length = 500) private String publicDescription;
    /** Practical notes for the assigned driver (fuel type, quirks). Not shown publicly. */
    @Column(length = 1000) private String operatingNotes;
    /** Internal fleet notes. Never shown to drivers or the public. */
    private String notes;
    /** The published vehicle option (model) this vehicle is, or null (V26). */
    @Column(name = "vehicle_option_slug", length = 80) private String vehicleOptionSlug;
    private Integer hourlyRateCents;
    private Integer minimumMinutes;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "vehicle_amenities", joinColumns = @JoinColumn(name = "vehicle_id"))
    @OrderColumn(name = "item_order") @Column(name = "value", nullable = false, length = 80)
    private List<String> amenities = new ArrayList<>();
    @Column(nullable = false) private OffsetDateTime createdAt;
    /** Development sample row from migration V2 (flagged by V12); excluded from inventory outside dev/test. */
    @Column(nullable = false) private boolean demoData;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public boolean isDemoData() { return demoData; }
    public void setDemoData(boolean value) { demoData = value; }
    public UUID getId() { return id; }
    public long getVersion() { return version == null ? 0 : version; }
    public String getRegistration() { return registration; }
    public void setRegistration(String value) { registration = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; legacyType = value.length() > 80 ? value.substring(0, 80) : value; }
    public VehicleCategory getCategory() { return category; }
    public void setCategory(VehicleCategory value) { category = value; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int value) { capacity = value; }
    public Integer getLuggageCapacity() { return luggageCapacity; }
    public void setLuggageCapacity(Integer value) { luggageCapacity = value; }
    public OperationalStatus getOperationalStatus() { return operationalStatus; }
    public void setOperationalStatus(OperationalStatus value) { operationalStatus = value; }
    public ListingStatus getListingStatus() { return listingStatus; }
    public void setListingStatus(ListingStatus value) { listingStatus = value; }
    public String getPublicDescription() { return publicDescription; }
    public void setPublicDescription(String value) { publicDescription = value; }
    public String getOperatingNotes() { return operatingNotes; }
    public void setOperatingNotes(String value) { operatingNotes = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public Integer getHourlyRateCents() { return hourlyRateCents; }
    public void setHourlyRateCents(Integer value) { hourlyRateCents = value; }
    public String getVehicleOptionSlug() { return vehicleOptionSlug; }
    public void setVehicleOptionSlug(String value) { vehicleOptionSlug = value; }
    public Integer getMinimumMinutes() { return minimumMinutes; }
    public void setMinimumMinutes(Integer value) { minimumMinutes = value; }
    public List<String> getAmenities() { return amenities; }
    public void setAmenities(List<String> value) { amenities.clear(); amenities.addAll(value); }
}
