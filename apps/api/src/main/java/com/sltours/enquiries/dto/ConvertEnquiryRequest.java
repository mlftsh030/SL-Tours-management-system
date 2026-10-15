package com.sltours.enquiries.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** Converts an enquiry into a tour booking. Customer details are copied from the enquiry, never re-entered. */
public record ConvertEnquiryRequest(
    @NotNull @Min(0) Long version,
    @NotBlank @Size(max = 160) String tourSlug,
    @NotNull @FutureOrPresent LocalDate travelDate,
    @Min(1) @Max(60) int guests,
    @Size(max = 240) String pickupLocation,
    @Size(max = 1200) String notes
) {}
