package com.sltours.activities.service;
import com.sltours.activities.dto.ActivityDtos.*;
import com.sltours.activities.model.*;
import com.sltours.activities.model.ActivityEnums.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import org.springframework.stereotype.Component;

/** Maps activities to public and admin views and works out what still blocks publication. */
@Component
public class ActivityViews {
    private final CatalogueFreshness freshness;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public ActivityViews(CatalogueFreshness freshness, org.springframework.jdbc.core.JdbcTemplate jdbc) { this.freshness = freshness; this.jdbc = jdbc; }

    /** The supplier partner's business name, for the disclosure on public pages. */
    public String supplierName(Activity a) {
        if (a.getSupplierPartnerId() == null) return null;
        return jdbc.query("select business_name from partners where id = ?", (rs, i) -> rs.getString(1), a.getSupplierPartnerId()).stream().findFirst().orElse(null);
    }
    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    /** A verification older than this is flagged as stale and blocks publishing. */
    public static final int VERIFICATION_MAX_AGE_DAYS = 180;
    private static final Map<Area, String> AREA_LABELS = Map.of(
        Area.CITY_CENTRE, "City centre", Area.WATERFRONT, "V&A Waterfront", Area.TABLE_MOUNTAIN, "Table Mountain",
        Area.ATLANTIC_SEABOARD, "Atlantic Seaboard", Area.SOUTHERN_SUBURBS, "Southern Suburbs", Area.CONSTANTIA, "Constantia",
        Area.HOUT_BAY_CHAPMANS, "Hout Bay and Chapman's Peak", Area.FALSE_BAY, "False Bay", Area.CAPE_POINT, "Cape Point",
        Area.NORTHERN_SUBURBS, "Northern Suburbs");
    private static final Map<Region, String> REGION_LABELS = Map.of(Region.CAPE_TOWN, "Cape Town", Region.CAPE_WINELANDS, "Cape Winelands",
        Region.OVERBERG, "Overberg", Region.WEST_COAST, "West Coast", Region.GARDEN_ROUTE_KLEIN_KAROO, "Garden Route & Klein Karoo",
        Region.CAPE_KAROO, "Cape Karoo");
    public static String areaLabel(Area area) { return area == null ? null : AREA_LABELS.get(area); }
    public static String regionLabel(Region region) { return REGION_LABELS.get(region); }
    /** Day plans use Cape Town planning areas, so only Cape Town listings with an area can be planned. */
    public static boolean plannable(Activity a) { return a.getRegion() == Region.CAPE_TOWN && a.getArea() != null; }
    public static LocalDate today() { return LocalDate.now(ZONE); }

    public boolean stale(Activity a) {
        return a.getLastVerifiedOn() == null || a.getLastVerifiedOn().isBefore(today().minusDays(VERIFICATION_MAX_AGE_DAYS));
    }

    public List<String> publishProblems(Activity a) {
        List<String> problems = new ArrayList<>();
        if (a.getSources().stream().noneMatch(s -> s.getSourceType() != SourceType.OTHER))
            problems.add("Add at least one official source (operator, public authority or official tourism body)");
        if (a.publicImages().isEmpty()) problems.add("Add at least one picture with confirmed permission to use it");
        if (a.getArea() != null && a.getRegion() != Region.CAPE_TOWN) problems.add("Planning areas apply only to Cape Town listings");
        if (a.getLastVerifiedOn() == null) problems.add("Mark the activity as verified after checking the sources");
        else if (stale(a)) problems.add("Verification is older than " + VERIFICATION_MAX_AGE_DAYS + " days; re-check the sources");
        if (a.getAddress() == null || a.getAddress().isBlank()) problems.add("Add the address or meeting point");
        if ((a.getScheduleType() == ScheduleType.OPENING_HOURS || a.getScheduleType() == ScheduleType.SESSIONS) && a.getHours().isEmpty())
            problems.add("Add opening hours or sessions");
        if (a.getPriceStatus() == PriceStatus.VERIFIED && a.getPriceRules().isEmpty()) problems.add("Add the verified price rules, or change the price status");
        if (a.getPriceStatus() != PriceStatus.VERIFIED && !a.getPriceRules().isEmpty() && a.getPriceStatus() != PriceStatus.FREE)
            problems.add("Price rules exist but the price status is not Verified");
        Set<UUID> sourceIds = a.getSources().stream().map(ActivitySource::getId).collect(Collectors.toSet());
        for (ActivityPriceRule p : a.getPriceRules())
            if (p.getSourceId() == null || !sourceIds.contains(p.getSourceId())) problems.add("Price \"" + p.getLabel() + "\" needs a source");
        return problems;
    }

    public ActivityCard card(Activity a) {
        return new ActivityCard(a.getSlug(), a.getTitle(), a.getCategory(), List.copyOf(a.getThemes()), a.getRegion(), regionLabel(a.getRegion()),
            a.getTown(), a.getArea(), areaLabel(a.getArea()), a.getSummary(), a.getDurationMinutes(), a.getKind(), a.getEventSchedule(),
            a.publicImages().stream().findFirst().map(ActivityViews::publicMedia).orElse(null), a.isIndoor(), a.isWeatherDependent(),
            a.getOperatingStatus(), a.getLastVerifiedOn());
    }

