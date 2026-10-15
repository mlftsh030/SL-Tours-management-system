package com.sltours.privatetrips.dto;
import com.sltours.assignments.dto.AssignmentDtos.AdminAssignment;
import com.sltours.bookings.model.BookingStatus;
import com.sltours.fleet.model.VehicleCategory;
import com.sltours.privatetrips.model.*;
import com.sltours.privatetrips.service.PriceCalculator.FeeLine;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class PrivateTripDtos {
    private PrivateTripDtos() {}
    public record PriceQuote(Integer hourlyRateCents, PrivateTripDetails.RateSource rateSource, boolean sampleRate, int requestedMinutes,
        int minimumMinutes, int billableMinutes, int incrementMinutes, boolean minimumApplied, boolean roundedUp, int baseCents,
        List<FeeLine> fees, int feesCents, int totalCents) {}
    /** Public vehicle card: no registration, internal notes, documents or operational state. optionSlug names the published
     *  model this vehicle is (null if none). Without its own photo the card shows the model's picture, which is then
     *  illustrative and credited. */
    public record VehicleCard(UUID id, String name, VehicleCategory category, int capacity, Integer luggageCapacity, List<String> amenities,
        String description, String imageUrl, String imageAlt, Integer hourlyRateCents, Integer minimumMinutes, boolean sampleRate,
        PriceQuote quote, String quoteOnlyReason, String optionSlug, boolean illustrativeImage, String imageCredit) {}
    public record FeeDisclosure(String name, PrivateTripFee.Type type, int amountCents) {}
    public record Disclosures(int billingIncrementMinutes, Integer includedKmPerHour, Integer excessKmRateCents,
        Integer overtimeHourlyRateCents, List<FeeDisclosure> fees, boolean sampleRatesShown) {}
    public record SearchResult(OffsetDateTime startsAt, OffsetDateTime endsAt, int durationMinutes, int passengers,
        List<VehicleCard> vehicles, Disclosures disclosures) {}
    public record FleetResult(List<VehicleCard> vehicles, Disclosures disclosures) {}
    public record PrivateTripRequest(@NotNull UUID idempotencyKey, @NotNull UUID vehicleId, @NotNull LocalDate date, @NotNull LocalTime startTime,
        @Min(60) @Max(1440) int durationMinutes, @NotBlank @Size(max = 240) String pickupLocation,
        @NotBlank @Size(max = 1200) String destination, @Min(1) @Max(80) int passengers, @Size(max = 1200) String specialRequests,
        @NotBlank @Size(max = 160) String customerName, @NotBlank @Email @Size(max = 180) String customerEmail,
        @NotBlank @Size(max = 80) String customerPhone, @NotNull @Min(0) Integer expectedTotalCents,
        @jakarta.validation.Valid com.sltours.partners.dto.PartnerDtos.ReferralInput referral) {}
    public record QuoteRequest(@NotNull UUID idempotencyKey, @NotNull @FutureOrPresent LocalDate date, LocalTime startTime,
        @Min(60) @Max(1440) Integer durationMinutes, @NotBlank @Size(max = 240) String pickupLocation,
        @NotBlank @Size(max = 1200) String destination, @Min(1) @Max(80) int passengers, VehicleCategory preferredCategory,
        @Size(max = 1200) String specialRequests, @NotBlank @Size(max = 160) String customerName,
        @NotBlank @Email @Size(max = 180) String customerEmail, @Size(max = 80) String customerPhone,
        @jakarta.validation.Valid com.sltours.partners.dto.PartnerDtos.ReferralInput referral,
        @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String preferredVehicle) {}
    /** A vehicle customers can request. capacityConfirmed is false while the requested capacity still needs checking against
     *  the vehicle's seating; illustrativeImage means the picture shows the model or category, not an SL Tours vehicle. */
    public record VehicleOption(String slug, String name, String typeLabel, VehicleCategory category, int passengerCapacity, boolean capacityConfirmed,
        String capacityNote, String description, String imageUrl, String imageThumbnailUrl, String imageAlt, boolean illustrativeImage,
        String imageCredit, String imageSourcePage, String imageLicence, String imageLicenceUrl, LocalDate checkedOn) {}
    public record PrivateTripReceipt(String reference, BookingStatus status, String message, PriceQuote quote, OffsetDateTime startsAt,
        OffsetDateTime endsAt, String vehicleName, boolean duplicate) {}

    public record AdminPrivateTrip(String reference, long bookingVersion, BookingStatus status, String customerName, String customerEmail,
        String customerPhone, int passengers, OffsetDateTime startsAt, OffsetDateTime endsAt, int durationMinutes, String pickupLocation,
        String destination, String specialRequests, UUID requestedVehicleId, String requestedVehicleName, VehicleCategory requestedCategory,
        PriceQuote quote, AdminAssignment assignment) {}
    public record ConfirmRequest(@NotNull @Min(0) Long version, @NotNull UUID vehicleId, @NotNull UUID driverId) {}
    public record ConvertEnquiryToTripRequest(@NotNull @Min(0) Long version, @NotNull UUID vehicleId, @NotNull LocalDate date,
        @NotNull LocalTime startTime, @Min(60) @Max(1440) int durationMinutes, @NotBlank @Size(max = 240) String pickupLocation,
        @NotBlank @Size(max = 1200) String destination, @Min(1) @Max(80) int passengers, @Min(0) @Max(100_000_000) Integer manualTotalCents) {}

    public record RateView(VehicleCategory category, long version, Integer hourlyRateCents, int minimumMinutes, PrivateTripRate.Status rateStatus,
        String updatedBy, OffsetDateTime updatedAt) {}
    public record FeeView(UUID id, long version, String name, PrivateTripFee.Type type, int amountCents, boolean active, int sortOrder) {}
    public record SettingsView(long version, int turnaroundMinutes, int billingIncrementMinutes, Integer includedKmPerHour,
        Integer excessKmRateCents, Integer overtimeHourlyRateCents, String updatedBy, OffsetDateTime updatedAt) {}
    public record PricingView(SettingsView settings, List<RateView> rates, List<FeeView> fees, boolean sampleRatesShownPublicly) {}
    public record SaveSettingsRequest(@NotNull @Min(0) Long version, @Min(0) @Max(1440) int turnaroundMinutes,
        @NotNull Integer billingIncrementMinutes, @Min(1) @Max(1000) Integer includedKmPerHour, @Min(1) @Max(1_000_000) Integer excessKmRateCents,
        @Min(1) @Max(10_000_000) Integer overtimeHourlyRateCents) {}
    public record SaveRateRequest(@NotNull @Min(0) Long version, @Min(100) @Max(10_000_000) Integer hourlyRateCents, @Min(60) @Max(1440) int minimumMinutes) {}
    public record SaveFeeRequest(@NotNull @Min(0) Long version, @NotBlank @Size(max = 120) String name, @NotNull PrivateTripFee.Type type,
        @Min(1) @Max(10_000_000) int amountCents, boolean active, @Min(0) @Max(1000) int sortOrder) {}
}
