package com.sltours.activities.service;
import com.sltours.activities.dto.ActivityDtos.*;
import com.sltours.activities.model.*;
import com.sltours.activities.model.ActivityEnums.*;
import com.sltours.activities.repository.ActivityRepository;
import com.sltours.audit.AuditService;
import com.sltours.shared.error.*;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Admin editing: drafts can be saved freely; publishing requires sourced, recently verified information. */
@Service
public class ActivityAdminService {
    private final ActivityRepository activities;
    private final ActivityViews views;
    private final AuditService audit;
    private final EntityManager entityManager;
    public ActivityAdminService(ActivityRepository activities, ActivityViews views, AuditService audit, EntityManager entityManager) {
        this.activities = activities; this.views = views; this.audit = audit; this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<AdminActivitySummary> list() { return activities.findAllByOrderByStatusAscTitleAsc().stream().map(views::summary).toList(); }

    @Transactional(readOnly = true)
    public AdminActivity get(UUID id) { return views.admin(find(id)); }

    @Transactional
    public AdminActivity create(SaveActivityRequest r) {
        if (activities.existsBySlug(r.slug())) throw new ConflictException("An activity with this URL already exists");
        Activity draft = new Activity();
        Set<UUID> sourceIds = applyWithSources(draft, r);
        // Continue with the managed instance: save() may merge into a copy, and rows added to the original would be lost.
        Activity a = activities.saveAndFlush(draft);
        applyCitingRows(a, r, sourceIds);
        activities.flush();
        audit.record("ACTIVITY_CREATED", a.getId().toString(), a.getSlug());
        return views.admin(a);
    }

    @Transactional
    public AdminActivity update(UUID id, SaveActivityRequest r) {
        Activity a = find(id);
        if (a.getVersion() != r.version()) throw new ConflictException("This activity changed. Reload before saving.");
        if (activities.existsBySlugAndIdNot(r.slug(), id)) throw new ConflictException("An activity with this URL already exists");
        boolean pricesChanged = a.getPriceStatus() != r.priceStatus() || !priceFingerprint(a).equals(requestFingerprint(r));
        Set<UUID> sourceIds = applyWithSources(a, r);
        activities.flush();
        applyCitingRows(a, r, sourceIds);
        activities.flush();
        // A published page must keep meeting the publication rules after edits.
        if (a.getStatus() == Status.PUBLISHED) {
            List<String> problems = views.publishProblems(a);
            if (!problems.isEmpty()) throw new IllegalArgumentException("Cannot save a published activity with problems: " + String.join("; ", problems)
                + ". Unpublish it first to keep editing as a draft.");
        }
        audit.record("ACTIVITY_UPDATED", id.toString(), a.getSlug() + (pricesChanged ? " (prices changed)" : ""));
        return views.admin(a);
    }

    /** Records that an administrator re-checked the cited sources today. */
    @Transactional
    public AdminActivity verify(UUID id, long version) {
        Activity a = find(id);
        if (a.getVersion() != version) throw new ConflictException("This activity changed. Reload before saving.");
        if (a.getSources().isEmpty()) throw new IllegalArgumentException("Add the sources you checked before marking the activity as verified");
        a.markVerified(ActivityViews.today(), actor());
        activities.flush();
        audit.record("ACTIVITY_VERIFIED", id.toString(), a.getSlug());
        return views.admin(a);
    }

    @Transactional
    public AdminActivity setStatus(UUID id, long version, Status status) {
        Activity a = find(id);
        if (a.getVersion() != version) throw new ConflictException("This activity changed. Reload before saving.");
        if (status == Status.PUBLISHED) {
            List<String> problems = views.publishProblems(a);
            if (!problems.isEmpty()) throw new IllegalArgumentException("Cannot publish yet: " + String.join("; ", problems));
            if (a.getPublishedAt() == null) a.setPublishedAt(OffsetDateTime.now());
        }
        Status previous = a.getStatus();
        a.setStatus(status);
        activities.flush();
        audit.record("ACTIVITY_STATUS_CHANGED", id.toString(), a.getSlug() + " " + previous + "->" + status);
        return views.admin(a);
    }

    /** Fields and sources. Sources are written before the rows that cite them, so the foreign keys are satisfied. */
    private Set<UUID> applyWithSources(Activity a, SaveActivityRequest r) {
        if (r.heroImage() != null && !r.heroImage().isBlank()
                && (!r.heroImage().matches("/assets/[a-zA-Z0-9_./-]+\\.(?:png|jpg|jpeg|webp|avif)") || r.heroImage().contains("..")))
            throw new IllegalArgumentException("Images must use an existing /assets/ image path (PNG, JPEG, WebP or AVIF)");
        if (StringUtils.hasText(r.heroImage()) && !StringUtils.hasText(r.heroImageAlt()))
            throw new IllegalArgumentException("Describe the image (alternative text)");
        a.setSlug(r.slug()); a.setTitle(r.title().trim()); a.setCategory(r.category()); a.setSummary(r.summary().trim());
        a.setDescription(r.description().trim()); a.setArea(r.area()); a.setAddress(trim(r.address())); a.setOfficialUrl(trim(r.officialUrl()));
        a.setDurationMinutes(r.durationMinutes()); a.setScheduleType(r.scheduleType()); a.setWeatherDependent(r.weatherDependent());
        a.setIndoor(r.indoor()); a.setMinAge(r.minAge()); a.setAccompanimentRule(trim(r.accompanimentRule())); a.setBookingNotes(trim(r.bookingNotes()));
        a.setPriceStatus(r.priceStatus()); a.setVerificationNotes(trim(r.verificationNotes())); a.setHeroImage(trim(r.heroImage()));
        a.setHeroImageAlt(trim(r.heroImageAlt())); a.setSeoTitle(trim(r.seoTitle())); a.setSeoDescription(trim(r.seoDescription()));
        a.setParticipationRules(r.participationRules()); a.setParticipationNote(trim(r.participationNote()));
        if (r.participationRules() != ParticipationRules.KNOWN && a.getParticipationNote() == null)
            throw new IllegalArgumentException("Explain which participation limits are not published");

        Regional g = r.regional();
        if (g != null) applyRegional(a, g);
        if (a.getArea() != null && a.getRegion() != Region.CAPE_TOWN) throw new IllegalArgumentException("Planning areas apply only to Cape Town listings");

        // Replace child rows; flush the removals first so re-used source ids do not collide.
        a.getSources().clear(); a.getPriceRules().clear(); a.getExtras().clear(); a.getHours().clear(); a.getFaqs().clear();
        if (g != null) a.getMedia().clear();
        if (entityManager.contains(a)) entityManager.flush();
        if (g != null) for (int i = 0; i < g.media().size(); i++) a.getMedia().add(media(a, g.media().get(i), i));
        Set<UUID> sourceIds = new HashSet<>();
        for (int i = 0; i < r.sources().size(); i++) {
            Source s = r.sources().get(i);
            UUID sid = s.id() == null ? UUID.randomUUID() : s.id();
            if (!sourceIds.add(sid)) throw new IllegalArgumentException("Duplicate source");
            a.getSources().add(new ActivitySource(sid, a, s.url().trim(), s.publisher().trim(), s.sourceType(), s.covers().trim(), s.accessedOn(), i));
        }
        return sourceIds;
    }

    private static void applyRegional(Activity a, Regional g) {
        if ((g.latitude() == null) != (g.longitude() == null)) throw new IllegalArgumentException("Give both coordinates or neither");
        if (g.kind() == Kind.EVENT && !StringUtils.hasText(g.eventSchedule())) throw new IllegalArgumentException("Describe when the event takes place");
        if (g.operatingStatus() != OperatingStatus.OPEN && !StringUtils.hasText(g.operatingNote()))
            throw new IllegalArgumentException("Explain the operating status (closure, season or what is unknown)");
        a.setRegion(g.region()); a.setTown(trim(g.town())); a.setCoordinates(g.latitude(), g.longitude()); a.setKind(g.kind());
        a.setEventSchedule(trim(g.eventSchedule())); a.setWhatToExpect(trim(g.whatToExpect())); a.setPracticalInfo(trim(g.practicalInfo()));
        a.setSeasonalInfo(trim(g.seasonalInfo())); a.setAccessibilityInfo(trim(g.accessibilityInfo())); a.setOperatingStatus(g.operatingStatus());
        a.setOperatingNote(trim(g.operatingNote())); a.setOperatingCheckedOn(g.operatingCheckedOn()); a.setExistenceVerifiedOn(g.existenceVerifiedOn());
        a.getThemes().clear(); a.getThemes().addAll(g.themes());
    }

    /** Images must be files under /assets; videos must be privacy-enhanced YouTube embeds. */
    private static ActivityMedia media(Activity a, Media m, int order) {
        String url = m.url().trim();
        boolean ok = m.kind() == MediaKind.IMAGE
            ? url.matches("/assets/[A-Za-z0-9_./-]+\\.(?:jpg|jpeg|png|webp|avif)") && !url.contains("..")
            : url.matches("https://www\\.youtube-nocookie\\.com/embed/[A-Za-z0-9_-]{6,20}");
        if (!ok) throw new IllegalArgumentException(m.kind() == MediaKind.IMAGE ? "Pictures must use an /assets/ image path (JPEG, PNG, WebP or AVIF)"
            : "Videos must use a https://www.youtube-nocookie.com/embed/ link");
        String thumb = trim(m.thumbnailUrl());
        if (thumb != null && (!thumb.matches("/assets/[A-Za-z0-9_./-]+\\.(?:jpg|jpeg|png|webp|avif)") || thumb.contains("..")))
            throw new IllegalArgumentException("Thumbnails must use an /assets/ image path");
        ActivityMedia row = new ActivityMedia(a);
        row.set(m.kind(), url, thumb, m.width(), m.height(), m.altText().trim(), trim(m.caption()), m.credit().trim(), m.sourcePage().trim(),
            m.licence().trim(), trim(m.licenceUrl()), m.permission(), m.depiction(), m.checkedOn(), order);
        return row;
    }

    private void applyCitingRows(Activity a, SaveActivityRequest r, Set<UUID> sourceIds) {
        for (int i = 0; i < r.priceRules().size(); i++) {
            PriceRule p = r.priceRules().get(i);
            if (p.minAge() != null && p.maxAge() != null && p.minAge() > p.maxAge()) throw new IllegalArgumentException("Price \"" + p.label() + "\": minimum age is above maximum age");
            if (p.effectiveFrom() != null && p.effectiveTo() != null && p.effectiveFrom().isAfter(p.effectiveTo()))
                throw new IllegalArgumentException("Price \"" + p.label() + "\": effective dates are reversed");
            if ((p.timeFrom() == null) != (p.timeTo() == null)) throw new IllegalArgumentException("Price \"" + p.label() + "\": give both session times or neither");
            checkSource(p.sourceId(), sourceIds, p.label());
            ActivityPriceRule rule = new ActivityPriceRule(a);
            rule.set(p.label().trim(), p.audience(), p.minAge(), p.maxAge(), p.residency(), p.unit(), p.amountCents(), DayRules.normalize(p.daysOfWeek()),
                p.timeFrom(), p.timeTo(), trim(p.conditions()), p.estimateDefault(), p.effectiveFrom(), p.effectiveTo(), p.sourceId(), p.verifiedOn(), i);
            rule.setQualifiers(trim(p.uncertaintyNote()), p.promotional());
            if (p.promotional() && p.effectiveTo() == null) throw new IllegalArgumentException("Price \"" + p.label() + "\": give the promotion's end date");
            a.getPriceRules().add(rule);
        }
        for (int i = 0; i < r.extras().size(); i++) {
            Extra x = r.extras().get(i);
            checkSource(x.sourceId(), sourceIds, x.label());
            ActivityExtra extra = new ActivityExtra(a);
            extra.set(x.label().trim(), x.amountCents(), x.unit(), x.mandatory(), trim(x.conditions()), x.effectiveFrom(), x.effectiveTo(), x.sourceId(), x.verifiedOn(), i);
            extra.setUncertaintyNote(trim(x.uncertaintyNote()));
            a.getExtras().add(extra);
        }
        for (int i = 0; i < r.hours().size(); i++) {
            Hours h = r.hours().get(i);
            if ((h.seasonStart() == null) != (h.seasonEnd() == null)) throw new IllegalArgumentException("Give both season dates or neither");
            if (!h.closes().isAfter(h.opens())) throw new IllegalArgumentException("Closing time must be after opening time");
            checkSource(h.sourceId(), sourceIds, "opening hours");
            ActivityHours hours = new ActivityHours(a);
            hours.set(trim(h.seasonLabel()), h.seasonStart(), h.seasonEnd(), DayRules.normalize(h.daysOfWeek()), h.opens(), h.closes(), h.lastEntry(),
                trim(h.notes()), h.sourceId(), i);
            if (h.validFrom() != null && h.validTo() != null && h.validFrom().isAfter(h.validTo())) throw new IllegalArgumentException("Hours: valid dates are reversed");
            hours.setVerification(h.verifiedOn(), trim(h.uncertaintyNote()), h.validFrom(), h.validTo());
            a.getHours().add(hours);
        }
        for (int i = 0; i < r.faqs().size(); i++) a.getFaqs().add(new ActivityFaq(a, r.faqs().get(i).question().trim(), r.faqs().get(i).answer().trim(), i));
    }

    private static void checkSource(UUID sourceId, Set<UUID> sourceIds, String what) {
        if (sourceId != null && !sourceIds.contains(sourceId)) throw new IllegalArgumentException("\"" + what + "\" refers to a source that is not listed");
    }
    private static String priceFingerprint(Activity a) {
        return a.getPriceRules().stream().map(p -> p.getLabel() + p.getAmountCents() + p.getEffectiveFrom() + p.getEffectiveTo()).collect(Collectors.joining("|"));
    }
    private static String requestFingerprint(SaveActivityRequest r) {
        return r.priceRules().stream().map(p -> p.label().trim() + p.amountCents() + p.effectiveFrom() + p.effectiveTo()).collect(Collectors.joining("|"));
    }
    private Activity find(UUID id) { return activities.findById(id).orElseThrow(() -> new ResourceNotFoundException("Activity not found")); }
    private static String trim(String v) { return StringUtils.hasText(v) ? v.trim() : null; }
    private static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