    /** Prices for planning tools. "From" uses standard admission (the rules marked as the estimate default), so add-ons such as a
     *  spectator fee are never shown as the starting price; activities without defaults fall back to all adult prices. */
    public ActivityPricing pricing(Activity a, LocalDate on) {
        List<ActivityPriceRule> adult = a.getPriceStatus() != PriceStatus.VERIFIED ? List.of() : a.getPriceRules().stream()
            .filter(p -> p.effectiveOn(on) && p.getUnit() == Unit.PER_PERSON && p.getAmountCents() > 0
                && (p.getAudience() == Audience.ADULT || p.getAudience() == Audience.EVERYONE)).toList();
        List<ActivityPriceRule> standard = adult.stream().filter(ActivityPriceRule::isEstimateDefault).toList();
        Optional<ActivityPriceRule> from = (standard.isEmpty() ? adult : standard).stream().min(Comparator.comparingInt(ActivityPriceRule::getAmountCents));
        Map<UUID, ActivitySource> sources = a.getSources().stream().collect(Collectors.toMap(ActivitySource::getId, s -> s));
        // Current and future prices; expired prices are history, not something to plan with.
        var prices = a.getPriceRules().stream().filter(p -> p.getEffectiveTo() == null || !p.getEffectiveTo().isBefore(on)).map(p -> {
            ActivitySource s = p.getSourceId() == null ? null : sources.get(p.getSourceId());
            return new PublicPrice(p.getLabel(), p.getAudience(), p.getMinAge(), p.getMaxAge(), p.getResidency(), p.getUnit(), p.getAmountCents(),
                p.getDaysOfWeek(), p.getTimeFrom(), p.getTimeTo(), p.getConditions(), p.getEffectiveFrom(), p.getEffectiveTo(), p.getVerifiedOn(),
                s == null ? null : s.getUrl(), s == null ? null : s.getPublisher(), p.effectiveOn(on),
                caution(p.getUncertaintyNote(), freshness.price(p, on)));
        }).toList();
        var extras = a.getExtras().stream().filter(x -> x.getEffectiveTo() == null || !x.getEffectiveTo().isBefore(on)).map(x -> {
            ActivitySource s = x.getSourceId() == null ? null : sources.get(x.getSourceId());
            return new PublicExtra(x.getLabel(), x.getAmountCents(), x.getUnit(), x.isMandatory(), x.getConditions(), x.getVerifiedOn(), s == null ? null : s.getUrl(),
                caution(x.getUncertaintyNote(), freshness.extra(x, on)));
        }).toList();
        return new ActivityPricing(a.getSlug(), a.getPriceStatus(), from.map(ActivityPriceRule::getAmountCents).orElse(null),
            from.map(ActivityPriceRule::getLabel).orElse(null), prices, extras);
    }

    static PublicMedia publicMedia(ActivityMedia m) {
        return new PublicMedia(m.getKind(), m.getUrl(), m.getThumbnailUrl(), m.getWidth(), m.getHeight(), m.getAltText(), m.getCaption(), m.getCredit(),
            m.getSourcePage(), m.getLicence(), m.getLicenceUrl(), m.getDepiction());
    }

    public ActivityPage page(Activity a) {
        LocalDate today = today();
        var hours = a.getHours().stream().filter(h -> h.getValidTo() == null || !h.getValidTo().isBefore(today))
            .map(h -> new PublicHours(h.getSeasonLabel(), h.getSeasonStart(), h.getSeasonEnd(), h.getDaysOfWeek(), h.getOpens(), h.getCloses(),
                h.getLastEntry(), h.getNotes(), h.getVerifiedOn(), h.getValidFrom(), h.getValidTo(), caution(h.getUncertaintyNote(), freshness.hours(a, h, today))))
            .toList();
        return new ActivityPage(a.getSlug(), a.getTitle(), a.getCategory(), List.copyOf(a.getThemes()), a.getRegion(), regionLabel(a.getRegion()),
            a.getTown(), a.getArea(), areaLabel(a.getArea()), a.getLatitude(), a.getLongitude(), a.getSummary(), a.getDescription(), a.getWhatToExpect(),
            a.getPracticalInfo(), a.getSeasonalInfo(), a.getAccessibilityInfo(), a.getAddress(), a.getOfficialUrl(), a.getDurationMinutes(),
            a.getScheduleType(), a.isWeatherDependent(), a.isIndoor(), a.getMinAge(), a.getAccompanimentRule(), a.getBookingNotes(), a.getKind(),
            a.getEventSchedule(), a.getOperatingStatus(), a.getOperatingNote(), a.getOperatingCheckedOn(), a.getExistenceVerifiedOn(),
            a.getLastVerifiedOn(), a.getMedia().stream().filter(ActivityMedia::isPublic).map(ActivityViews::publicMedia).toList(), seoTitle(a),
            seoDescription(a), hours, a.getFaqs().stream().map(f -> new Faq(f.getQuestion(), f.getAnswer())).toList(),
            a.getSources().stream().map(s -> new PublicSource(s.getUrl(), s.getPublisher(), s.getSourceType(), s.getCovers(), s.getAccessedOn())).toList(),
            a.getUpdatedAt(), a.getParticipationRules(), a.getParticipationNote(), supplierName(a), plannable(a));
    }

