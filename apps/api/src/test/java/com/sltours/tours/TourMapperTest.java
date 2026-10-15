package com.sltours.tours;

import static org.assertj.core.api.Assertions.assertThat;

import com.sltours.tours.model.PricingType;
import com.sltours.tours.model.Tour;
import com.sltours.tours.model.TourCategory;
import com.sltours.tours.model.TourStatus;
import com.sltours.tours.service.TourMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class TourMapperTest {
    private final TourMapper mapper = new TourMapper();

    @Test
    void mapsTourSummaryWithoutRecalculatingDomainFields() {
        Tour tour = new Tour();
        tour.setSlug("cape-peninsula-journey");
        tour.setTitle("Cape Peninsula Journey");
        tour.setShortDescription("Coastal full-day tour");
        tour.setFullDescription("Full detail");
        tour.setDuration("Full day");
        tour.setBasePriceCents(210000);
        tour.setPricingType(PricingType.PER_PERSON);
        tour.setMinimumGuests(1);
        tour.setMaximumGuests(18);
        tour.setPickupAvailable(true);
        tour.setFeatured(true);
        tour.setStatus(TourStatus.ACTIVE);
        tour.setCategory(TourCategory.PENINSULA);
        tour.setHeroImage("/assets/cape-point-tour.jpg");
        tour.setSeoTitle("Cape Peninsula");
        tour.setSeoDescription("Cape Peninsula tour");
        tour.setTags(List.of("nature", "full day"));
        tour.setCardNote("Included: Entrance fees, toll fee and coffee.");

        var response = mapper.toSummary(tour);

        assertThat(response.slug()).isEqualTo("cape-peninsula-journey");
        assertThat(response.basePriceCents()).isEqualTo(210000);
        assertThat(response.cardNote()).isEqualTo("Included: Entrance fees, toll fee and coffee.");
        assertThat(response.tags()).containsExactly("nature", "full day");
    }
}
