package com.sltours.privatetrips.model;
import com.sltours.fleet.model.VehicleCategory;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Category hourly rate. A SAMPLE rate is development data and is never presented or charged as a live price. */
@Entity
@Table(name = "private_trip_rates")
public class PrivateTripRate {
    public enum Status { SAMPLE, LIVE }
    @Id @Enumerated(EnumType.STRING) @Column(length = 30) private VehicleCategory category;
    @Version private Long version;
    private Integer hourlyRateCents;
    @Column(nullable = false) private int minimumMinutes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Status rateStatus;
    @Column(nullable = false, length = 180) private String updatedBy;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    public VehicleCategory getCategory() { return category; }
    public long getVersion() { return version == null ? 0 : version; }
    public Integer getHourlyRateCents() { return hourlyRateCents; }
    public int getMinimumMinutes() { return minimumMinutes; }
    public Status getRateStatus() { return rateStatus; }
    public String getUpdatedBy() { return updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    /** Any administrator edit makes the rate live: it is now a business-entered price, not seed data. */
    public void update(Integer hourlyRateCents, int minimumMinutes, String actor) {
        this.hourlyRateCents = hourlyRateCents; this.minimumMinutes = minimumMinutes; this.rateStatus = Status.LIVE;
        updatedBy = actor; updatedAt = OffsetDateTime.now();
    }
}
