package com.sltours.tripplanner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CreateTripPlanningRequest(
        @NotBlank @Size(max = 160) String customerName,
        @NotBlank @Email @Size(max = 180) String customerEmail,
        @Size(max = 80) String customerPhone,
        @NotNull @FutureOrPresent LocalDate arrivalDate,
        @NotNull @FutureOrPresent LocalDate departureDate,
        @Min(1) @Max(60) int adults,
        @Min(0) @Max(60) int children,
        @NotBlank @Size(max = 80) String groupType,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 80) String> interests,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 80) String> services,
        @Size(max = 1600) String notes,
        /** Optional client-generated key that turns retries into a single stored request. */
        java.util.UUID idempotencyKey,
        @jakarta.validation.Valid com.sltours.partners.dto.PartnerDtos.ReferralInput referral
) {
}
