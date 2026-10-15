package com.sltours.partners.service;
import com.sltours.audit.AuditService;
import com.sltours.bookings.model.BookingType;
import com.sltours.partners.dto.PartnerDtos.*;
import com.sltours.partners.model.PartnerEnums.*;
import com.sltours.shared.error.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Administrator review of partners: route decisions, agreements and the full partner record. */
@Service
public class PartnerAdminService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final JdbcTemplate jdbc;
    private final PartnerSupport support;
    private final CommissionService commissions;
    private final PartnerFinanceService finance;
    private final SupplierProposalService proposals;
    private final AuditService audit;
    private final SessionRegistry sessions;
    private final SecureRandom random = new SecureRandom();
    public PartnerAdminService(JdbcTemplate jdbc, PartnerSupport support, CommissionService commissions, PartnerFinanceService finance,
            SupplierProposalService proposals, AuditService audit, SessionRegistry sessions) {
        this.jdbc = jdbc; this.support = support; this.commissions = commissions; this.finance = finance; this.proposals = proposals; this.audit = audit;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<PartnerSummary> list(RouteStatus status) {
        List<PartnerSummary> all = jdbc.query("""
            select p.id, p.reference, p.business_name, p.business_type, p.area, p.created_at,
                exists(select 1 from partner_agreements a where a.partner_id = p.id and a.route = 'REFERRAL' and a.effective_from <= current_date
                    and (a.effective_to is null or a.effective_to >= current_date)),
                coalesce((select sum(c.amount_cents) from partner_commissions c where c.partner_id = p.id and c.payout_id is null), 0)
            from partners p order by p.created_at desc""", (rs, i) -> new PartnerSummary((UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
            BusinessType.valueOf(rs.getString(4)), rs.getString(5), routes((UUID) rs.getObject(1)), rs.getBoolean(7), rs.getLong(8),
            PartnerSupport.time(rs.getTimestamp(6))));
        return status == null ? all : all.stream().filter(p -> p.routes().stream().anyMatch(r -> r.status() == status)).toList();
    }

    @Transactional(readOnly = true)
    public PartnerDetail detail(UUID id) {
        var p = jdbc.query("""
            select p.*, u.enabled from partners p join user_accounts u on u.id = p.account_id where p.id = ?""", (rs, i) -> new PartnerDetail(id,
            rs.getLong("version"), rs.getString("reference"), rs.getString("business_name"), BusinessType.valueOf(rs.getString("business_type")),
            rs.getString("contact_name"), rs.getString("email"), rs.getString("phone"), rs.getString("website"), rs.getString("area"),
            rs.getString("registration_number"), rs.getString("licences"), rs.getString("description"), rs.getString("services_offered"),
            rs.getString("referral_code"), rs.getBoolean("enabled"), PartnerSupport.time(rs.getTimestamp("created_at")), routes(id), agreements(id),
            referrals(id), commissions.lines("c.partner_id = ?", id), finance.payouts(id), finance.unpaid(id), finance.supplierPayments(id),
            proposals.forPartner(id)), id).stream().findFirst();
        return p.orElseThrow(() -> new ResourceNotFoundException("Partner not found"));
    }

    /**
     * PENDING -> APPROVED or REJECTED; APPROVED -> SUSPENDED; SUSPENDED -> APPROVED (reinstate); REJECTED -> APPROVED on
     * reconsideration. Rejections and suspensions need a reason. The login is enabled while any route is approved.
     */
    @Transactional
    public PartnerDetail decide(UUID id, RouteDecision d) {
        support.lock(id);
        RouteStatus current = support.routeStatus(id, d.route())
            .orElseThrow(() -> new IllegalArgumentException("This partner did not apply for the " + d.route().name().toLowerCase(Locale.ROOT) + " route"));
        RouteStatus next = switch (d.decision()) {
            case APPROVE -> { if (current != RouteStatus.PENDING && current != RouteStatus.REJECTED) throw invalid(current, d); yield RouteStatus.APPROVED; }
            case REJECT -> { if (current != RouteStatus.PENDING) throw invalid(current, d); yield RouteStatus.REJECTED; }
            case SUSPEND -> { if (current != RouteStatus.APPROVED) throw invalid(current, d); yield RouteStatus.SUSPENDED; }
            case REINSTATE -> { if (current != RouteStatus.SUSPENDED) throw invalid(current, d); yield RouteStatus.APPROVED; }
        };
        if ((next == RouteStatus.REJECTED || next == RouteStatus.SUSPENDED) && !StringUtils.hasText(d.reason()))
            throw new IllegalArgumentException("Give a reason");
        jdbc.update("update partner_routes set status = ?, decided_by = ?, decided_at = ?, decision_reason = ? where partner_id = ? and route = ?",
            next.name(), PartnerSupport.actor(), OffsetDateTime.now(), StringUtils.hasText(d.reason()) ? d.reason().trim() : null, id, d.route().name());
        if (next == RouteStatus.APPROVED && d.route() == Route.REFERRAL) ensureReferralCode(id);
        boolean enabled = routes(id).stream().anyMatch(r -> r.status() == RouteStatus.APPROVED);
        jdbc.update("update user_accounts set enabled = ? where id = (select account_id from partners where id = ?)", enabled, id);
        jdbc.update("update partners set version = version + 1, updated_at = ? where id = ?", OffsetDateTime.now(), id);
        if (!enabled) expireSessions(jdbc.queryForObject("select email from partners where id = ?", String.class, id));
        audit.record("PARTNER_" + d.route() + "_" + next, reference(id), current + "->" + next + (StringUtils.hasText(d.reason()) ? ": " + d.reason().trim() : ""));
        return detail(id);
    }

    /** Records an agreement exactly as agreed. Nothing is defaulted; referral and supplier terms are kept apart. */
    @Transactional
    public PartnerDetail addAgreement(UUID id, NewAgreement a) {
        support.lock(id);
        if (support.routeStatus(id, a.route()).isEmpty()) throw new IllegalArgumentException("This partner has not applied for this route");
        if (a.effectiveTo() != null && a.effectiveTo().isBefore(a.effectiveFrom())) throw new IllegalArgumentException("The end date is before the start date");
        String types = null;
        if (a.route() == Route.REFERRAL) {
            if (a.commissionType() == null || a.eligibleBasis() == null || a.attributionWindowDays() == null || a.refundTreatment() == null
                    || a.eligibleBookingTypes() == null || a.eligibleBookingTypes().isEmpty())
                throw new IllegalArgumentException("Referral agreements need the commission type and value, eligible amount, booking types, attribution window and refund treatment");
            if (a.commissionType() == CommissionType.PERCENTAGE && (a.rateBasisPoints() == null || a.fixedAmountCents() != null))
                throw new IllegalArgumentException("Give a percentage rate (and no fixed amount)");
            if (a.commissionType() == CommissionType.FIXED_PER_BOOKING && (a.fixedAmountCents() == null || a.rateBasisPoints() != null))
                throw new IllegalArgumentException("Give a fixed amount per booking (and no percentage)");
            if (StringUtils.hasText(a.supplierTerms())) throw new IllegalArgumentException("Supplier terms belong on a supplier agreement");
            types = a.eligibleBookingTypes().stream().map(BookingType::name).sorted().collect(Collectors.joining(","));
        } else {
            if (!StringUtils.hasText(a.supplierTerms())) throw new IllegalArgumentException("Record the supplier terms as agreed");
            if (a.commissionType() != null || a.rateBasisPoints() != null || a.fixedAmountCents() != null)
                throw new IllegalArgumentException("Referral commission terms belong on a referral agreement");
        }
        try {
            jdbc.update("""
                insert into partner_agreements (id, partner_id, route, agreement_reference, effective_from, effective_to, commission_type, rate_basis_points,
                    fixed_amount_cents, eligible_basis, eligible_booking_types, attribution_window_days, refund_treatment, minimum_eligible_cents,
                    maximum_commission_cents, supplier_terms, notes, recorded_by, recorded_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), id, a.route().name(), a.agreementReference().trim(), a.effectiveFrom(), a.effectiveTo(),
                a.route() == Route.REFERRAL ? a.commissionType().name() : null, a.rateBasisPoints(), a.fixedAmountCents(),
                a.route() == Route.REFERRAL ? a.eligibleBasis().name() : null, types, a.route() == Route.REFERRAL ? a.attributionWindowDays() : null,
                a.route() == Route.REFERRAL ? a.refundTreatment().name() : null, a.route() == Route.REFERRAL ? a.minimumEligibleCents() : null,
                a.route() == Route.REFERRAL ? a.maximumCommissionCents() : null, a.route() == Route.SUPPLIER ? a.supplierTerms().trim() : null,
                StringUtils.hasText(a.notes()) ? a.notes().trim() : null, PartnerSupport.actor(), OffsetDateTime.now());
        } catch (DataIntegrityViolationException overlap) {
            throw new ConflictException("These dates overlap another " + a.route().name().toLowerCase(Locale.ROOT) + " agreement for this partner. End that one first.");
        }
        audit.record("PARTNER_AGREEMENT_RECORDED", reference(id), a.route() + " " + a.agreementReference().trim() + " from " + a.effectiveFrom()
            + (a.effectiveTo() == null ? "" : " to " + a.effectiveTo()) + ": " + summary(a));
        return detail(id);
    }

    /** Ends an agreement. Terms are never edited, and an end date cannot cut off bookings that already earned under it. */
    @Transactional
    public PartnerDetail endAgreement(UUID id, UUID agreementId, EndAgreement end) {
        support.lock(id);
        var row = jdbc.query("select effective_from, effective_to, agreement_reference from partner_agreements where id = ? and partner_id = ?",
            (rs, i) -> new Object[]{rs.getObject(1, LocalDate.class), rs.getObject(2, LocalDate.class), rs.getString(3)}, agreementId, id)
            .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Agreement not found"));
        LocalDate from = (LocalDate) row[0], to = (LocalDate) row[1];
        if (end.effectiveTo().isBefore(from)) throw new IllegalArgumentException("The end date is before the agreement starts");
        if (to != null && !end.effectiveTo().isBefore(to)) throw new IllegalArgumentException("The agreement already ends on " + to);
        Boolean used = jdbc.queryForObject("""
            select exists(select 1 from partner_commissions c join bookings b on b.id = c.booking_id
                where c.agreement_id = ? and (b.created_at at time zone 'Africa/Johannesburg')::date > ?)""", Boolean.class, agreementId, end.effectiveTo());
        if (Boolean.TRUE.equals(used)) throw new ConflictException("Bookings made after that date already earned commission under this agreement");
        jdbc.update("update partner_agreements set effective_to = ?, ended_by = ?, ended_at = ? where id = ?", end.effectiveTo(), PartnerSupport.actor(),
            OffsetDateTime.now(), agreementId);
        audit.record("PARTNER_AGREEMENT_ENDED", reference(id), row[2] + " ends " + end.effectiveTo() + (to == null ? "" : " (was " + to + ")"));
        return detail(id);
    }

    List<RouteView> routes(UUID id) {
        return jdbc.query("select route, status, decided_by, decided_at, decision_reason from partner_routes where partner_id = ? order by route",
            (rs, i) -> new RouteView(Route.valueOf(rs.getString(1)), RouteStatus.valueOf(rs.getString(2)), rs.getString(3),
                PartnerSupport.time(rs.getTimestamp(4)), rs.getString(5)), id);
    }

    List<AgreementView> agreements(UUID id) {
        return jdbc.query("select * from partner_agreements where partner_id = ? order by route, effective_from desc", (rs, i) -> new AgreementView(
            (UUID) rs.getObject("id"), Route.valueOf(rs.getString("route")), rs.getString("agreement_reference"), rs.getObject("effective_from", LocalDate.class),
            rs.getObject("effective_to", LocalDate.class), rs.getString("commission_type") == null ? null : CommissionType.valueOf(rs.getString("commission_type")),
            (Integer) rs.getObject("rate_basis_points"), (Integer) rs.getObject("fixed_amount_cents"),
            rs.getString("eligible_basis") == null ? null : EligibleBasis.valueOf(rs.getString("eligible_basis")), rs.getString("eligible_booking_types"),
            (Integer) rs.getObject("attribution_window_days"),
            rs.getString("refund_treatment") == null ? null : RefundTreatment.valueOf(rs.getString("refund_treatment")),
            (Integer) rs.getObject("minimum_eligible_cents"), (Integer) rs.getObject("maximum_commission_cents"), rs.getString("supplier_terms"),
            rs.getString("notes"), rs.getString("recorded_by"), PartnerSupport.time(rs.getTimestamp("recorded_at")), rs.getString("ended_by"),
            PartnerSupport.time(rs.getTimestamp("ended_at"))), id);
    }

    private List<AdminReferral> referrals(UUID id) {
        List<AdminReferral> list = new ArrayList<>(jdbc.query("""
            select reference, customer_name, status, referral_source, referral_captured_at, created_at, total_amount_cents, amount_paid_cents
            from bookings where referral_partner_id = ? order by created_at desc""", (rs, i) -> new AdminReferral("BOOKING", rs.getString(1), rs.getString(2),
            rs.getString(3), ReferralSource.valueOf(rs.getString(4)), PartnerSupport.time(rs.getTimestamp(5)), PartnerSupport.time(rs.getTimestamp(6)),
            rs.getInt(7), rs.getInt(8)), id));
        list.addAll(jdbc.query("""
            select reference, customer_name, status, referral_source, referral_captured_at, created_at from enquiries where referral_partner_id = ?
            order by created_at desc""", (rs, i) -> new AdminReferral("ENQUIRY", rs.getString(1), rs.getString(2), rs.getString(3),
            ReferralSource.valueOf(rs.getString(4)), PartnerSupport.time(rs.getTimestamp(5)), PartnerSupport.time(rs.getTimestamp(6)), 0, 0), id));
        return list;
    }

    private void ensureReferralCode(UUID id) {
        if (jdbc.queryForObject("select referral_code from partners where id = ?", String.class, id) != null) return;
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 8; i++) code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            Boolean taken = jdbc.queryForObject("select exists(select 1 from partners where referral_code = ?)", Boolean.class, code.toString());
            if (!Boolean.TRUE.equals(taken)) { jdbc.update("update partners set referral_code = ? where id = ?", code.toString(), id); return; }
        }
        throw new IllegalStateException("Could not allocate a referral code");
    }

    private void expireSessions(String email) {
        for (Object principal : sessions.getAllPrincipals())
            if (principal instanceof UserDetails user && user.getUsername().equals(email))
                sessions.getAllSessions(principal, false).forEach(org.springframework.security.core.session.SessionInformation::expireNow);
    }

    static String summary(NewAgreement a) {
        if (a.route() == Route.SUPPLIER) return "supplier terms recorded";
        String value = a.commissionType() == CommissionType.PERCENTAGE ? (a.rateBasisPoints() / 100.0) + "%" : PartnerSupport.rand(a.fixedAmountCents()) + " per booking";
        return value + " of " + a.eligibleBasis() + ", types " + a.eligibleBookingTypes() + ", window " + a.attributionWindowDays() + " days, refunds "
            + a.refundTreatment() + (a.minimumEligibleCents() == null ? "" : ", minimum " + PartnerSupport.rand(a.minimumEligibleCents()))
            + (a.maximumCommissionCents() == null ? "" : ", cap " + PartnerSupport.rand(a.maximumCommissionCents()));
    }
    private String reference(UUID id) { return jdbc.queryForObject("select reference from partners where id = ?", String.class, id); }
    private static IllegalArgumentException invalid(RouteStatus current, RouteDecision d) {
        return new IllegalArgumentException("Cannot " + d.decision().name().toLowerCase(Locale.ROOT) + " a " + current.name().toLowerCase(Locale.ROOT) + " route");
    }
}