    private static String caution(String uncertainty, Optional<String> stale) {
        String text = Stream.concat(Stream.ofNullable(uncertainty), stale.stream()).collect(Collectors.joining(". "));
        return text.isEmpty() ? null : text;
    }

    public AdminActivity admin(Activity a) {
        return new AdminActivity(a.getId(), a.getVersion(), a.getStatus(), a.getSlug(), a.getTitle(), a.getCategory(), a.getSummary(), a.getDescription(),
            a.getArea(), a.getAddress(), a.getOfficialUrl(), a.getDurationMinutes(), a.getScheduleType(), a.isWeatherDependent(), a.isIndoor(),
            a.getMinAge(), a.getAccompanimentRule(), a.getBookingNotes(), a.getPriceStatus(), a.getVerificationNotes(), a.getLastVerifiedOn(),
            a.getVerifiedBy(), stale(a), a.getHeroImage(), a.getHeroImageAlt(), a.getSeoTitle(), a.getSeoDescription(), a.getPublishedAt(),
            a.getUpdatedAt(),
            a.getSources().stream().map(s -> new Source(s.getId(), s.getUrl(), s.getPublisher(), s.getSourceType(), s.getCovers(), s.getAccessedOn())).toList(),
            a.getPriceRules().stream().map(p -> new PriceRule(p.getLabel(), p.getAudience(), p.getMinAge(), p.getMaxAge(), p.getResidency(), p.getUnit(),
                p.getAmountCents(), p.getDaysOfWeek(), p.getTimeFrom(), p.getTimeTo(), p.getConditions(), p.isEstimateDefault(), p.getEffectiveFrom(),
                p.getEffectiveTo(), p.getSourceId(), p.getVerifiedOn(), p.getUncertaintyNote(), p.isPromotional())).toList(),
            a.getExtras().stream().map(x -> new Extra(x.getLabel(), x.getAmountCents(), x.getUnit(), x.isMandatory(), x.getConditions(), x.getEffectiveFrom(),
                x.getEffectiveTo(), x.getSourceId(), x.getVerifiedOn(), x.getUncertaintyNote())).toList(),
            a.getHours().stream().map(h -> new Hours(h.getSeasonLabel(), h.getSeasonStart(), h.getSeasonEnd(), h.getDaysOfWeek(), h.getOpens(), h.getCloses(),
                h.getLastEntry(), h.getNotes(), h.getSourceId(), h.getVerifiedOn(), h.getUncertaintyNote(), h.getValidFrom(), h.getValidTo())).toList(),
            a.getFaqs().stream().map(f -> new Faq(f.getQuestion(), f.getAnswer())).toList(), publishProblems(a), a.getParticipationRules(),
            a.getParticipationNote(), freshness.issues(a, today()), regional(a));
    }

    static Regional regional(Activity a) {
        return new Regional(a.getRegion(), a.getTown(), a.getLatitude(), a.getLongitude(), a.getKind(), a.getEventSchedule(), a.getWhatToExpect(),
            a.getPracticalInfo(), a.getSeasonalInfo(), a.getAccessibilityInfo(), a.getOperatingStatus(), a.getOperatingNote(), a.getOperatingCheckedOn(),
            a.getExistenceVerifiedOn(), List.copyOf(a.getThemes()), a.getMedia().stream().map(m -> new Media(m.getKind(), m.getUrl(), m.getThumbnailUrl(),
                m.getWidth(), m.getHeight(), m.getAltText(), m.getCaption(), m.getCredit(), m.getSourcePage(), m.getLicence(), m.getLicenceUrl(),
                m.getPermission(), m.getDepiction(), m.getCheckedOn())).toList());
    }

    public AdminActivitySummary summary(Activity a) {
        return new AdminActivitySummary(a.getId(), a.getStatus(), a.getSlug(), a.getTitle(), a.getCategory(), a.getRegion(), a.getArea(), a.getPriceStatus(),
            a.getLastVerifiedOn(), stale(a), a.getPriceRules().size(), a.getSources().size(), publishProblems(a), freshness.issues(a, today()));
    }

    public static String seoTitle(Activity a) {
        return a.getSeoTitle() != null && !a.getSeoTitle().isBlank() ? a.getSeoTitle() : a.getTitle() + (a.getRegion() == Region.CAPE_TOWN
            ? " | Things to Do in Cape Town | SL Tours" : " | " + regionLabel(a.getRegion()) + " | Things to Do | SL Tours");
    }
    public static String seoDescription(Activity a) {
        return a.getSeoDescription() != null && !a.getSeoDescription().isBlank() ? a.getSeoDescription() : a.getSummary();
    }
}
