package com.sltours.activities;
import com.sltours.activities.dto.ActivityDtos.*;
import com.sltours.activities.dto.ItineraryDtos.*;
import com.sltours.activities.model.ActivityEnums.*;
import com.sltours.activities.service.*;
import com.sltours.seo.SeoService;
import com.sltours.support.CatalogueTestData;
import com.sltours.support.PostgresIntegrationTest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import static org.assertj.core.api.Assertions.*;

@WithMockUser(username = "admin@test.local", roles = "ADMIN")
class ActivityCatalogueIntegrationTest extends PostgresIntegrationTest {
    @Autowired ActivityService activities;
    @Autowired ActivityAdminService admin;
    @Autowired ItineraryService itineraries;
    @Autowired SeoService seo;

    @Test void researchedCatalogueIsSeededWithSourcesAndDraftsStayHidden() {
        List<ActivityCard> cards = activities.list(null, null, null);
        assertThat(cards).hasSizeGreaterThanOrEqualTo(120).extracting(ActivityCard::slug)
            .contains("table-mountain-aerial-cableway", "grandwest-ice-station", "century-karting", "the-labia-theatre", "lions-head-hike", "cango-caves")
            .doesNotContain("kenilworth-karting", "ster-kinekor-va-waterfront", "nu-metro-canal-walk");
        assertThat(activities.list(Category.ICE_SKATING, null, null)).extracting(ActivityCard::slug).containsExactly("grandwest-ice-station");
        // The non-skater fee is an add-on, not the starting price (prices are for planning tools only).
        assertThat(activities.pricing("grandwest-ice-station").fromPriceCents()).isEqualTo(7000);
        ActivityPage cableway = activities.get("table-mountain-aerial-cableway");
        assertThat(cableway.sources()).isNotEmpty().allSatisfy(s -> assertThat(s.url()).startsWith("https://www.tablemountain.net/"));
        assertThat(activities.pricing("table-mountain-aerial-cableway").prices()).anySatisfy(p -> {
            assertThat(p.label()).isEqualTo("Adult return (online)"); assertThat(p.amountCents()).isEqualTo(47500);
            assertThat(p.effectiveTo()).isEqualTo(LocalDate.of(2027, 6, 30)); assertThat(p.sourceUrl()).contains("ticket-prices"); });
        assertThat(activities.pricing("century-karting").priceStatus()).isEqualTo(PriceStatus.NOT_PUBLISHED);
        assertThatThrownBy(() -> activities.get("kenilworth-karting")).hasMessageContaining("not found");
    }

    @Test void everyPublishedListingHasAPictureARegionAndThemes() {
        List<ActivityCard> cards = activities.list(null, null, null);
        assertThat(cards).allSatisfy(c -> {
            assertThat(c.image()).as(c.slug() + " picture").isNotNull();
            assertThat(c.image().url()).startsWith("/assets/");
            assertThat(c.image().credit()).isNotBlank();
            assertThat(c.themes()).as(c.slug() + " themes").isNotEmpty();
            assertThat(c.area() == null || c.region() == Region.CAPE_TOWN).isTrue();
        });
        assertThat(cards).extracting(ActivityCard::region).contains(Region.values());
        assertThat(activities.list(null, null, Region.CAPE_KAROO, null, null)).isNotEmpty().allSatisfy(c -> assertThat(c.region()).isEqualTo(Region.CAPE_KAROO));
        assertThat(activities.list(null, null, null, Theme.WINE, null)).isNotEmpty().allSatisfy(c -> assertThat(c.themes()).contains(Theme.WINE));
        ActivityPage caves = activities.get("cango-caves");
        assertThat(caves.regionLabel()).isEqualTo("Garden Route & Klein Karoo");
        assertThat(caves.plannable()).isFalse();
        assertThat(caves.latitude()).isNotNull();
        assertThat(caves.media()).isNotEmpty();
        assertThat(activities.get("table-mountain-aerial-cableway").plannable()).isTrue();
    }

    @Test void thingsToDoPagesCarryNoPrices() {
        // Road numbers such as R328 are allowed; amounts introduced as a cost, price, fee or hire are not.
        java.util.regex.Pattern price = java.util.regex.Pattern.compile("(?i:cost|price|fee|hire|ticket|admission|entry)[^.]{0,40}\\bR ?\\d");
        for (ActivityCard card : activities.list(null, null, null)) {
            ActivityPage p = activities.get(card.slug());
            String text = String.join(" ", p.summary(), p.description(), String.valueOf(p.whatToExpect()), String.valueOf(p.practicalInfo()),
                String.valueOf(p.bookingNotes()), String.valueOf(p.participationNote()), String.valueOf(p.accompanimentRule()),
                p.faqs().stream().map(f -> f.question() + " " + f.answer()).collect(java.util.stream.Collectors.joining(" ")));
            assertThat(price.matcher(text).find()).as(card.slug() + " mentions a price").isFalse();
        }
    }

