package com.sltours.enquiries.dto;
import com.sltours.enquiries.model.EnquiryType;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record CreateEnquiryRequest(
    @NotNull UUID idempotencyKey,
    @NotBlank @Size(max = 160) String customerName,
    @NotBlank @Email @Size(max = 180) String customerEmail,
    @Size(max = 80) String customerPhone,
    @NotBlank @Size(max = 1600) String message,
    EnquiryType enquiryType,
    @Size(max = 160) String tourSlug,
    @FutureOrPresent LocalDate requestedDate,
    @Size(max = 240) String pickupLocation,
    @Min(1) @Max(60) Integer passengers,
    @jakarta.validation.Valid com.sltours.partners.dto.PartnerDtos.ReferralInput referral,
    @jakarta.validation.Valid TransferDetails transfer,
    @jakarta.validation.Valid CustomTourDetails custom
) {
    public enum Direction { ARRIVAL, DEPARTURE }
    /** Airport transfer details. destination is the accommodation (arrivals) or pickup address (departures). */
    public record TransferDetails(@NotNull Direction direction, @Size(max = 20) String flightNumber, @FutureOrPresent LocalDate flightDate,
        java.time.LocalTime flightTime, @NotBlank @Size(max = 240) String destination, @Size(max = 240) String luggage) {}
    /** Custom tour details: travel dates and interests. */
    public record CustomTourDetails(@FutureOrPresent LocalDate startDate, @FutureOrPresent LocalDate endDate, @Size(max = 600) String interests) {}
}
