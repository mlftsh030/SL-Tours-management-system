package com.sltours.activities.service;
import com.sltours.activities.dto.ItineraryDtos.*;
import com.sltours.activities.model.*;
import com.sltours.activities.model.ActivityEnums.*;
import com.sltours.privatetrips.dto.PrivateTripDtos.VehicleCard;
import com.sltours.privatetrips.service.PrivateTripService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a one-day plan from the verified catalogue. Every cost is labelled: VERIFIED (published price effective on the date),
 * ASSUMPTION (a fixed price with an assumed quantity, or the visitor's own food allowance), ESTIMATE (e.g. a sample vehicle
 * rate, a price whose official sources conflict, or one not re-checked within its freshness period), UNKNOWN (no verified
 * price for that date) or EXCLUDED (not estimated, such as fuel). A plan is only said to fit the budget when nothing is
 * unknown, and only said to fit firmly when nothing is an estimate. Unconfirmed hours, sessions and participation limits
 * are reported as cautions and in {@link Confidence}, never as confirmed.
 */
@Service
public class ItineraryService {
    private final ActivityService activities;
    private final PlanningService planning;
    private final PrivateTripService privateTrips;
    private final CatalogueFreshness freshness;
    public ItineraryService(ActivityService activities, PlanningService planning, PrivateTripService privateTrips, CatalogueFreshness freshness) {
        this.activities = activities; this.planning = planning; this.privateTrips = privateTrips; this.freshness = freshness;
    }

    record Traveller(Integer age) { boolean adult() { return age == null; } }
    record Slot(LocalTime start, LocalTime end, String note, ActivityHours row) {}
    record StopCost(List<CostLine> lines, String note) {
        int known() { return lines.stream().filter(l -> l.amountCents() != null && l.kind() != CostKind.EXCLUDED).mapToInt(CostLine::amountCents).sum(); }
    }