    @Test void dayPlansNeverTreatDistantListingsAsNearby() {
        LocalDate day = ActivityViews.today().plusDays(3);
        var request = new ItineraryRequest(day, LocalTime.of(8, 0), LocalTime.of(18, 0), 2, List.of(), Residency.INTERNATIONAL, List.of(),
            Area.CITY_CENTRE, Transport.OWN_VEHICLE, null, null, 3, false, List.of("cango-caves"), null);
        Itinerary plan = itineraries.plan(request);
        assertThat(plan.stops()).noneSatisfy(s -> assertThat(s.slug()).isEqualTo("cango-caves"));
        assertThat(plan.skipped()).anySatisfy(s -> assertThat(s).contains("Cango Caves", "outside the Cape Town areas"));
        assertThat(plan.stops()).filteredOn(s -> s.slug() != null).allSatisfy(s -> assertThat(s.area()).isNotNull());
    }

    @Test void capePointFeesAreNotAssumedAfterTheirPublishedEndDate() {
        LocalDate after = ActivityViews.today().isAfter(LocalDate.of(2026, 10, 31)) ? ActivityViews.today().plusDays(1) : LocalDate.of(2026, 11, 2);
        if (after.getDayOfWeek() == DayOfWeek.SUNDAY) after = after.plusDays(1);
        var request = new ItineraryRequest(after, LocalTime.of(8, 0), LocalTime.of(17, 0), 2, List.of(), Residency.INTERNATIONAL, List.of(),
            Area.CITY_CENTRE, Transport.OWN_VEHICLE, null, 500_000, 1, false, List.of("cape-point"), null);
        Itinerary plan = itineraries.plan(request);
        Stop capePoint = plan.stops().stream().filter(s -> "cape-point".equals(s.slug())).findFirst().orElseThrow();
        assertThat(capePoint.costs()).anySatisfy(l -> { assertThat(l.kind()).isEqualTo(CostKind.UNKNOWN); assertThat(l.detail()).contains("2026-10-31"); });
        assertThat(plan.verdict()).isEqualTo(Verdict.CANNOT_CONFIRM);
    }

    private SaveActivityRequest draft(String slug, long version, List<Source> sources, List<PriceRule> prices) {
        return new SaveActivityRequest(version, slug, "Test Rooftop Cinema", Category.CINEMA, "Outdoor films on a rooftop.", "Screenings in summer.",
            Area.CITY_CENTRE, "1 Test Street, Cape Town", "https://example.org", 120, ScheduleType.SCHEDULE_VARIES, true, false, null, null, null,
            prices.isEmpty() ? PriceStatus.NOT_PUBLISHED : PriceStatus.VERIFIED, "Test notes", null, null, null, null, sources, prices, List.of(), List.of(),
            List.of(new Faq("Is it outdoors?", "Yes, screenings are outdoors.")), ParticipationRules.KNOWN, null, CatalogueTestData.capeTown(Theme.EVENTS));
    }

    @Test void publishingNeedsSourcesAndVerificationAndRefreshesSeoAndSitemapImmediately() {
        String slug = "test-rooftop-cinema-" + UUID.randomUUID().toString().substring(0, 6);
        AdminActivity created = admin.create(draft(slug, 0, List.of(), List.of()));
        assertThat(created.publishProblems()).anySatisfy(p -> assertThat(p).contains("official source"));
        assertThatThrownBy(() -> admin.setStatus(created.id(), created.version(), Status.PUBLISHED)).hasMessageContaining("Cannot publish yet");
        assertThat(seo.sitemap().orElseThrow()).doesNotContain(slug);

        UUID source = UUID.randomUUID();
        var withSource = draft(slug, created.version(), List.of(new Source(source, "https://example.org/prices", "Rooftop Cinema",
            SourceType.OFFICIAL_OPERATOR, "Ticket price", LocalDate.now())), List.of(new PriceRule("Ticket", Audience.EVERYONE, null, null, Residency.ANY,
            Unit.PER_PERSON, 15000, null, null, null, null, true, null, null, source, LocalDate.now(), null, false)));
        AdminActivity sourced = admin.update(created.id(), withSource);
        assertThat(sourced.publishProblems()).containsExactly("Mark the activity as verified after checking the sources");
        AdminActivity verified = admin.verify(created.id(), sourced.version());
        AdminActivity published = admin.setStatus(created.id(), verified.version(), Status.PUBLISHED);
        assertThat(published.status()).isEqualTo(Status.PUBLISHED);

        assertThat(seo.sitemap().orElseThrow()).contains("/explore/" + slug);
        String head = seo.head("/explore/" + slug + "?utm=x");
        assertThat(head).contains("<title>Test Rooftop Cinema | Things to Do in Cape Town | SL Tours</title>", "rel=\"canonical\" href=\"https://sl-tours.com/explore/" + slug + "\"",
            "\"@type\":\"TouristAttraction\"").doesNotContain("FAQPage", "noindex");
        // Things to Do pages show no prices; the price stays available to the day planner.
        assertThat(seo.body("/explore/" + slug)).contains("<h1>Test Rooftop Cinema</h1>", "Is it outdoors?").doesNotContain("R150", "Ticket:");
        assertThat(activities.pricing(slug).prices()).singleElement().satisfies(p -> assertThat(p.amountCents()).isEqualTo(15000));
        assertThat(admin.get(created.id()).regional().themes()).containsExactly(Theme.EVENTS);

        // A published page cannot be edited into a state that breaks the publication rules.
        assertThatThrownBy(() -> admin.update(created.id(), draft(slug, published.version(), List.of(), List.of()))).hasMessageContaining("Unpublish it first");
        admin.setStatus(created.id(), published.version(), Status.DRAFT);
        assertThat(seo.sitemap().orElseThrow()).doesNotContain(slug);
        assertThat(seo.head("/explore/" + slug)).contains("noindex");
    }

