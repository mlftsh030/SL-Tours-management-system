package com.sltours.activities.service;
import com.sltours.activities.model.*;
import com.sltours.activities.model.ActivityEnums.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Field-specific re-check periods. A recent overall verification is not enough: session timetables and promotions change
 * much faster than standard admission prices. Anything older than its period is still shown, but plans treat it as
 * unconfirmed (prices become estimates; hours and sessions carry a caution).
 */
@Component
public class CatalogueFreshness {
    private final int standardPriceDays, promotionalPriceDays, extraDays, openingHoursDays, sessionDays, participationDays;

    public CatalogueFreshness(@Value("${sltours.catalogue.freshness.standard-price-days:180}") int standardPriceDays,
            @Value("${sltours.catalogue.freshness.promotional-price-days:14}") int promotionalPriceDays,
            @Value("${sltours.catalogue.freshness.extra-days:180}") int extraDays,
            @Value("${sltours.catalogue.freshness.opening-hours-days:120}") int openingHoursDays,
            @Value("${sltours.catalogue.freshness.session-days:30}") int sessionDays,
            @Value("${sltours.catalogue.freshness.participation-days:365}") int participationDays) {
        this.standardPriceDays = standardPriceDays; this.promotionalPriceDays = promotionalPriceDays; this.extraDays = extraDays;
        this.openingHoursDays = openingHoursDays; this.sessionDays = sessionDays; this.participationDays = participationDays;
    }
    /** Closures and reopenings change quickly, so whether a place is open is re-checked more often than its description. */
    public static final int OPERATING_STATUS_DAYS = 90;
    public static CatalogueFreshness defaults() { return new CatalogueFreshness(180, 14, 180, 120, 30, 365); }

    public Optional<String> price(ActivityPriceRule p, LocalDate today) {
        int days = p.isPromotional() ? promotionalPriceDays : standardPriceDays;
        return stale(p.getVerifiedOn(), days, today, p.isPromotional() ? "Promotional price" : "Price");
    }
    public Optional<String> extra(ActivityExtra x, LocalDate today) { return stale(x.getVerifiedOn(), extraDays, today, "Additional cost"); }
    public Optional<String> hours(Activity a, ActivityHours h, LocalDate today) {
        boolean sessions = a.getScheduleType() == ScheduleType.SESSIONS;
        return stale(h.getVerifiedOn(), sessions ? sessionDays : openingHoursDays, today, sessions ? "Session times" : "Opening hours");
    }
    public Optional<String> participation(Activity a, LocalDate today) {
        return stale(a.getLastVerifiedOn(), participationDays, today, "Participation rules");
    }

    /** Everything an administrator should re-check, for the catalogue list and editor. */
    public List<String> issues(Activity a, LocalDate today) {
        List<String> issues = new ArrayList<>();
        a.getPriceRules().stream().filter(p -> p.getEffectiveTo() == null || !p.getEffectiveTo().isBefore(today))
            .forEach(p -> price(p, today).ifPresent(m -> issues.add(p.getLabel() + ": " + m)));
        a.getExtras().forEach(x -> extra(x, today).ifPresent(m -> issues.add(x.getLabel() + ": " + m)));
        a.getHours().stream().map(h -> hours(a, h, today)).flatMap(Optional::stream).distinct().forEach(issues::add);
        a.getPriceRules().stream().filter(p -> p.getUncertaintyNote() != null).forEach(p -> issues.add(p.getLabel() + ": unresolved - " + p.getUncertaintyNote()));
        a.getExtras().stream().filter(x -> x.getUncertaintyNote() != null).forEach(x -> issues.add(x.getLabel() + ": unresolved - " + x.getUncertaintyNote()));
        a.getHours().stream().map(ActivityHours::getUncertaintyNote).filter(Objects::nonNull).distinct().forEach(n -> issues.add("Hours: unresolved - " + n));
        if (a.getParticipationRules() != ParticipationRules.KNOWN) issues.add("Participation limits are " +
            (a.getParticipationRules() == ParticipationRules.UNKNOWN ? "not published" : "only partly published"));
        boolean expiring = a.getPriceRules().stream().anyMatch(p -> p.getEffectiveTo() != null && !p.getEffectiveTo().isBefore(today)
            && p.getEffectiveTo().isBefore(today.plusDays(45)))
            && a.getPriceRules().stream().noneMatch(p -> p.getEffectiveFrom() != null && p.getEffectiveFrom().isAfter(today));
        if (expiring) issues.add("Prices end within 45 days and no later prices are recorded");
        stale(a.getOperatingCheckedOn(), OPERATING_STATUS_DAYS, today, "Operating status").ifPresent(issues::add);
        if (a.getMedia().stream().anyMatch(m -> !m.isPublic())) issues.add("Some pictures or videos await permission and are not shown");
        return issues;
    }

    private static Optional<String> stale(LocalDate checked, int days, LocalDate today, String what) {
        if (checked == null) return Optional.of(what + ": no check recorded");
        if (checked.isBefore(today.minusDays(days)))
            return Optional.of(what + " last checked " + checked + "; this kind of information is re-checked every " + days + " days");
        return Optional.empty();
    }
}
