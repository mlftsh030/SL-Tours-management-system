package com.sltours.partners.service;
import com.sltours.activities.dto.ActivityDtos.*;
import com.sltours.activities.model.ActivityEnums.*;
import com.sltours.activities.service.ActivityAdminService;
import com.sltours.audit.AuditService;
import com.sltours.partners.dto.PartnerDtos.*;
import com.sltours.partners.model.PartnerEnums.*;
import com.sltours.shared.error.*;
import com.sltours.shared.util.ReferenceCodes;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Experiences proposed by approved suppliers. Commercial approval creates a DRAFT catalogue entry with no verified prices,
 * hours or participation rules: the supplier's figures are kept only as internal notes, and the entry is published
 * through the normal verification workflow (official source, verification, publication checks).
 */
@Service
public class SupplierProposalService {
    private final JdbcTemplate jdbc;
    private final PartnerSupport support;
    private final ActivityAdminService activities;
    private final AuditService audit;
    public SupplierProposalService(JdbcTemplate jdbc, PartnerSupport support, ActivityAdminService activities, AuditService audit) {
        this.jdbc = jdbc; this.support = support; this.activities = activities; this.audit = audit;
    }

    // ----- supplier -----

    @Transactional
    public ProposalView submit(UUID partnerId, ProposalInput p) {
        if (!support.active(partnerId, Route.SUPPLIER)) throw new org.springframework.security.access.AccessDeniedException("Your supplier route is not approved");
        UUID id = UUID.randomUUID();
        String reference = ReferenceCodes.next("SPR", 5, r -> Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from supplier_proposals where reference = ?)", Boolean.class, r)));
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
            insert into supplier_proposals (id, reference, partner_id, status, title, category, area, summary, description, duration_minutes, address, website,
                proposed_prices, proposed_schedule, participation_info, supplier_notes, created_at, updated_at)
            values (?, ?, ?, 'SUBMITTED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, reference, partnerId, p.title().trim(), p.category().name(),
            p.area().name(), p.summary().trim(), p.description().trim(), p.durationMinutes(), trim(p.address()), trim(p.website()), p.proposedPrices().trim(),
            p.proposedSchedule().trim(), p.participationInfo().trim(), trim(p.supplierNotes()), now, now);
        audit.record("SUPPLIER_PROPOSAL_SUBMITTED", reference, p.title().trim());
        return get(partnerId, id);
    }

    @Transactional
    public ProposalView update(UUID partnerId, UUID id, long version, ProposalInput p) {
        ProposalView current = get(partnerId, id);
        if (current.status() != ProposalStatus.SUBMITTED && current.status() != ProposalStatus.CHANGES_REQUESTED)
            throw new IllegalArgumentException("Only proposals awaiting review or changes can be edited");
        int updated = jdbc.update("""
            update supplier_proposals set title = ?, category = ?, area = ?, summary = ?, description = ?, duration_minutes = ?, address = ?, website = ?,
                proposed_prices = ?, proposed_schedule = ?, participation_info = ?, supplier_notes = ?, status = 'SUBMITTED', version = version + 1, updated_at = ?
            where id = ? and partner_id = ? and version = ?""", p.title().trim(), p.category().name(), p.area().name(), p.summary().trim(), p.description().trim(),
            p.durationMinutes(), trim(p.address()), trim(p.website()), p.proposedPrices().trim(), p.proposedSchedule().trim(), p.participationInfo().trim(),
            trim(p.supplierNotes()), OffsetDateTime.now(), id, partnerId, version);
        if (updated == 0) throw new ConflictException("This proposal changed. Reload before saving.");
        audit.record("SUPPLIER_PROPOSAL_UPDATED", current.reference(), p.title().trim());
        return get(partnerId, id);
    }

    @Transactional
    public ProposalView withdraw(UUID partnerId, UUID id, long version) {
        ProposalView current = get(partnerId, id);
        if (current.status() != ProposalStatus.SUBMITTED && current.status() != ProposalStatus.CHANGES_REQUESTED)
            throw new IllegalArgumentException("This proposal can no longer be withdrawn");
        if (jdbc.update("update supplier_proposals set status = 'WITHDRAWN', version = version + 1, updated_at = ? where id = ? and partner_id = ? and version = ?",
                OffsetDateTime.now(), id, partnerId, version) == 0) throw new ConflictException("This proposal changed. Reload and try again.");
        audit.record("SUPPLIER_PROPOSAL_WITHDRAWN", current.reference(), current.title());
        return get(partnerId, id);
    }

    /** A partner's own proposal; another partner's proposal is reported as not found. */
    @Transactional(readOnly = true)
    public ProposalView get(UUID partnerId, UUID id) {
        return query("p.id = ? and p.partner_id = ?", id, partnerId).stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Proposal not found"));
    }
    @Transactional(readOnly = true)
    public List<ProposalView> forPartner(UUID partnerId) { return query("p.partner_id = ?", partnerId); }

    // ----- administrator -----

    @Transactional(readOnly = true)
    public List<ProposalView> list(ProposalStatus status) {
        return status == null ? query("true") : query("p.status = ?", status.name());
    }

