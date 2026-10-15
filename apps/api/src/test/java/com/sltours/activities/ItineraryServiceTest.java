package com.sltours.activities;
import com.sltours.activities.dto.ItineraryDtos.*;
import com.sltours.activities.model.*;
import com.sltours.activities.model.ActivityEnums.*;
import com.sltours.activities.service.*;
import com.sltours.privatetrips.dto.PrivateTripDtos.*;
import com.sltours.privatetrips.service.PrivateTripService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ItineraryServiceTest {
    private final ActivityService activities = mock(ActivityService.class);
    private final PlanningService planning = mock(PlanningService.class);
    private final PrivateTripService privateTrips = mock(PrivateTripService.class);
    private final ItineraryService service = new ItineraryService(activities, planning, privateTrips, CatalogueFreshness.defaults());
    private final LocalDate date = nextWeekday();

    ItineraryServiceTest() {
        when(planning.settings()).thenReturn(new PlanningService.Settings(0, new BigDecimal("1.40"), 40, 10, 60, "test"));
        Map<Area, PlanningService.AreaPoint> areas = new EnumMap<>(Area.class);
        double[][] coords = {{-33.925, 18.423}, {-33.905, 18.420}, {-33.948, 18.403}, {-33.95, 18.38}, {-33.955, 18.47}, {-34.027, 18.425},
            {-34.06, 18.36}, {-34.15, 18.45}, {-34.357, 18.497}, {-33.90, 18.53}};
        for (Area area : Area.values()) areas.put(area, new PlanningService.AreaPoint(area, area.name(), coords[area.ordinal()][0], coords[area.ordinal()][1]));
        when(planning.areas()).thenReturn(areas);
        when(planning.assumption(any())).thenReturn("Travel times are planning estimates");
    }
    private static LocalDate nextWeekday() {
        LocalDate d = ActivityViews.today().plusDays(7);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }
    private ItineraryRequest request(List<Integer> children, Residency residency, Integer budget, Transport transport, int maxStops, String... must) {
        return new ItineraryRequest(date, LocalTime.of(9, 0), LocalTime.of(17, 0), 2, children, residency, List.of(), Area.CITY_CENTRE,
            transport, null, budget, maxStops, false, List.of(must), null);
    }

    @Test void pricesEachTravellerByAgeAndResidencyAndOnlyClaimsTheBudgetWhenEverythingIsVerified() {
        Activity aquarium = new ActivityBuilder("aquarium", Category.WILDLIFE, Area.WATERFRONT, 120, ScheduleType.OPENING_HOURS)
            .price("Adult", Audience.ADULT, 18, null, Residency.ANY, 28000, true)
            .price("Child 4-13", Audience.CHILD, 4, 13, Residency.ANY, 13000, true)
            .price("Under 4", Audience.INFANT, 0, 3, Residency.ANY, 0, true)
            .hours("09:30", "18:00", "17:30", null, null, null).build();
        when(activities.published()).thenReturn(List.of(aquarium));
        Itinerary plan = service.plan(request(List.of(2, 9), Residency.INTERNATIONAL, 100_000, Transport.OWN_VEHICLE, 1));
        Stop stop = plan.stops().stream().filter(s -> s.type() == StopType.ACTIVITY).findFirst().orElseThrow();
        assertThat(stop.start()).isEqualTo(LocalTime.of(9, 30)); // waits for opening
        assertThat(stop.costs()).extracting(CostLine::label, CostLine::quantity, CostLine::amountCents)
            .containsExactlyInAnyOrder(tuple("Adult", 2, 56000), tuple("Child 4-13", 1, 13000), tuple("Under 4", 1, 0));
        assertThat(plan.totals().knownTotalCents()).isEqualTo(69000);
        assertThat(plan.verdict()).isEqualTo(Verdict.WITHIN_BUDGET);
        assertThat(plan.verdictMessage()).contains("Not included:", "fuel and parking", "food and drinks");
        assertThat(service.plan(request(List.of(2, 9), Residency.INTERNATIONAL, 50_000, Transport.OWN_VEHICLE, 1, "aquarium")).verdict())
            .isEqualTo(Verdict.OVER_BUDGET);
    }

    @Test void expiredPricesBecomeUnknownSoTheBudgetCannotBeConfirmed() {
        Activity park = new ActivityBuilder("park", Category.NATURE, Area.CAPE_POINT, 120, ScheduleType.OPENING_HOURS)
            .price("International adult", Audience.ADULT, 12, null, Residency.INTERNATIONAL, Unit.PER_PERSON, 51500, true, null, null, null,
                date.minusYears(1), date.minusDays(1))
            .hours("06:00", "18:00", null, null, null, null).build();
        when(activities.published()).thenReturn(List.of(park));
        Itinerary plan = service.plan(request(List.of(), Residency.INTERNATIONAL, 10_000_000, Transport.OWN_VEHICLE, 1));
        CostLine admission = plan.stops().get(0).costs().get(0);
        assertThat(admission.kind()).isEqualTo(CostKind.UNKNOWN);
        // The ended figure stays visible, but only as the last published price, never as a cost for the plan date.
        assertThat(admission.amountCents()).isNull();
        assertThat(admission.detail()).contains("Last published price R515.00 (International adult) ended on " + date.minusDays(1), "not verified yet");
        assertThat(plan.confidence().pricesConfirmed()).isFalse();
        assertThat(plan.verdict()).isEqualTo(Verdict.CANNOT_CONFIRM);
    }

    @Test void sessionsDaysAndPerItemPurchasesAreHonoured() {
        Activity rink = new ActivityBuilder("rink", Category.ICE_SKATING, Area.NORTHERN_SUBURBS, 90, ScheduleType.SESSIONS)
            .price("Morning", Audience.EVERYONE, null, null, Residency.ANY, Unit.PER_PERSON, 7000, true, "MON,TUE,WED,THU,FRI", "10:00", "12:15", null, null)
            .price("Afternoon", Audience.EVERYONE, null, null, Residency.ANY, Unit.PER_PERSON, 8500, true, "MON,TUE,WED,THU,FRI", "14:00", "16:00", null, null)
            .price("Weekend", Audience.EVERYONE, null, null, Residency.ANY, Unit.PER_PERSON, 7500, true, "SAT,SUN", "10:00", "13:30", null, null)
            .hours("10:00", "11:00", null, "MON,TUE,WED,THU,FRI", null, null)
            .hours("14:00", "16:00", null, "MON,TUE,WED,THU,FRI", null, null).build();
        Activity gems = new ActivityBuilder("gems", Category.FAMILY_LEISURE, Area.WATERFRONT, 60, ScheduleType.OPENING_HOURS)
            .price("Small bag", Audience.CHILD, null, null, Residency.ANY, Unit.PER_ITEM, 4500, true, null, null, null, null, null)
            .price("Large bag", Audience.EVERYONE, null, null, Residency.ANY, Unit.PER_ITEM, 19500, false, null, null, null, null, null)
            .hours("09:00", "16:45", null, null, null, null).build();
        when(activities.published()).thenReturn(List.of(rink, gems));
        var r = new ItineraryRequest(date, LocalTime.of(10, 30), LocalTime.of(17, 0), 2, List.of(8), Residency.SA_RESIDENT, List.of(),
            Area.WATERFRONT, Transport.OWN_VEHICLE, null, null, 2, false, List.of(), null);
        Itinerary plan = service.plan(r);
        Stop gemStop = plan.stops().stream().filter(s -> "gems".equals(s.slug())).findFirst().orElseThrow();
        assertThat(gemStop.costs()).singleElement().satisfies(l -> {
            assertThat(l.quantity()).isEqualTo(1); assertThat(l.amountCents()).isEqualTo(4500); assertThat(l.kind()).isEqualTo(CostKind.ASSUMPTION); });
        Stop rinkStop = plan.stops().stream().filter(s -> "rink".equals(s.slug())).findFirst().orElseThrow();
        // Arrives after the morning session has started, so it takes the 14:00 session at the afternoon price.
        assertThat(rinkStop.start()).isEqualTo(LocalTime.of(14, 0));
        assertThat(rinkStop.costs()).singleElement().satisfies(l -> assertThat(l.amountCents()).isEqualTo(3 * 8500));
    }

    @Test void routesGeographicallyRespectsLastEntrySeasonsAndMinimumAge() {
        Activity near = new ActivityBuilder("near", Category.NATURE, Area.WATERFRONT, 60, ScheduleType.OPENING_HOURS)
            .price("All", Audience.EVERYONE, null, null, Residency.ANY, 1000, true).hours("08:00", "18:00", null, null, null, null).build();
        Activity far = new ActivityBuilder("far", Category.NATURE, Area.CAPE_POINT, 60, ScheduleType.OPENING_HOURS)
            .price("All", Audience.EVERYONE, null, null, Residency.ANY, 1000, true).hours("08:00", "18:00", null, null, null, null).build();
        Activity closedSeason = new ActivityBuilder("closed-season", Category.NATURE, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS)
            .price("All", Audience.EVERYONE, null, null, Residency.ANY, 1000, true)
            .hours("08:00", "18:00", null, null, MonthDay.from(date.plusMonths(2)).toString().substring(2), MonthDay.from(date.plusMonths(3)).toString().substring(2)).build();
        Activity lateEntry = new ActivityBuilder("late-entry", Category.NATURE, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS)
            .price("All", Audience.EVERYONE, null, null, Residency.ANY, 1000, true).hours("08:00", "18:00", "08:30", null, null, null).build();
        Activity wine = new ActivityBuilder("wine", Category.WINE, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS).minAge(18)
            .price("All", Audience.EVERYONE, null, null, Residency.ANY, 1000, true).hours("08:00", "18:00", null, null, null, null).build();
        when(activities.published()).thenReturn(List.of(far, near, closedSeason, lateEntry, wine));
        var r = new ItineraryRequest(date, LocalTime.of(9, 0), LocalTime.of(17, 0), 1, List.of(10), Residency.SA_RESIDENT, List.of(),
            Area.CITY_CENTRE, Transport.OWN_VEHICLE, null, null, 2, false, List.of(), null);
        Itinerary plan = service.plan(r);
        List<String> order = plan.stops().stream().filter(s -> s.type() == StopType.ACTIVITY).map(Stop::slug).toList();
        assertThat(order).first().isEqualTo("near");
        assertThat(order).doesNotContain("closed-season", "late-entry", "wine");
        assertThat(plan.skipped()).anySatisfy(s -> assertThat(s).contains("minimum age 18"));
    }

    @Test void unpublishedPricesAndSampleVehicleRatesAreNeverPresentedAsFirmFigures() {
        Activity karting = new ActivityBuilder("karting", Category.GO_KARTING, Area.NORTHERN_SUBURBS, 60, ScheduleType.OPENING_HOURS)
            .status(PriceStatus.NOT_PUBLISHED).hours("09:00", "21:00", null, null, null, null).build();
        Activity museum = new ActivityBuilder("museum", Category.MUSEUM_HISTORY, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS)
            .price("All", Audience.EVERYONE, null, null, Residency.ANY, 5000, true).hours("09:00", "16:00", null, null, null, null).build();
        when(activities.published()).thenReturn(List.of(karting, museum));
        Itinerary withKarting = service.plan(request(List.of(), Residency.SA_RESIDENT, 1_000_000, Transport.OWN_VEHICLE, 2, "karting"));
        assertThat(withKarting.stops()).anySatisfy(s -> assertThat(s.costs()).anySatisfy(l -> assertThat(l.kind()).isEqualTo(CostKind.UNKNOWN)));
        assertThat(withKarting.verdict()).isEqualTo(Verdict.CANNOT_CONFIRM);
        var onlyPriced = new ItineraryRequest(date, LocalTime.of(9, 0), LocalTime.of(17, 0), 2, List.of(), Residency.SA_RESIDENT, List.of(),
            Area.CITY_CENTRE, Transport.SL_TOURS_PRIVATE_VEHICLE, 15000, 1_000_000, 2, true, List.of(), null);
        var quote = new PriceQuote(65000, com.sltours.privatetrips.model.PrivateTripDetails.RateSource.CATEGORY, true, 480, 180, 480, 60, false, false,
            520000, List.of(), 0, 520000);
        when(privateTrips.search(eq(date), any(), eq(480), eq(2))).thenReturn(new SearchResult(null, null, 480, 2, List.of(new VehicleCard(UUID.randomUUID(),
            "Van", com.sltours.fleet.model.VehicleCategory.VAN, 7, null, List.of(), null, null, "Van", 65000, 180, true, quote, null, null, false, null)), null));
        Itinerary plan = service.plan(onlyPriced);
        assertThat(plan.stops()).noneMatch(s -> "karting".equals(s.slug()));
        assertThat(plan.transport()).singleElement().satisfies(l -> assertThat(l.kind()).isEqualTo(CostKind.ESTIMATE));
        assertThat(plan.food()).singleElement().satisfies(l -> assertThat(l.amountCents()).isEqualTo(30000));
        assertThat(plan.verdict()).isEqualTo(Verdict.ESTIMATED_WITHIN_BUDGET);
    }

    @Test void budgetSkipsStopsThatWouldExceedIt() {
        Activity cheap = new ActivityBuilder("cheap", Category.NATURE, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS)
            .price("All", Audience.EVERYONE, null, null, Residency.ANY, 1000, true).hours("08:00", "18:00", null, null, null, null).build();
        Activity pricey = new ActivityBuilder("pricey", Category.NATURE, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS)
            .price("All", Audience.EVERYONE, null, null, Residency.ANY, 90000, true).hours("08:00", "18:00", null, null, null, null).build();
        when(activities.published()).thenReturn(List.of(pricey, cheap));
        Itinerary plan = service.plan(request(List.of(), Residency.SA_RESIDENT, 50_000, Transport.OWN_VEHICLE, 2));
        assertThat(plan.stops()).extracting(Stop::slug).contains("cheap").doesNotContain("pricey");
        assertThat(plan.verdict()).isEqualTo(Verdict.WITHIN_BUDGET);
    }

    private Stop only(Itinerary plan, String slug) {
        return plan.stops().stream().filter(s -> slug.equals(s.slug())).findFirst().orElseThrow();
    }

    @Test void conflictingStaleAndPromotionalPricesAreEstimatesNotFirmBudgetClaims() {
        LocalDate today = ActivityViews.today();
        Activity conflicting = new ActivityBuilder("conflicting", Category.CINEMA, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS)
            .price("Ticket", Audience.EVERYONE, null, null, Residency.ANY, 9000, true).lastPriceQualifiers("Two official pages disagree", false)
            .hours("09:00", "18:00", null, null, null, null).build();
        when(activities.published()).thenReturn(List.of(conflicting));
        Itinerary plan = service.plan(request(List.of(), Residency.SA_RESIDENT, 1_000_000, Transport.OWN_VEHICLE, 1));
        assertThat(only(plan, "conflicting").costs()).singleElement().satisfies(l -> {
            assertThat(l.kind()).isEqualTo(CostKind.ESTIMATE); assertThat(l.amountCents()).isEqualTo(18000);
            assertThat(l.detail()).contains("Not confirmed: Two official pages disagree"); });
        assertThat(plan.verdict()).isEqualTo(Verdict.ESTIMATED_WITHIN_BUDGET);
        assertThat(plan.confidence().pricesConfirmed()).isFalse();

        // A standard price checked 20 days ago is fine; a promotion checked 20 days ago, or a standard price 200 days old, is not.
        Activity recent = new ActivityBuilder("recent", Category.NATURE, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS).checkedOn(today.minusDays(20))
            .price("Standard", Audience.EVERYONE, null, null, Residency.ANY, 1000, true).hours("09:00", "18:00", null, null, null, null).build();
        Activity promo = new ActivityBuilder("promo", Category.NATURE, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS).checkedOn(today.minusDays(20))
            .price("Spring special", Audience.EVERYONE, null, null, Residency.ANY, Unit.PER_PERSON, 1000, true, null, null, null, null, date.plusDays(30))
            .lastPriceQualifiers(null, true).hours("09:00", "18:00", null, null, null, null).build();
        Activity old = new ActivityBuilder("old", Category.NATURE, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS).checkedOn(today.minusDays(200))
            .price("Standard", Audience.EVERYONE, null, null, Residency.ANY, 1000, true).checkedOn(today).hours("09:00", "18:00", null, null, null, null).build();
        when(activities.published()).thenReturn(List.of(recent, promo, old));
        Itinerary mixed = service.plan(request(List.of(), Residency.SA_RESIDENT, null, Transport.OWN_VEHICLE, 3, "recent", "promo", "old"));
        assertThat(only(mixed, "recent").costs()).singleElement().extracting(CostLine::kind).isEqualTo(CostKind.VERIFIED);
        assertThat(only(mixed, "promo").costs()).singleElement().satisfies(l -> {
            assertThat(l.kind()).isEqualTo(CostKind.ESTIMATE); assertThat(l.detail()).contains("Promotional price last checked", "every 14 days"); });
        assertThat(only(mixed, "old").costs()).singleElement().satisfies(l -> {
            assertThat(l.kind()).isEqualTo(CostKind.ESTIMATE); assertThat(l.detail()).contains("every 180 days"); });
    }

    @Test void sampledSessionsApplyOnlyToCheckedDaysAndStaleOrDoubtfulTimesAreCautions() {
        LocalDate today = ActivityViews.today();
        String day = date.getDayOfWeek().name().substring(0, 3);
        String otherDay = date.plusDays(1).getDayOfWeek().name().substring(0, 3);
        Activity rink = new ActivityBuilder("rink", Category.ICE_SKATING, Area.NORTHERN_SUBURBS, 90, ScheduleType.SESSIONS)
            .price("Session", Audience.EVERYONE, null, null, Residency.ANY, Unit.PER_PERSON, 7000, true, otherDay, null, null, null, null)
            .hours("10:00", "11:00", null, otherDay, null, null).build();
        when(activities.published()).thenReturn(List.of(rink));
        Itinerary unchecked = service.plan(request(List.of(), Residency.SA_RESIDENT, null, Transport.OWN_VEHICLE, 1, "rink"));
        assertThat(unchecked.stops()).noneMatch(s -> "rink".equals(s.slug()));
        assertThat(unchecked.skipped()).anySatisfy(s -> assertThat(s).contains("no verified opening hours or sessions for"));

        // Sessions observed only for a window do not apply after it.
        Activity window = new ActivityBuilder("window", Category.ICE_SKATING, Area.NORTHERN_SUBURBS, 90, ScheduleType.SESSIONS)
            .price("Session", Audience.EVERYONE, null, null, Residency.ANY, 7000, true)
            .hours("10:00", "11:00", null, null, null, null).lastHoursVerification(null, today, date.minusDays(1)).build();
        when(activities.published()).thenReturn(List.of(window));
        assertThat(service.plan(request(List.of(), Residency.SA_RESIDENT, null, Transport.OWN_VEHICLE, 1, "window")).stops())
            .noneMatch(s -> "window".equals(s.slug()));

        // Session times checked 40 days ago are past the 30-day period; doubtful opening hours are flagged too.
        Activity stale = new ActivityBuilder("stale", Category.ICE_SKATING, Area.NORTHERN_SUBURBS, 90, ScheduleType.SESSIONS)
            .price("Session", Audience.EVERYONE, null, null, Residency.ANY, 7000, true).checkedOn(today.minusDays(40))
            .hours("14:00", "15:30", null, day, null, null).build();
        Activity castle = new ActivityBuilder("castle", Category.MUSEUM_HISTORY, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS)
            .price("Adult", Audience.EVERYONE, null, null, Residency.ANY, 5000, true).hours("09:00", "16:00", "15:15", null, null, null)
            .lastHoursVerification("Last entry given as 15:15 and 15:45", null, null).build();
        when(activities.published()).thenReturn(List.of(stale, castle));
        Itinerary plan = service.plan(request(List.of(), Residency.SA_RESIDENT, 1_000_000, Transport.OWN_VEHICLE, 2, "stale", "castle"));
        assertThat(only(plan, "stale").cautions()).anySatisfy(c -> assertThat(c).contains("Session times last checked", "every 30 days"));
        assertThat(only(plan, "castle").cautions()).anySatisfy(c -> assertThat(c).contains("Times not confirmed: Last entry given as 15:15 and 15:45"));
        assertThat(plan.confidence().scheduleConfirmed()).isFalse();
        assertThat(plan.confidence().pricesConfirmed()).isTrue();
        assertThat(plan.verdict()).isEqualTo(Verdict.WITHIN_BUDGET);
        assertThat(plan.verdictMessage()).contains("opening times or sessions are not confirmed");
    }

    @Test void unpublishedParticipationLimitsBlockSuitabilityClaims() {
        Activity karting = new ActivityBuilder("karting", Category.GO_KARTING, Area.NORTHERN_SUBURBS, 60, ScheduleType.OPENING_HOURS)
            .status(PriceStatus.NOT_PUBLISHED).participation(ParticipationRules.UNKNOWN, "Age and height limits are not published")
            .hours("09:00", "21:00", null, null, null, null).build();
        Activity labia = new ActivityBuilder("labia", Category.CINEMA, Area.CITY_CENTRE, 120, ScheduleType.SCHEDULE_VARIES)
            .participation(ParticipationRules.PARTLY_KNOWN, "Depends on each film's classification")
            .price("Ticket", Audience.EVERYONE, null, null, Residency.ANY, 9000, true).build();
        when(activities.published()).thenReturn(List.of(karting, labia));
        Itinerary family = service.plan(request(List.of(6), Residency.SA_RESIDENT, null, Transport.OWN_VEHICLE, 2, "karting", "labia"));
        assertThat(only(family, "karting").cautions()).anySatisfy(c -> assertThat(c).contains("not published: Age and height limits"));
        assertThat(only(family, "labia").cautions()).anySatisfy(c -> assertThat(c).contains("only partly published"));
        assertThat(family.confidence().suitabilityConfirmed()).isFalse();
        assertThat(family.verdictMessage()).contains("Suitability is not confirmed");

        when(activities.published()).thenReturn(List.of(labia));
        Itinerary adults = service.plan(request(List.of(), Residency.SA_RESIDENT, null, Transport.OWN_VEHICLE, 1, "labia"));
        assertThat(adults.confidence().suitabilityConfirmed()).isTrue();
    }

    @Test void excludedActivitiesAreLeftOutUnlessRequired() {
        Activity a = new ActivityBuilder("a", Category.NATURE, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS)
            .price("All", Audience.EVERYONE, null, null, Residency.ANY, 1000, true).hours("08:00", "18:00", null, null, null, null).build();
        Activity b = new ActivityBuilder("b", Category.NATURE, Area.CITY_CENTRE, 60, ScheduleType.OPENING_HOURS)
            .price("All", Audience.EVERYONE, null, null, Residency.ANY, 1000, true).hours("08:00", "18:00", null, null, null, null).build();
        when(activities.published()).thenReturn(List.of(a, b));
        var r = new ItineraryRequest(date, LocalTime.of(9, 0), LocalTime.of(17, 0), 2, List.of(), Residency.SA_RESIDENT, List.of(), Area.CITY_CENTRE,
            Transport.OWN_VEHICLE, null, null, 2, false, List.of(), List.of("a"));
        assertThat(service.plan(r).stops()).extracting(Stop::slug).contains("b").doesNotContain("a");
    }
}
