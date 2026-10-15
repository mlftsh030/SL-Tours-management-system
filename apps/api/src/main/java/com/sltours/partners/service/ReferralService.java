package com.sltours.partners.service;
import com.sltours.audit.AuditService;
import com.sltours.partners.dto.PartnerDtos.*;
import com.sltours.partners.model.PartnerEnums.*;
import com.sltours.shared.error.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Referral attribution rules:
 * <ol>
 * <li>A booking or enquiry is attributed only when it is submitted with the code of a partner whose referral route is
 *     approved at that moment. Unknown, suspended or rejected codes are ignored; the submission still succeeds.</li>
 * <li>The code comes from a referral link (kept in the visitor's browser; a newer link replaces an older one) or is
 *     typed on the form. The capture time is reported by the browser and clamped to the submission time.</li>
 * <li>Attribution is fixed when the record is created. A converted enquiry passes its attribution to the booking.</li>
 * <li>Only an administrator can change attribution, with a reason, and only while the booking has no commission lines.
 *     Every change is kept in referral_attribution_changes and the audit log.</li>
 * <li>Commission eligibility (agreement in force, window, booking type, self-referral) is decided by
 *     {@link CommissionService}, not here: attribution alone earns nothing.</li>
 * </ol>
 */
@Service
public class ReferralService {
    private final JdbcTemplate jdbc;
    private final PartnerSupport support;
    private final AuditService audit;
    public ReferralService(JdbcTemplate jdbc, PartnerSupport support, AuditService audit) { this.jdbc = jdbc; this.support = support; this.audit = audit; }

    record Attribution(UUID partnerId, String code, ReferralSource source, OffsetDateTime capturedAt) {}

    @Transactional(readOnly = true)
    public Optional<ReferralLookup> lookup(String code) {
        return activePartner(code).map(id -> new ReferralLookup(normalize(code), support.name(id)));
    }

    private Optional<UUID> activePartner(String code) {
        if (code == null || !normalize(code).matches("[A-Z0-9]{4,20}")) return Optional.empty();
        return jdbc.query("""
            select p.id from partners p join partner_routes r on r.partner_id = p.id and r.route = 'REFERRAL' and r.status = 'APPROVED'
            where p.referral_code = ?""", (rs, i) -> (UUID) rs.getObject(1), normalize(code)).stream().findFirst();
    }

    Optional<Attribution> resolve(ReferralInput input) {
        if (input == null || input.source() == ReferralSource.ADMIN || input.source() == ReferralSource.ENQUIRY) return Optional.empty();
        return activePartner(input.code()).map(id -> {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime captured = input.source() == ReferralSource.CODE || input.capturedAt() == null || input.capturedAt().isAfter(now) ? now : input.capturedAt();
            return new Attribution(id, normalize(input.code()), input.source(), captured);
        });
    }

    /** Attaches a referral to a newly created booking. Call after the booking row is flushed. */
    @Transactional
    public void attachToBooking(UUID bookingId, ReferralInput input) {
        resolve(input).ifPresent(a -> jdbc.update("""
            update bookings set referral_partner_id = ?, referral_code = ?, referral_source = ?, referral_captured_at = ?
            where id = ? and referral_partner_id is null""", a.partnerId(), a.code(), a.source().name(), a.capturedAt(), bookingId));
    }

    @Transactional
    public void attachToEnquiry(UUID enquiryId, ReferralInput input) {
        resolve(input).ifPresent(a -> jdbc.update("""
            update enquiries set referral_partner_id = ?, referral_code = ?, referral_source = ?, referral_captured_at = ?
            where id = ? and referral_partner_id is null""", a.partnerId(), a.code(), a.source().name(), a.capturedAt(), enquiryId));
    }

    /** A converted enquiry passes its referral to the booking, keeping the original capture time. */
    @Transactional
    public void carryFromEnquiry(UUID enquiryId, UUID bookingId) {
        jdbc.update("""
            update bookings b set referral_partner_id = e.referral_partner_id, referral_code = e.referral_code, referral_source = 'ENQUIRY',
                referral_captured_at = e.referral_captured_at
            from enquiries e where e.id = ? and b.id = ? and e.referral_partner_id is not null and b.referral_partner_id is null""", enquiryId, bookingId);
    }

    /** Administrator change with a reason. Not allowed once commission has been calculated for the booking. */
    @Transactional
    public void changeBookingAttribution(String bookingReference, ChangeAttribution change) {
        var rows = jdbc.query("select id, referral_partner_id from bookings where reference = ? for update",
            (rs, i) -> new UUID[]{(UUID) rs.getObject(1), (UUID) rs.getObject(2)}, bookingReference);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Booking not found");
        UUID bookingId = rows.get(0)[0], from = rows.get(0)[1];
        if (Objects.equals(from, change.partnerId())) throw new IllegalArgumentException("The booking is already attributed this way");
        Integer lines = jdbc.queryForObject("select count(*) from partner_commissions where booking_id = ?", Integer.class, bookingId);
        if (lines != null && lines > 0) throw new ConflictException("Commission has already been calculated for this booking, so its referral cannot change");
        String code = null;
        if (change.partnerId() != null) {
            if (!support.active(change.partnerId(), Route.REFERRAL)) throw new IllegalArgumentException("Choose a partner with an approved referral route");
            code = jdbc.queryForObject("select referral_code from partners where id = ?", String.class, change.partnerId());
        }
        jdbc.update("""
            update bookings set referral_partner_id = ?, referral_code = ?, referral_source = ?, referral_captured_at = ?, version = version + 1 where id = ?""",
            change.partnerId(), code, change.partnerId() == null ? null : ReferralSource.ADMIN.name(), change.partnerId() == null ? null : OffsetDateTime.now(), bookingId);
        jdbc.update("""
            insert into referral_attribution_changes (id, booking_id, from_partner_id, to_partner_id, reason, changed_by, changed_at)
            values (?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), bookingId, from, change.partnerId(), change.reason().trim(), PartnerSupport.actor(), OffsetDateTime.now());
        audit.record("REFERRAL_ATTRIBUTION_CHANGED", bookingReference, support.name(from) + " -> " + support.name(change.partnerId()) + ": " + change.reason().trim());
    }

    @Transactional(readOnly = true)
    public List<AttributionChange> history(UUID bookingId) {
        return jdbc.query("""
            select f.business_name, t.business_name, c.reason, c.changed_by, c.changed_at from referral_attribution_changes c
            left join partners f on f.id = c.from_partner_id left join partners t on t.id = c.to_partner_id where c.booking_id = ? order by c.changed_at""",
            (rs, i) -> new AttributionChange(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), PartnerSupport.time(rs.getTimestamp(5))), bookingId);
    }

    static String normalize(String code) { return code == null ? null : code.trim().toUpperCase(Locale.ROOT); }
}
