package com.sltours.bookings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateBookingRequest(
        @NotBlank String tourSlug,
        @NotNull @FutureOrPresent LocalDate travelDate,
        @Min(1) @Max(60) int guests,
        @NotBlank @Size(max = 160) String customerName,
        @NotBlank @Email @Size(max = 180) String customerEmail,
        @Size(max = 80) String customerPhone,
        @Size(max = 80) String customerCountry,
        @Size(max = 240) String pickupLocation,
        @Size(max = 1200) String notes,
        /** Optional referral from a partner link or code; see ReferralService for the attribution rules. */
        @jakarta.validation.Valid com.sltours.partners.dto.PartnerDtos.ReferralInput referral
) {
}
