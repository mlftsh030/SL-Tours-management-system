package com.sltours.tours.dto;

import com.sltours.tours.model.PricingType;
import com.sltours.tours.model.TourCategory;
import java.util.List;

public record TourSummaryResponse(
        String slug,
        String title,
        String shortDescription,
        String duration,
        int basePriceCents,
        PricingType pricingType,
        TourCategory category,
        boolean featured,
        String heroImage,
        String cardNote,
        List<String> tags,
        String heroImageAlt,
        String groupSizeLabel,
        int minimumGuests
) {
}
