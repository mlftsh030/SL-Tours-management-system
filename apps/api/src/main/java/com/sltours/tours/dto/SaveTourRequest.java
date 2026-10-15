package com.sltours.tours.dto;
import com.sltours.tours.model.*;
import jakarta.validation.constraints.*;
import java.util.List;

public record SaveTourRequest(
    @NotNull @Min(0) Long version,
    @NotBlank @Size(max = 160) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
    @NotBlank @Size(max = 180) String title,
    @NotBlank @Size(max = 500) String shortDescription,
    @NotBlank @Size(max = 2500) String fullDescription,
    @NotBlank @Size(max = 80) String duration,
    @Min(0) @Max(30000000) int basePriceCents,
    @NotNull PricingType pricingType,
    @Min(1) @Max(60) int minimumGuests,
    @Min(1) @Max(60) int maximumGuests,
    boolean pickupAvailable,
    boolean featured,
    @NotNull TourStatus status,
    @NotNull TourCategory category,
    @NotBlank @Size(max = 220) String heroImage,
    @Size(max = 220) String bannerImage,
    @NotBlank @Size(max = 220) String seoTitle,
    @NotBlank @Size(max = 500) String seoDescription,
    @Size(max = 160) String cardNote,
    @Min(0) @Max(100000) int displayOrder,
    @NotNull @Size(max = 30) List<@NotBlank @Size(max = 255) String> locations,
    @NotNull @Size(max = 30) List<@NotBlank @Size(max = 255) String> tags,
    @NotNull @Size(max = 30) List<@NotBlank @Size(max = 255) String> highlights,
    @NotNull @Size(max = 30) List<@NotBlank @Size(max = 255) String> included,
    @NotNull @Size(max = 30) List<@NotBlank @Size(max = 255) String> excluded,
    @NotNull @Size(max = 30) List<@NotBlank @Size(max = 500) String> itinerary,
    @NotNull @Size(max = 30) List<@NotBlank @Size(max = 800) String> faqs,
    @jakarta.validation.Valid TourExtras extras
) {}