    @Test void privateAndUnknownPagesAreNotIndexedAndOutputIsEscaped() {
        assertThat(seo.head("/operations/bookings")).contains("noindex").doesNotContain("canonical");
        assertThat(seo.head("/book/cape-point-tour")).contains("noindex");
        assertThat(seo.head("/partner/referrals")).contains("noindex");
        assertThat(seo.head("/partner-with-us")).contains("<title>Partner with SL Tours", "rel=\"canonical\"").doesNotContain("noindex");
        assertThat(seo.head("/nope\"><script>")).contains("noindex").doesNotContain("<script>alert");
        assertThat(seo.head("/")).contains("\"@type\":\"TravelAgency\"").doesNotContain("makesOffer");
        assertThat(seo.sitemap().orElseThrow()).contains("https://sl-tours.com/explore</loc>", "/experiences/cape-peninsula-journey");
    }

    @Test void officialUncertaintiesAreCarriedIntoPagesAndPlans() {
        ActivityPage ice = activities.get("grandwest-ice-station");
        assertThat(ice.hours()).extracting(PublicHours::daysOfWeek).containsOnly("TUE", "SAT,SUN", "SAT");
        assertThat(ice.participationRules()).isEqualTo(ParticipationRules.PARTLY_KNOWN);
        assertThat(activities.get("century-karting").participationRules()).isEqualTo(ParticipationRules.UNKNOWN);
        assertThat(activities.get("castle-of-good-hope").hours()).allSatisfy(h -> assertThat(h.caution()).contains("15:15", "15:45"));
        assertThat(activities.pricing("the-labia-theatre").prices()).filteredOn(p -> p.audience() == Audience.CHILD)
            .singleElement().satisfies(p -> assertThat(p.caution()).contains("concession ages"));
        assertThat(activities.pricing("neighbourgoods-market").extras()).singleElement().satisfies(x -> assertThat(x.caution()).contains("R10", "R20"));

        LocalDate wednesday = ActivityViews.today().plusDays(2);
        while (wednesday.getDayOfWeek() != DayOfWeek.WEDNESDAY) wednesday = wednesday.plusDays(1);
        var iceOnWednesday = new ItineraryRequest(wednesday, LocalTime.of(9, 0), LocalTime.of(18, 0), 2, List.of(10), Residency.SA_RESIDENT, List.of(),
            Area.NORTHERN_SUBURBS, Transport.OWN_VEHICLE, null, null, 1, false, List.of("grandwest-ice-station"), null);
        assertThat(itineraries.plan(iceOnWednesday).skipped()).anySatisfy(s -> assertThat(s).contains("no verified opening hours or sessions for Wednesdays"));

        var cinema = new ItineraryRequest(wednesday, LocalTime.of(14, 0), LocalTime.of(20, 0), 1, List.of(10), Residency.SA_RESIDENT, List.of(),
            Area.CITY_CENTRE, Transport.OWN_VEHICLE, null, 100_000, 1, false, List.of("the-labia-theatre"), null);
        Itinerary plan = itineraries.plan(cinema);
        Stop labia = plan.stops().stream().filter(s -> "the-labia-theatre".equals(s.slug())).findFirst().orElseThrow();
        assertThat(labia.costs()).anySatisfy(l -> { assertThat(l.label()).contains("child"); assertThat(l.kind()).isEqualTo(CostKind.ESTIMATE); });
        assertThat(plan.verdict()).isEqualTo(Verdict.ESTIMATED_WITHIN_BUDGET);
        assertThat(plan.confidence().suitabilityConfirmed()).isFalse();
    }
}