    @Transactional
    public ProposalView decide(UUID id, ProposalDecisionRequest d) {
        var row = jdbc.query("select partner_id, status, version, reference from supplier_proposals where id = ? for update",
            (rs, i) -> new Object[]{rs.getObject(1), rs.getString(2), rs.getLong(3), rs.getString(4)}, id).stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Proposal not found"));
        UUID partnerId = (UUID) row[0];
        if ((long) row[2] != d.version()) throw new ConflictException("This proposal changed. Reload before deciding.");
        if (!"SUBMITTED".equals(row[1])) throw new IllegalArgumentException("Only submitted proposals can be decided");
        if (d.decision() != ProposalDecision.APPROVE && !StringUtils.hasText(d.reason())) throw new IllegalArgumentException("Give a reason for the supplier");
        UUID activityId = null;
        if (d.decision() == ProposalDecision.APPROVE) {
            if (!support.active(partnerId, Route.SUPPLIER)) throw new IllegalArgumentException("The supplier route is not approved");
            ProposalView p = query("p.id = ?", id).get(0);
            activityId = activities.create(draft(p)).id();
            jdbc.update("update activities set supplier_partner_id = ? where id = ?", partnerId, activityId);
        }
        String status = switch (d.decision()) { case APPROVE -> "APPROVED"; case REQUEST_CHANGES -> "CHANGES_REQUESTED"; case REJECT -> "REJECTED"; };
        jdbc.update("""
            update supplier_proposals set status = ?, decision_reason = ?, decided_by = ?, decided_at = ?, activity_id = coalesce(?, activity_id),
                version = version + 1, updated_at = ? where id = ?""", status, trim(d.reason()), PartnerSupport.actor(), OffsetDateTime.now(), activityId,
            OffsetDateTime.now(), id);
        audit.record("SUPPLIER_PROPOSAL_" + status, (String) row[3], (activityId == null ? "" : "draft activity " + activityId + " ") + Objects.toString(trim(d.reason()), ""));
        return query("p.id = ?", id).get(0);
    }

    /** The draft keeps the supplier's claims as internal notes only; nothing is marked verified. */
    private SaveActivityRequest draft(ProposalView p) {
        String notes = clip("From supplier proposal " + p.reference() + " (" + p.partnerName() + "), commercially approved " + PartnerSupport.today()
            + ". NOT VERIFIED - check against an official source before entering prices, hours or limits.\nProposed prices: " + p.proposedPrices()
            + "\nProposed schedule: " + p.proposedSchedule() + "\nParticipation: " + p.participationInfo()
            + (p.supplierNotes() == null ? "" : "\nSupplier notes: " + p.supplierNotes()), 2000);
        return new SaveActivityRequest(0L, slug(p.title()), p.title(), p.category(), p.summary(), p.description(), p.area(), p.address(), p.website(),
            p.durationMinutes(), ScheduleType.SCHEDULE_VARIES, false, false, null, null, null, PriceStatus.NOT_PUBLISHED, notes, null, null, null, null,
            List.of(), List.of(), List.of(), List.of(), List.of(), ParticipationRules.UNKNOWN,
            "Participation limits not yet verified (supplier states: " + clip(p.participationInfo(), 300) + ")", null);
    }

    private String slug(String title) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "experience";
        if (base.length() > 140) base = base.substring(0, 140).replaceAll("-$", "");
        String slug = base;
        for (int n = 2; Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from activities where slug = ?)", Boolean.class, slug)); n++) slug = base + "-" + n;
        return slug;
    }

    private List<ProposalView> query(String where, Object... args) {
        return jdbc.query("""
            select p.*, pa.business_name, a.slug as activity_slug, a.status as activity_status from supplier_proposals p join partners pa on pa.id = p.partner_id
            left join activities a on a.id = p.activity_id where """ + " " + where + " order by p.created_at desc", (rs, i) -> new ProposalView((UUID) rs.getObject("id"),
            rs.getLong("version"), rs.getString("reference"), rs.getString("business_name"), ProposalStatus.valueOf(rs.getString("status")), rs.getString("title"),
            Category.valueOf(rs.getString("category")), Area.valueOf(rs.getString("area")), rs.getString("summary"), rs.getString("description"),
            rs.getInt("duration_minutes"), rs.getString("address"), rs.getString("website"), rs.getString("proposed_prices"), rs.getString("proposed_schedule"),
            rs.getString("participation_info"), rs.getString("supplier_notes"), rs.getString("decision_reason"), PartnerSupport.time(rs.getTimestamp("decided_at")),
            rs.getString("activity_slug"), rs.getString("activity_status"), PartnerSupport.time(rs.getTimestamp("created_at")),
            PartnerSupport.time(rs.getTimestamp("updated_at"))), args);
    }
    private static String trim(String v) { return StringUtils.hasText(v) ? v.trim() : null; }
    private static String clip(String v, int max) { return v.length() <= max ? v : v.substring(0, max - 1) + "…"; }
}
