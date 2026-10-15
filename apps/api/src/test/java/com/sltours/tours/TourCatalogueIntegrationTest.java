package com.sltours.tours;

import com.sltours.enquiries.dto.CreateEnquiryRequest;
import com.sltours.enquiries.model.EnquiryType;
import com.sltours.enquiries.service.EnquiryService;
import com.sltours.seo.SeoService;
import com.sltours.support.PostgresIntegrationTest;
import com.sltours.tours.dto.*;
import com.sltours.tours.model.*;
import com.sltours.tours.repository.TourRepository;
import com.sltours.tours.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import static org.assertj.core.api.Assertions.*;

/** The nine published offerings, as applied by V22 to the existing tour records. */
class TourCatalogueIntegrationTest extends PostgresIntegrationTest {
    @Autowired TourService tours;
    @Autowired AdminTourService admin;
    @Autowired TourRepository repository;
    @Autowired EnquiryService enquiries;
    @Autowired SeoService seo;
    @Autowired JdbcTemplate jdbc;

    @Test void allNineOfferingsArePublishedOnceWithTheirPriceUnitsAndImages() {
        List<TourSummaryResponse> list = tours.listTours(null, null);
        assertThat(list).extracting(TourSummaryResponse::title).containsExactly("Cape Peninsula & Cape Point", "Cape Town City & Heritage Tour",
            "Township Cultural Experience", "Cape Winelands Tour", "Garden Route Tour", "Aquila Safari Day Tour", "West Coast Tour", "Airport Transfers",
            "Private & Custom Tours");
        assertThat(jdbc.queryForObject("select count(*) from tours", Integer.class)).isEqualTo(9); // updated in place, no duplicates
        assertThat(list).allSatisfy(t -> {
            assertThat(t.heroImage()).startsWith("/assets/tours/");
            assertThat(t.heroImageAlt()).isNotBlank();
            assertThat(t.groupSizeLabel()).isNotBlank();
        });
        Map<String, TourSummaryResponse> bySlug = new HashMap<>();
        list.forEach(t -> bySlug.put(t.slug(), t));
        assertThat(bySlug.get("cape-peninsula-journey")).satisfies(t -> { assertThat(t.basePriceCents()).isEqualTo(210000); assertThat(t.pricingType()).isEqualTo(PricingType.PER_PERSON); });
        assertThat(bySlug.get("airport-transfer")).satisfies(t -> { assertThat(t.basePriceCents()).isEqualTo(130000); assertThat(t.pricingType()).isEqualTo(PricingType.PER_TRIP); });
        assertThat(bySlug.get("private-custom-tour").pricingType()).isEqualTo(PricingType.CUSTOM);
        assertThat(bySlug.get("aquila-safari-day-trip").groupSizeLabel()).isEqualTo("Any group size");
        assertThat(bySlug.get("garden-route-journey").duration()).isEqualTo("3 days, 2 nights");
    }

    @Test void detailPagesCarryLabelledItinerariesAndPracticalNotesButNotReviewNotes() {
        TourDetailResponse city = tours.getTour("cape-town-essentials");
        assertThat(city.itineraryNote()).contains("illustration");
        assertThat(city.practicalInfo()).anySatisfy(p -> assertThat(p).contains("cableway tickets are not included"));
        assertThat(city.included()).noneSatisfy(i -> assertThat(i).containsIgnoringCase("cableway"));
        assertThat(String.valueOf(city)).doesNotContain("conflicts with the final stop");
        TourDetailResponse aquila = tours.getTour("aquila-safari-day-trip");
        assertThat(aquila.practicalInfo()).anySatisfy(p -> assertThat(p).contains("not guaranteed"));
        assertThat(tours.getTour("airport-transfer").enquiryForm()).isEqualTo(EnquiryForm.AIRPORT);
        assertThat(tours.getTour("private-custom-tour").enquiryForm()).isEqualTo(EnquiryForm.CUSTOM);
        assertThat(seo.body("/experiences/airport-transfer")).contains("R1,300 per trip", "#enquire").doesNotContain("/book/");
        assertThat(seo.head("/experiences/private-custom-tour")).contains("\"@type\":\"TouristTrip\"").doesNotContain("\"offers\"");
    }

    @Test @WithMockUser(username = "admin@test.local", roles = "ADMIN")
    void adminSeesReviewNotesAndASaveWithoutExtrasKeepsThem() {
        Tour city = repository.findBySlugAndStatus("cape-town-essentials", TourStatus.ACTIVE).orElseThrow();
        AdminTourResponse view = admin.list(0, 50).getContent().stream().filter(v -> v.id().equals(city.getId())).findFirst().orElseThrow();
        assertThat(view.extras().reviewNotes()).contains("Table Mountain & Cape Town City Tour");
        TourDetailResponse d = view.detail();
        var request = new SaveTourRequest(view.version(), d.slug(), d.title(), d.shortDescription(), d.fullDescription(), d.duration(), d.basePriceCents(),
            d.pricingType(), d.minimumGuests(), d.maximumGuests(), d.pickupAvailable(), false, view.status(), d.category(), d.heroImage(), d.bannerImage(),
            d.seoTitle(), d.seoDescription(), view.cardNote(), view.displayOrder(), d.locations(), d.tags(), d.highlights(), d.included(), d.excluded(),
            d.itinerary(), d.faqs(), null);
        AdminTourResponse saved = admin.update(city.getId(), request);
        assertThat(saved.extras().reviewNotes()).isEqualTo(view.extras().reviewNotes());
        assertThat(saved.detail().practicalInfo()).isEqualTo(d.practicalInfo());
    }

    @Test void airportEnquiriesRecordFlightDirectionDestinationAndLuggage() {
        LocalDate day = LocalDate.now().plusDays(12);
        var request = new CreateEnquiryRequest(UUID.randomUUID(), "Ama Mensah", "ama@example.com", "+27 82 555 0000", "Two child seats please.", EnquiryType.TOUR,
            "airport-transfer", null, null, 3, null,
            new CreateEnquiryRequest.TransferDetails(CreateEnquiryRequest.Direction.ARRIVAL, "BA59", day, LocalTime.of(7, 40), "Sea Point guesthouse", "3 large cases"),
            null);
        var receipt = enquiries.submit(request);
        var detail = enquiries.get(receipt.reference());
        assertThat(detail.message()).contains("Airport transfer: arrival", "Flight: BA59", "Destination: Sea Point guesthouse", "Luggage: 3 large cases",
            "Group size: 3", "Two child seats please.");
        assertThat(detail.requestedDate()).isEqualTo(day);
        assertThat(detail.destination()).isEqualTo("Sea Point guesthouse");
    }
}