    @Transactional(readOnly = true)
    public Itinerary plan(ItineraryRequest r) {
        LocalDate today = ActivityViews.today();
        if (r.date().isBefore(today)) throw new IllegalArgumentException("Choose today or a future date");
        if (r.date().isAfter(today.plusDays(366))) throw new IllegalArgumentException("Choose a date within the next year");
        if (!r.endTime().isAfter(r.startTime().plusHours(1))) throw new IllegalArgumentException("Allow at least an hour between start and end");
        if (r.residency() == Residency.ANY) throw new IllegalArgumentException("Choose where the travellers are resident");

        List<Traveller> travellers = new ArrayList<>();
        for (int i = 0; i < r.adults(); i++) travellers.add(new Traveller(null));
        r.childAges().forEach(age -> travellers.add(new Traveller(age)));
        Integer youngest = r.childAges().stream().min(Integer::compare).orElse(null);
        var settings = planning.settings();
        var areas = planning.areas();
        List<String> assumptions = new ArrayList<>(), exclusions = new ArrayList<>(), warnings = new ArrayList<>(), skipped = new ArrayList<>();
        assumptions.add(planning.assumption(settings));
        assumptions.add("Adults are charged adult prices; senior, student and other concessions are not applied automatically.");
        if (!r.childAges().isEmpty()) assumptions.add("Child prices use the children's ages you entered.");

        // Fixed costs reserved before choosing stops, so budget checks include them.
        List<CostLine> food = foodLines(r, travellers.size(), exclusions);
        List<CostLine> transport = transportLines(r, travellers.size(), assumptions, exclusions);
        int reserved = sumKnown(food) + sumKnown(transport);

        Set<String> must = new LinkedHashSet<>(r.mustInclude());
        List<Activity> candidates = new ArrayList<>();
        for (Activity a : activities.published()) {
            boolean required = must.contains(a.getSlug());
            // Plans cover Cape Town's planning areas only: distant listings are never treated as nearby.
            if (!ActivityViews.plannable(a)) {
                if (required) skipped.add(a.getTitle() + ": outside the Cape Town areas covered by day plans");
                continue;
            }
            if (!required && r.exclude().contains(a.getSlug())) continue;
            if (!required && !r.interests().isEmpty() && !r.interests().contains(a.getCategory())) continue;
            if (a.getMinAge() != null && youngest != null && youngest < a.getMinAge()) { skipped.add(a.getTitle() + ": minimum age " + a.getMinAge()); continue; }
            if (r.onlyPublishedPrices() && !required && a.getPriceStatus() != PriceStatus.VERIFIED && a.getPriceStatus() != PriceStatus.FREE) continue;
            candidates.add(a);
        }
        Set<String> published = activities.published().stream().map(Activity::getSlug).collect(Collectors.toSet());
        must.stream().filter(slug -> !published.contains(slug)).forEach(slug -> skipped.add(slug + ": not a published activity"));

        List<Stop> stops = new ArrayList<>();
        LocalTime time = r.startTime();
        Area current = r.startArea();
        boolean lunchDone = false;
        int spent = 0;
        while (stops.stream().filter(s -> s.type() == StopType.ACTIVITY).count() < r.maxStops() && !candidates.isEmpty()) {
            if (!lunchDone && !time.isBefore(LocalTime.of(11, 45)) && time.plusMinutes(settings.lunchMinutes()).isBefore(r.endTime())) {
                stops.add(lunchStop(stops.size() + 1, current, time, settings.lunchMinutes(), areas));
                time = time.plusMinutes(settings.lunchMinutes()); lunchDone = true;
            }
            Activity best = null; Slot bestSlot = null; int bestTravel = 0; StopCost bestCost = null; double bestScore = Double.MAX_VALUE;
            for (Activity a : candidates) {
                int travel = PlanningService.travelMinutes(areas.get(current), areas.get(a.getArea()), settings);
                LocalTime arrival = time.plusMinutes(travel);
                if (arrival.isBefore(time)) continue; // past midnight
                Optional<Slot> slot = slot(a, r.date(), arrival, r.endTime());
                if (slot.isEmpty()) continue;
                StopCost cost = cost(a, r, travellers, slot.get().start());
                if (r.budgetCents() != null && reserved + spent + cost.known() > r.budgetCents() && !must.contains(a.getSlug())) continue;
                long wait = ChronoUnit.MINUTES.between(arrival, slot.get().start());
                double score = travel * 2.0 + wait - (must.contains(a.getSlug()) ? 10_000 : 0) - (r.interests().contains(a.getCategory()) ? 15 : 0);
                if (score < bestScore) { best = a; bestSlot = slot.get(); bestTravel = travel; bestCost = cost; bestScore = score; }
            }
            if (best == null) break;
            List<String> notes = notes(best, bestSlot, bestCost);
            stops.add(new Stop(stops.size() + 1, StopType.ACTIVITY, best.getSlug(), best.getTitle(), best.getArea(), ActivityViews.areaLabel(best.getArea()),
                bestSlot.start(), bestSlot.end(), bestTravel, notes, bestCost.lines(), cautions(best, bestSlot, youngest != null)));
            spent += bestCost.known();
            time = bestSlot.end(); current = best.getArea();
            candidates.remove(best);
        }
        for (Activity a : candidates) if (must.contains(a.getSlug())) skipped.add(a.getTitle() + ": " + (hasNoHoursOn(a, r.date())
            ? "no verified opening hours or sessions for " + r.date().getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + "s"
            : "does not fit the times you chose"));
        if (stops.stream().noneMatch(s -> s.type() == StopType.ACTIVITY))
            warnings.add("No published activity fits these dates, times, ages and interests. Try a longer day, other interests or another date.");

        int back = PlanningService.travelMinutes(areas.get(current), areas.get(r.startArea()), settings);
        LocalTime home = time.plusMinutes(back);
        stops.add(new Stop(stops.size() + 1, StopType.RETURN, null, "Return to " + areas.get(r.startArea()).label(), r.startArea(),
            ActivityViews.areaLabel(r.startArea()), time, home, back, home.isAfter(r.endTime()) ? List.of("Arrives after your chosen end time") : List.of(), List.of(),
            List.of()));
        if (home.isAfter(r.endTime())) warnings.add("The return journey ends at " + home + ", after your chosen end time.");

        List<CostLine> all = new ArrayList<>(food); all.addAll(transport); stops.forEach(s -> all.addAll(s.costs()));
        Totals totals = totals(all);
        Confidence confidence = confidence(stops, all);
        Verdict verdict; String message;
        if (r.budgetCents() == null) { verdict = Verdict.NO_BUDGET; message = "No budget set. Known costs are shown below."; }
        else if (totals.unknownItems() > 0) { verdict = Verdict.CANNOT_CONFIRM;
            message = "We can't confirm this fits your budget: " + totals.unknownItems() + " cost(s) have no verified price for this date. Known costs come to " + rand(totals.knownTotalCents()) + "."; }
        else if (totals.knownTotalCents() > r.budgetCents()) { verdict = Verdict.OVER_BUDGET; message = "Over your budget by " + rand(totals.knownTotalCents() - r.budgetCents()) + "."; }
        else if (totals.estimatedCents() > 0) { verdict = Verdict.ESTIMATED_WITHIN_BUDGET;
            message = "Likely within budget, but part of the total is an estimate, so it is not a firm figure."; }
        else { verdict = Verdict.WITHIN_BUDGET;
            message = "Fits your budget using prices published by the operators for this date and the assumptions listed. Prices can change; check before you go."; }
        if (!exclusions.isEmpty() && r.budgetCents() != null) message += " Not included: " + String.join(", ", exclusions) + ".";
        if (!confidence.scheduleConfirmed()) message += " Some opening times or sessions are not confirmed; check them before you go.";
        if (!confidence.suitabilityConfirmed()) message += " Suitability is not confirmed for at least one stop: its participation limits are not published or not recently checked.";

        String who = r.adults() + (r.adults() == 1 ? " adult" : " adults") + (r.childAges().isEmpty() ? "" : ", children aged "
            + r.childAges().stream().map(String::valueOf).collect(Collectors.joining(", ")));
        return new Itinerary(r.date(), who, stops, transport, food, totals, r.budgetCents(), verdict, message, dedupe(assumptions), exclusions,
            dedupe(warnings), skipped, confidence);
    }

