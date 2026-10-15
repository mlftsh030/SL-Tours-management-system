package com.sltours.privatetrips.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Additional fee disclosed before submission. */
@Entity
@Table(name = "private_trip_fees")
public class PrivateTripFee {
    public enum Type { FIXED, PER_BILLABLE_HOUR }
    @Id private UUID id = UUID.randomUUID();
    @Version private Long version;
    @Column(nullable = false, length = 120) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "fee_type", nullable = false, length = 20) private Type type;
    @Column(nullable = false) private int amountCents;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false, length = 180) private String updatedBy;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    public UUID getId() { return id; }
    public long getVersion() { return version == null ? 0 : version; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public int getAmountCents() { return amountCents; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public void update(String name, Type type, int amountCents, boolean active, int sortOrder, String actor) {
        this.name = name; this.type = type; this.amountCents = amountCents; this.active = active; this.sortOrder = sortOrder;
        updatedBy = actor; updatedAt = OffsetDateTime.now();
    }
}
