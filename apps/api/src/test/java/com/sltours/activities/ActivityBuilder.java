package com.sltours.activities;
import com.sltours.activities.model.*;
import com.sltours.activities.model.ActivityEnums.*;
import java.time.*;
import java.util.UUID;

/** Builds in-memory activities for itinerary tests. */
class ActivityBuilder {
    final Activity a = new Activity();
    final UUID source = UUID.randomUUID();
    private LocalDate checkedOn = LocalDate.now();
    ActivityBuilder(String slug, Category category, Area area, int minutes, ScheduleType schedule) {
        a.setSlug(slug); a.setTitle(slug); a.setCategory(category); a.setArea(area); a.setDurationMinutes(minutes); a.setScheduleType(schedule);
        a.setSummary("s"); a.setDescription("d"); a.setPriceStatus(PriceStatus.VERIFIED); a.setStatus(Status.PUBLISHED); a.setAddress("Somewhere");
        a.getSources().add(new ActivitySource(source, a, "https://example.org", "Operator", SourceType.OFFICIAL_OPERATOR, "prices", LocalDate.now(), 0));
    }
    ActivityBuilder price(String label, Audience audience, Integer min, Integer max, Residency residency, int cents, boolean byDefault) {
        return price(label, audience, min, max, residency, Unit.PER_PERSON, cents, byDefault, null, null, null, null, null);
    }
    ActivityBuilder price(String label, Audience audience, Integer min, Integer max, Residency residency, Unit unit, int cents, boolean byDefault,
            String days, String from, String to, LocalDate effFrom, LocalDate effTo) {
        ActivityPriceRule p = new ActivityPriceRule(a);
        p.set(label, audience, min, max, residency, unit, cents, days, from == null ? null : LocalTime.parse(from), to == null ? null : LocalTime.parse(to),
            null, byDefault, effFrom, effTo, source, checkedOn, a.getPriceRules().size());
        a.getPriceRules().add(p); return this;
    }
    ActivityBuilder hours(String opens, String closes, String lastEntry, String days, String seasonStart, String seasonEnd) {
        ActivityHours h = new ActivityHours(a);
        h.set(null, seasonStart, seasonEnd, days, LocalTime.parse(opens), LocalTime.parse(closes), lastEntry == null ? null : LocalTime.parse(lastEntry), null, source, a.getHours().size());
        h.setVerification(checkedOn, null, null, null);
        a.getHours().add(h); return this;
    }
    /** Verification date for rows added after this call. */
    ActivityBuilder checkedOn(LocalDate date) { checkedOn = date; return this; }
    ActivityBuilder lastPriceQualifiers(String uncertainty, boolean promotional) {
        a.getPriceRules().get(a.getPriceRules().size() - 1).setQualifiers(uncertainty, promotional); return this;
    }
    ActivityBuilder lastHoursVerification(String uncertainty, LocalDate validFrom, LocalDate validTo) {
        a.getHours().get(a.getHours().size() - 1).setVerification(checkedOn, uncertainty, validFrom, validTo); return this;
    }
    ActivityBuilder participation(ParticipationRules rules, String note) { a.setParticipationRules(rules); a.setParticipationNote(note); return this; }
    ActivityBuilder status(PriceStatus status) { a.setPriceStatus(status); return this; }
    ActivityBuilder minAge(int age) { a.setMinAge(age); return this; }
    Activity build() { return a; }
}