    /** Earliest feasible slot on the date at or after the arrival time, or empty when the activity cannot fit. */
    static Optional<Slot> slot(Activity a, LocalDate date, LocalTime arrival, LocalTime endOfDay) {
        int duration = a.getDurationMinutes();
        switch (a.getScheduleType()) {
            case OPENING_HOURS -> {
                return a.getHours().stream().filter(h -> h.appliesOn(date)).map(h -> {
                    LocalTime start = arrival.isBefore(h.getOpens()) ? h.getOpens() : arrival;
                    LocalTime latest = h.getCloses().minusMinutes(duration);
                    if (h.getLastEntry() != null && h.getLastEntry().isBefore(latest)) latest = h.getLastEntry();
                    LocalTime end = start.plusMinutes(duration);
                    return start.isAfter(latest) || end.isAfter(endOfDay) || end.isBefore(start) ? null : new Slot(start, end, null, h);
                }).filter(Objects::nonNull).min(Comparator.comparing(Slot::start));
            }
            case SESSIONS -> {
                return a.getHours().stream().filter(h -> h.appliesOn(date) && !h.getOpens().isBefore(arrival) && !h.getCloses().isAfter(endOfDay))
                    .map(h -> new Slot(h.getOpens(), h.getCloses(), "Session " + h.getOpens() + "-" + h.getCloses(), h))
                    .min(Comparator.comparing(Slot::start));
            }
            case SCHEDULE_VARIES, CHECK_OPERATOR -> {
                LocalTime end = arrival.plusMinutes(duration);
                return end.isAfter(endOfDay) || end.isBefore(arrival) ? Optional.empty()
                    : Optional.of(new Slot(arrival, end, a.getScheduleType() == ScheduleType.CHECK_OPERATOR
                        ? "Opening hours not published; confirm with the operator" : "Times change daily; check the operator's schedule", null));
            }
            default -> {
                LocalTime end = arrival.plusMinutes(duration);
                return end.isAfter(endOfDay) || end.isBefore(arrival) ? Optional.empty() : Optional.of(new Slot(arrival, end, null, null));
            }
        }
    }

