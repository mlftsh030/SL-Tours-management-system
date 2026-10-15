package com.sltours.privatetrips.model;
import com.sltours.fleet.model.VehicleCategory;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Trip window, itinerary and the exact price breakdown quoted to the customer when they submitted. */
@Entity
@Table(name = "private_trip_details")
public class PrivateTripDetails {
    public enum RateSource { VEHICLE, CATEGORY, MANUAL }
    @Id private UUID bookingId;
    @Column(nullable = false) private OffsetDateTime startsAt;
    @Column(nullable = false) private OffsetDateTime endsAt;
    @Column(nullable = false) private int durationMinutes;
    @Column(nullable = false, length = 1200) private String destination;
    @Column(length = 1200) private String specialRequests;
    private UUID requestedVehicleId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private VehicleCategory requestedCategory;
    private Integer hourlyRateCents;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RateSource rateSource;
    @Column(nullable = false) private boolean rateIsSample;
    @Column(nullable = false) private int minimumMinutes;
    @Column(nullable = false) private int billableMinutes;
    @Column(nullable = false) private int billingIncrementMinutes;
    @Column(nullable = false) private int baseAmountCents;
    @Column(nullable = false) private int feesCents;
    @Column(nullable = false) private int totalCents;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String priceBreakdown;
    protected PrivateTripDetails() {}
    public PrivateTripDetails(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getBookingId() { return bookingId; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public void setWindow(OffsetDateTime startsAt, int durationMinutes) {
        this.startsAt = startsAt; this.durationMinutes = durationMinutes; this.endsAt = startsAt.plusMinutes(durationMinutes);
    }
    public int getDurationMinutes() { return durationMinutes; }
    public String getDestination() { return destination; }
    public void setDestination(String value) { destination = value; }
    public String getSpecialRequests() { return specialRequests; }
    public void setSpecialRequests(String value) { specialRequests = value; }
    public UUID getRequestedVehicleId() { return requestedVehicleId; }
    public void setRequestedVehicleId(UUID value) { requestedVehicleId = value; }
    public VehicleCategory getRequestedCategory() { return requestedCategory; }
    public void setRequestedCategory(VehicleCategory value) { requestedCategory = value; }
    public Integer getHourlyRateCents() { return hourlyRateCents; }
    public RateSource getRateSource() { return rateSource; }
    public boolean isRateIsSample() { return rateIsSample; }
    public int getMinimumMinutes() { return minimumMinutes; }
    public int getBillableMinutes() { return billableMinutes; }
    public int getBillingIncrementMinutes() { return billingIncrementMinutes; }
    public int getBaseAmountCents() { return baseAmountCents; }
    public int getFeesCents() { return feesCents; }
    public int getTotalCents() { return totalCents; }
    public String getPriceBreakdown() { return priceBreakdown; }
    public void setPrice(Integer hourlyRateCents, RateSource source, boolean sample, int minimumMinutes, int billableMinutes,
            int incrementMinutes, int baseCents, int feesCents, int totalCents, String breakdownJson) {
        this.hourlyRateCents = hourlyRateCents; this.rateSource = source; this.rateIsSample = sample; this.minimumMinutes = minimumMinutes;
        this.billableMinutes = billableMinutes; this.billingIncrementMinutes = incrementMinutes; this.baseAmountCents = baseCents;
        this.feesCents = feesCents; this.totalCents = totalCents; this.priceBreakdown = breakdownJson;
    }
}
