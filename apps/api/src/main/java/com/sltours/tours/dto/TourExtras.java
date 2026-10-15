package com.sltours.tours.dto;
import com.sltours.tours.model.EnquiryForm;
import jakarta.validation.constraints.*;
import java.util.List;

/** Summary labels, practical information and review notes of a tour. In a save request, null keeps the stored values. */
public record TourExtras(
    @Size(max = 160) String pickupSummary,
    @Size(max = 80) String groupSizeLabel,
    @Size(max = 160) String availabilityLabel,
    @Size(max = 80) String tourTypeLabel,
    @Size(max = 200) String heroImageAlt,
    @Size(max = 600) String itineraryNote,
    @NotNull @Size(max = 20) List<@NotBlank @Size(max = 600) String> practicalInfo,
    @Size(max = 4000) String reviewNotes,
    @Size(max = 300) @Pattern(regexp = "(https?://.+)?") String sourceUrl,
    @NotNull EnquiryForm enquiryForm
) {}