    StopCost cost(Activity a, ItineraryRequest r, List<Traveller> travellers, LocalTime start) {
        List<CostLine> lines = new ArrayList<>();
        Map<UUID, ActivitySource> sources = a.getSources().stream().collect(Collectors.toMap(ActivitySource::getId, s -> s));
        String note = null;
        switch (a.getPriceStatus()) {
            case FREE -> lines.add(new CostLine("Free entry", travellers.size(), 0, 0, CostKind.VERIFIED, null, null, a.getLastVerifiedOn()));
            case NOT_RECORDED -> lines.add(new CostLine("Admission", travellers.size(), null, null, CostKind.UNKNOWN,
                "SL Tours has not recorded prices for this activity; check with the operator", null, null));
            case NOT_PUBLISHED -> lines.add(new CostLine("Admission", travellers.size(), null, null, CostKind.UNKNOWN,
                "Prices are not published online by the operator", null, null));
            case VARIES_AT_BOOKING -> lines.add(new CostLine("Admission", travellers.size(), null, null, CostKind.UNKNOWN,
                "Prices are set per booking by the operator", null, null));
            case VERIFIED -> {
                Map<ActivityPriceRule, Integer> counts = new LinkedHashMap<>();
                Map<String, Integer> unknown = new LinkedHashMap<>();
                boolean vehicleRule = a.getPriceRules().stream().anyMatch(p -> p.getUnit() == Unit.PER_VEHICLE && p.isEstimateDefault());
                if (vehicleRule) {
                    Optional<ActivityPriceRule> rule = choose(a, r.residency(), new Traveller(null), r.date(), start, Unit.PER_VEHICLE);
                    if (rule.isPresent()) counts.put(rule.get(), 1);
                    else unknown.put(unavailableReason(a, r.residency(), new Traveller(null), r.date(), start, Unit.PER_VEHICLE), 1);
                } else for (Traveller t : travellers) {
                    Optional<ActivityPriceRule> rule = choose(a, r.residency(), t, r.date(), start, null);
                    // Per-item purchases (e.g. a gemstone bag) are only assumed for travellers the default item is meant for.
                    if (rule.isPresent() && rule.get().getUnit() == Unit.PER_ITEM && !rule.get().isEstimateDefault()) {
                        note = "Assumes one per " + defaultItemAudience(a) + "; other travellers are not charged";
                        continue;
                    }
                    if (rule.isPresent()) counts.merge(rule.get(), 1, Integer::sum);
                    else unknown.merge(unavailableReason(a, r.residency(), t, r.date(), start, null) + (t.adult() ? " (adult)" : " (child aged " + t.age() + ")"), 1, Integer::sum);
                }
                LocalDate today = ActivityViews.today();
                counts.forEach((rule, qty) -> {
                    ActivitySource s = rule.getSourceId() == null ? null : sources.get(rule.getSourceId());
                    boolean assumed = rule.getUnit() == Unit.PER_ITEM || rule.getUnit() == Unit.PER_VEHICLE;
                    String detail = assumed ? (rule.getUnit() == Unit.PER_VEHICLE ? "Assumes one vehicle" : "Assumes one per person shown") : rule.getConditions();
                    // Conflicting or undefined official information, or a price past its re-check period, is only an estimate.
                    List<String> doubts = new ArrayList<>();
                    if (rule.getUncertaintyNote() != null) doubts.add("Not confirmed: " + rule.getUncertaintyNote());
                    freshness.price(rule, today).ifPresent(doubts::add);
                    CostKind kind = !doubts.isEmpty() ? CostKind.ESTIMATE : assumed ? CostKind.ASSUMPTION : CostKind.VERIFIED;
                    if (!doubts.isEmpty()) detail = join(detail, String.join(". ", doubts));
                    lines.add(new CostLine(rule.getLabel(), qty, rule.getAmountCents(), Math.multiplyExact(rule.getAmountCents(), qty),
                        kind, detail, s == null ? null : s.getUrl(), rule.getVerifiedOn()));
                });
                unknown.forEach((reason, qty) -> lines.add(new CostLine("Admission", qty, null, null, CostKind.UNKNOWN, reason, null, null)));
                if (!unknown.isEmpty()) note = "Some prices are not verified for this date";
            }
        }
        for (ActivityExtra x : a.getExtras()) {
            if (!x.effectiveOn(r.date())) continue;
            ActivitySource s = x.getSourceId() == null ? null : sources.get(x.getSourceId());
            if (!x.isMandatory()) continue;
            int qty = x.getUnit() == Unit.PER_PERSON ? travellers.size() : 1;
            List<String> doubts = new ArrayList<>();
            if (x.getUncertaintyNote() != null) doubts.add("Not confirmed: " + x.getUncertaintyNote());
            freshness.extra(x, ActivityViews.today()).ifPresent(doubts::add);
            CostKind kind = !doubts.isEmpty() ? CostKind.ESTIMATE : x.getUnit() == Unit.PER_PERSON ? CostKind.VERIFIED : CostKind.ASSUMPTION;
            lines.add(new CostLine(x.getLabel(), qty, x.getAmountCents(), Math.multiplyExact(x.getAmountCents(), qty), kind,
                doubts.isEmpty() ? x.getConditions() : join(x.getConditions(), String.join(". ", doubts)), s == null ? null : s.getUrl(), x.getVerifiedOn()));
        }
        return new StopCost(lines, note);
    }

