package com.sltours.tours.dto;

import com.sltours.tours.model.PricingType;
import com.sltours.tours.model.TourCategory;
import java.util.List;

public record TourDetailResponse(
        String slug,
        String title,
        String shortDescription,
        String fullDescription,
        String duration,
        int basePriceCents,
        PricingType pricingType,
        int minimumGuests,
        int maximumGuests,
        List<String> locations,
        List<String> tags,
        List<String> highlights,
        List<String> included,
        List<String> excluded,
        List<String> itinerary,
        List<String> faqs,
        boolean pickupAvailable,
        TourCategory category,
        String heroImage,
        String bannerImage,
        String seoTitle,
        String seoDescription,
        String heroImageAlt,
        String pickupSummary,
        String groupSizeLabel,
        String availabilityLabel,
        String tourTypeLabel,
        String itineraryNote,
        List<String> practicalInfo,
        com.sltours.tours.model.EnquiryForm enquiryForm
) {
}