    /**
     * The rule for a traveller: effective on the date, valid for the day and session time, matching age and residency.
     * Residency falls back to the international rate when no specific rate is published for SADC or African nationals.
     */
    static Optional<ActivityPriceRule> choose(Activity a, Residency residency, Traveller t, LocalDate date, LocalTime start, Unit onlyUnit) {
        List<ActivityPriceRule> fitting = fitting(a, t, date, start, onlyUnit).filter(p -> p.effectiveOn(date)).toList();
        for (Residency r : fallbacks(residency)) {
            List<ActivityPriceRule> forResidency = fitting.stream().filter(p -> p.getResidency() == r).toList();
            if (forResidency.isEmpty()) continue;
            List<ActivityPriceRule> preferred = forResidency.stream().filter(ActivityPriceRule::isEstimateDefault).toList();
            List<ActivityPriceRule> pool = preferred.isEmpty() ? forResidency : preferred;
            // Free rules for a specific day (e.g. a free weekday) apply only when they are the defaults or the only fit.
            return pool.stream().max(Comparator.comparingInt(ActivityPriceRule::getAmountCents));
        }
        return Optional.empty();
    }

    /** Rules that fit the traveller, day, session and unit, before effective dates are considered. */
    private static Stream<ActivityPriceRule> fitting(Activity a, Traveller t, LocalDate date, LocalTime start, Unit onlyUnit) {
        return a.getPriceRules().stream()
            .filter(p -> DayRules.matches(p.getDaysOfWeek(), date.getDayOfWeek()))
            .filter(p -> p.getTimeFrom() == null || (!start.isBefore(p.getTimeFrom()) && start.isBefore(p.getTimeTo())))
            .filter(p -> onlyUnit == null ? p.getUnit() == Unit.PER_PERSON || p.getUnit() == Unit.PER_ITEM : p.getUnit() == onlyUnit)
            .filter(p -> onlyUnit == Unit.PER_VEHICLE || audienceFits(p, t));
    }

    /**
     * Why no price applies. When a matching price has ended, its amount stays visible as the last published figure,
     * clearly not a price for the plan date.
     */
    static String unavailableReason(Activity a, Residency residency, Traveller t, LocalDate date, LocalTime start, Unit onlyUnit) {
        List<ActivityPriceRule> ended = fitting(a, t, date, start, onlyUnit)
            .filter(p -> p.getEffectiveTo() != null && p.getEffectiveTo().isBefore(date)).toList();
        for (Residency r : fallbacks(residency)) {
            Optional<ActivityPriceRule> last = ended.stream().filter(p -> p.getResidency() == r)
                .max(Comparator.comparing(ActivityPriceRule::getEffectiveTo).thenComparing(ActivityPriceRule::isEstimateDefault));
            if (last.isPresent()) return "Last published price " + rand(last.get().getAmountCents()) + " (" + last.get().getLabel() + ") ended on "
                + last.get().getEffectiveTo() + "; the price for " + date + " is not published or not verified yet";
        }
        return expiredReason(a, date);
    }

    static List<Residency> fallbacks(Residency residency) {
        return switch (residency) {
            case SA_RESIDENT -> List.of(Residency.SA_RESIDENT, Residency.ANY);
            case SADC -> List.of(Residency.SADC, Residency.INTERNATIONAL, Residency.ANY);
            case AFRICAN -> List.of(Residency.AFRICAN, Residency.INTERNATIONAL, Residency.ANY);
            default -> List.of(Residency.INTERNATIONAL, Residency.ANY);
        };
    }

    static boolean audienceFits(ActivityPriceRule p, Traveller t) {
        boolean ageFits = t.adult() ? (p.getMaxAge() == null || p.getMaxAge() >= 18)
            : (p.getMinAge() == null || t.age() >= p.getMinAge()) && (p.getMaxAge() == null || t.age() <= p.getMaxAge());
        return switch (p.getAudience()) {
            case EVERYONE -> ageFits;
            case ADULT -> t.adult() && ageFits;
            case CHILD, INFANT -> !t.adult() && ageFits;
            default -> false; // Seniors, students, families and groups need information the planner does not collect.
        };
    }

    private static String defaultItemAudience(Activity a) {
        return a.getPriceRules().stream().filter(p -> p.getUnit() == Unit.PER_ITEM && p.isEstimateDefault())
            .map(p -> p.getAudience() == Audience.CHILD ? "child" : p.getAudience() == Audience.ADULT ? "adult" : "person").findFirst().orElse("person");
    }

    static String expiredReason(Activity a, LocalDate date) {
        Optional<LocalDate> ended = a.getPriceRules().stream().map(ActivityPriceRule::getEffectiveTo).filter(Objects::nonNull)
            .filter(to -> to.isBefore(date)).max(Comparator.naturalOrder());
        return ended.map(to -> "Published prices end on " + to + "; prices for " + date + " are not verified yet")
            .orElse("No published price matches these travellers for this date");
    }

    /** What cannot be confirmed for a stop: doubtful or stale hours and sessions, and unpublished participation limits. */
    private List<String> cautions(Activity a, Slot slot, boolean children) {
        List<String> cautions = new ArrayList<>();
        LocalDate today = ActivityViews.today();
        if (slot.row() != null) {
            if (slot.row().getUncertaintyNote() != null) cautions.add("Times not confirmed: " + slot.row().getUncertaintyNote());
            freshness.hours(a, slot.row(), today).ifPresent(cautions::add);
        }
        if (a.getParticipationRules() == ParticipationRules.UNKNOWN)
            cautions.add("Participation limits (such as age or height) are not published" + (a.getParticipationNote() == null ? "" : ": " + a.getParticipationNote()));
        else if (a.getParticipationRules() == ParticipationRules.PARTLY_KNOWN && children)
            cautions.add("Participation limits are only partly published" + (a.getParticipationNote() == null ? "" : ": " + a.getParticipationNote()));
        else if (children || a.getMinAge() != null) freshness.participation(a, today).ifPresent(cautions::add);
        return cautions;
    }

    static Confidence confidence(List<Stop> stops, List<CostLine> costs) {
        List<String> reasons = new ArrayList<>();
        long unknown = costs.stream().filter(c -> c.kind() == CostKind.UNKNOWN).count();
        long estimates = costs.stream().filter(c -> c.kind() == CostKind.ESTIMATE).count();
        if (unknown > 0) reasons.add(unknown + " cost(s) have no verified price for this date");
        if (estimates > 0) reasons.add(estimates + " cost(s) are estimates (conflicting, assumed or not recently checked)");
        boolean schedule = true, suitability = true;
        for (Stop s : stops) for (String c : s.cautions()) {
            if (c.startsWith("Participation")) suitability = false; else schedule = false;
            reasons.add(s.title() + ": " + c);
        }
        return new Confidence(unknown == 0 && estimates == 0, schedule, suitability, reasons);
    }

    private static boolean hasNoHoursOn(Activity a, LocalDate date) {
        return (a.getScheduleType() == ScheduleType.OPENING_HOURS || a.getScheduleType() == ScheduleType.SESSIONS)
            && a.getHours().stream().noneMatch(h -> h.appliesOn(date));
    }
    private static String join(String first, String second) { return first == null || first.isBlank() ? second : first + ". " + second; }

    private List<String> notes(Activity a, Slot slot, StopCost cost) {
        List<String> notes = new ArrayList<>();
        if (slot.note() != null) notes.add(slot.note());
        if (a.isWeatherDependent()) notes.add("Weather dependent; have a back-up plan");
        if (a.getAccompanimentRule() != null) notes.add(a.getAccompanimentRule());
        if (a.getMinAge() != null) notes.add("Minimum age " + a.getMinAge() + " for the main activity");
        if (a.getBookingNotes() != null) notes.add(a.getBookingNotes());
        if (cost.note() != null) notes.add(cost.note());
        return notes;
    }

    private Stop lunchStop(int order, Area area, LocalTime start, int minutes, Map<Area, PlanningService.AreaPoint> areas) {
        return new Stop(order, StopType.LUNCH, null, "Lunch break", area, ActivityViews.areaLabel(area), start, start.plusMinutes(minutes), 0,
            List.of("Choose a place near " + areas.get(area).label()), List.of(), List.of());
    }

    private List<CostLine> foodLines(ItineraryRequest r, int people, List<String> exclusions) {
        if (r.foodPerPersonCents() == null) { exclusions.add("food and drinks"); return List.of(); }
        return List.of(new CostLine("Food allowance (your amount)", people, r.foodPerPersonCents(), Math.multiplyExact(r.foodPerPersonCents(), people),
            CostKind.ASSUMPTION, "Uses the amount per person you entered", null, null));
    }

    private List<CostLine> transportLines(ItineraryRequest r, int people, List<String> assumptions, List<String> exclusions) {
        if (r.transport() == Transport.OWN_VEHICLE) {
            exclusions.add("fuel and parking for your own vehicle");
            return List.of(new CostLine("Own vehicle: fuel and parking", 1, null, null, CostKind.EXCLUDED, "Not estimated", null, null));
        }
        // Private vehicle with driver for the whole day window, priced by the private-trip rules and checked for availability.
        long minutes = ChronoUnit.MINUTES.between(r.startTime(), r.endTime());
        int duration = (int) Math.max(60, Math.min(1440, Math.ceil(minutes / 30.0) * 30));
        assumptions.add("SL Tours vehicle and driver hired for the whole day window (" + duration / 60.0 + " hours).");
        try {
            var search = privateTrips.search(r.date(), r.startTime(), duration, people);
            Optional<VehicleCard> priced = search.vehicles().stream().filter(v -> v.quote() != null)
                .min(Comparator.comparingInt(v -> v.quote().totalCents()));
            if (priced.isEmpty()) return List.of(new CostLine("SL Tours vehicle and driver", 1, null, null, CostKind.UNKNOWN,
                search.vehicles().isEmpty() ? "No listed vehicle is available for this group and time" : "Priced on request", null, null));
            var quote = priced.get().quote();
            return List.of(new CostLine("SL Tours vehicle and driver (" + priced.get().name() + ")", 1, quote.totalCents(), quote.totalCents(),
                quote.sampleRate() ? CostKind.ESTIMATE : CostKind.VERIFIED,
                quote.sampleRate() ? "Sample rate in this development environment, not a live price" : "Current private-trip price; confirmed when you book",
                null, null));
        } catch (RuntimeException e) {
            return List.of(new CostLine("SL Tours vehicle and driver", 1, null, null, CostKind.UNKNOWN, e.getMessage(), null, null));
        }
    }

    static Totals totals(List<CostLine> lines) {
        int verified = 0, assumption = 0, estimate = 0, unknown = 0;
        for (CostLine l : lines) switch (l.kind()) {
            case VERIFIED -> verified += l.amountCents();
            case ASSUMPTION -> assumption += l.amountCents();
            case ESTIMATE -> estimate += l.amountCents();
            case UNKNOWN -> unknown++;
            default -> { }
        }
        return new Totals(verified, assumption, estimate, verified + assumption + estimate, unknown);
    }
    private static int sumKnown(List<CostLine> lines) { return totals(lines).knownTotalCents(); }
    private static List<String> dedupe(List<String> items) { return new ArrayList<>(new LinkedHashSet<>(items)); }
    private static String rand(int cents) { return String.format(Locale.ROOT, "R%,.2f", cents / 100.0).replace(',', ' '); }
}
