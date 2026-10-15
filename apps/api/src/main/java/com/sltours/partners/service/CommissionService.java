package com.sltours.partners.service;
import com.sltours.audit.AuditService;
import com.sltours.partners.dto.PartnerDtos.CommissionLine;
import com.sltours.partners.model.PartnerEnums.*;
import java.math.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Referral commission ledger.
 * <p>
 * Earning: when an attributed booking is COMPLETED and fully paid (payments less refunds at least the booking total),
 * one EARNED line is written using the referral agreement in force on the date the booking was made. The agreement, the
 * basis amount and the calculation are stored on the line, and agreements cannot be edited afterwards.
 * <p>
 * Adjustments: a refund recorded after commission was earned writes one REFUND_ADJUSTMENT per refund payment
 * (proportional, or reversing the balance, as the agreement says). A booking moving to REFUNDED or CANCELLED writes one
 * REVERSAL for whatever balance remains. Adjustments never take a booking's commission below zero.
 * <p>
 * Duplicates: the booking row is locked while evaluating, and unique indexes allow one EARNED and one REVERSAL line per
 * booking and one adjustment per refund payment, so retries and concurrent updates cannot pay twice.
 * <p>
 * Money: integer cents; percentages use exact decimal arithmetic in basis points, rounded half-up to the cent.
 */
@Service
public class CommissionService {
    private final JdbcTemplate jdbc;
    private final PartnerSupport support;
    private final AuditService audit;
    public CommissionService(JdbcTemplate jdbc, PartnerSupport support, AuditService audit) { this.jdbc = jdbc; this.support = support; this.audit = audit; }

    record BookingRow(UUID id, String reference, String status, String type, int total, int netPaid, OffsetDateTime createdAt, boolean demo,
        String customerEmail, UUID partnerId, OffsetDateTime capturedAt) {
        boolean fullyPaid() { return total > 0 && netPaid >= total; }
    }
    record Agreement(UUID id, String reference, LocalDate from, LocalDate to, CommissionType type, Integer rateBp, Integer fixedCents, EligibleBasis basis,
        Set<String> bookingTypes, int windowDays, RefundTreatment refunds, Integer minimumCents, Integer maximumCents) {}
    record Eligibility(Agreement agreement, String reason) { boolean ok() { return reason == null; } }

    /** Re-evaluates a booking after a payment, refund or status change. Safe to call any number of times. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(UUID bookingId, UUID refundPaymentId) {
        BookingRow b = lockBooking(bookingId);
        if (b == null || b.partnerId() == null) return;
        var earned = jdbc.query("select id, amount_cents, basis_amount_cents, agreement_id from partner_commissions where booking_id = ? and kind = 'EARNED'",
            (rs, i) -> new Object[]{rs.getObject(1), rs.getInt(2), rs.getInt(3), rs.getObject(4)}, bookingId);
        if (earned.isEmpty()) {
            if (!"COMPLETED".equals(b.status()) || !b.fullyPaid()) return;
            Eligibility e = eligibility(b);
            if (!e.ok()) return;
            int basis = basisAmount(b, e.agreement());
            int amount = calculate(basis, e.agreement());
            if (amount <= 0) return;
            String calculation = describe(basis, amount, e.agreement(), b);
            int inserted = jdbc.update("""
                insert into partner_commissions (id, partner_id, booking_id, agreement_id, kind, amount_cents, basis_amount_cents, calculation, created_by, created_at)
                values (?, ?, ?, ?, 'EARNED', ?, ?, ?, ?, ?) on conflict do nothing""",
                UUID.randomUUID(), b.partnerId(), bookingId, e.agreement().id(), amount, basis, calculation, PartnerSupport.actor(), OffsetDateTime.now());
            if (inserted == 1) audit.record("COMMISSION_EARNED", b.reference(), PartnerSupport.rand(amount) + " " + calculation);
            return;
        }
        int earnedAmount = (int) earned.get(0)[1], earnedBasis = (int) earned.get(0)[2];
        UUID agreementId = (UUID) earned.get(0)[3];
        Agreement agreement = agreement(agreementId);
        if (refundPaymentId != null) {
            int balance = balance(bookingId);
            Integer refund = jdbc.query("select amount_cents from payments where id = ? and booking_id = ? and kind = 'REFUND'",
                (rs, i) -> rs.getInt(1), refundPaymentId, bookingId).stream().findFirst().orElse(null);
            if (refund != null && balance > 0) {
                int reduction = agreement.refunds() == RefundTreatment.FULL_REVERSAL ? balance
                    : Math.min(balance, proportional(earnedAmount, refund, earnedBasis));
                if (reduction > 0) {
                    String text = agreement.refunds() == RefundTreatment.FULL_REVERSAL
                        ? "Refund of " + PartnerSupport.rand(refund) + ": remaining commission reversed (agreement " + agreement.reference() + ")"
                        : "Refund of " + PartnerSupport.rand(refund) + ": " + PartnerSupport.rand(earnedAmount) + " x " + PartnerSupport.rand(refund) + " / "
                            + PartnerSupport.rand(earnedBasis) + " (basis), rounded half-up (agreement " + agreement.reference() + ")";
                    int inserted = jdbc.update("""
                        insert into partner_commissions (id, partner_id, booking_id, agreement_id, kind, amount_cents, basis_amount_cents, calculation,
                            source_payment_id, created_by, created_at)
                        values (?, ?, ?, ?, 'REFUND_ADJUSTMENT', ?, ?, ?, ?, ?, ?) on conflict do nothing""",
                        UUID.randomUUID(), b.partnerId(), bookingId, agreementId, -reduction, refund, text, refundPaymentId, PartnerSupport.actor(), OffsetDateTime.now());
                    if (inserted == 1) audit.record("COMMISSION_ADJUSTED", b.reference(), "-" + PartnerSupport.rand(reduction) + " " + text);
                }
            }
        }
        if ("REFUNDED".equals(b.status()) || "CANCELLED".equals(b.status())) {
            int balance = balance(bookingId);
            if (balance > 0) {
                String text = "Booking " + b.status().toLowerCase(Locale.ROOT) + ": remaining commission of " + PartnerSupport.rand(balance) + " reversed";
                int inserted = jdbc.update("""
                    insert into partner_commissions (id, partner_id, booking_id, agreement_id, kind, amount_cents, basis_amount_cents, calculation, created_by, created_at)
                    values (?, ?, ?, ?, 'REVERSAL', ?, 0, ?, ?, ?) on conflict do nothing""",
                    UUID.randomUUID(), b.partnerId(), bookingId, agreementId, -balance, text, PartnerSupport.actor(), OffsetDateTime.now());
                if (inserted == 1) audit.record("COMMISSION_REVERSED", b.reference(), text);
            }
        }
    }

    /** Why a booking has or has not earned commission, for admin and partner views. */
    @Transactional(readOnly = true)
    public String status(UUID bookingId) {
        BookingRow b = booking(bookingId, false);
        if (b == null || b.partnerId() == null) return null;
        Integer balance = jdbc.queryForObject("select sum(amount_cents) from partner_commissions where booking_id = ?", Integer.class, bookingId);
        if (balance != null) {
            Integer unpaid = jdbc.queryForObject("select count(*) from partner_commissions where booking_id = ? and payout_id is null", Integer.class, bookingId);
            return "Commission " + PartnerSupport.rand(balance) + (unpaid != null && unpaid == 0 ? " (paid out)" : " (not yet paid out)");
        }
        if ("CANCELLED".equals(b.status()) || "REFUNDED".equals(b.status())) return "No commission: booking " + b.status().toLowerCase(Locale.ROOT);
        Eligibility e = eligibility(b);
        if (!e.ok()) return "Not eligible: " + e.reason();
        if (!"COMPLETED".equals(b.status())) return "Pending: the trip has not been completed";
        if (!b.fullyPaid()) return "Pending: the booking is not fully paid";
        return "Pending calculation";
    }

    Eligibility eligibility(BookingRow b) {
        if (b.demo()) return new Eligibility(null, "development sample booking");
        if (!support.active(b.partnerId(), Route.REFERRAL)) return new Eligibility(null, "the referral partnership is not active");
        LocalDate made = b.createdAt().atZoneSameInstant(PartnerSupport.ZONE).toLocalDate();
        Optional<Agreement> agreement = jdbc.query("""
            select * from partner_agreements where partner_id = ? and route = 'REFERRAL' and effective_from <= ? and (effective_to is null or effective_to >= ?)""",
            (rs, i) -> agreement(rs), b.partnerId(), made, made).stream().findFirst();
        if (agreement.isEmpty()) return new Eligibility(null, "no referral agreement was in force when the booking was made (" + made + ")");
        Agreement a = agreement.get();
        if (!a.bookingTypes().contains(b.type())) return new Eligibility(a, "this booking type is not covered by agreement " + a.reference());
        if (b.capturedAt() == null || Duration.between(b.capturedAt(), b.createdAt()).toDays() >= a.windowDays())
            return new Eligibility(a, "the referral was older than the " + a.windowDays() + "-day attribution window");
        String partnerEmails = jdbc.queryForObject("select p.email || ' ' || u.email from partners p join user_accounts u on u.id = p.account_id where p.id = ?",
            String.class, b.partnerId());
        if (partnerEmails != null && Arrays.asList(partnerEmails.split(" ")).contains(b.customerEmail())) return new Eligibility(a, "self-referral");
        if (a.minimumCents() != null && basisAmount(b, a) < a.minimumCents())
            return new Eligibility(a, "below the minimum eligible amount of " + PartnerSupport.rand(a.minimumCents()));
        return new Eligibility(a, null);
    }

    static int basisAmount(BookingRow b, Agreement a) {
        return a.basis() == EligibleBasis.BOOKING_TOTAL ? b.total() : Math.max(0, Math.min(b.netPaid(), b.total()));
    }
    static int calculate(int basis, Agreement a) {
        long amount = a.type() == CommissionType.FIXED_PER_BOOKING ? a.fixedCents()
            : BigDecimal.valueOf(basis).multiply(BigDecimal.valueOf(a.rateBp())).divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact();
        if (a.maximumCents() != null) amount = Math.min(amount, a.maximumCents());
        return Math.toIntExact(amount);
    }
    static int proportional(int earned, int refund, int basis) {
        if (basis <= 0) return earned;
        return BigDecimal.valueOf(earned).multiply(BigDecimal.valueOf(refund)).divide(BigDecimal.valueOf(basis), 0, RoundingMode.HALF_UP).intValueExact();
    }
    static String describe(int basis, int amount, Agreement a, BookingRow b) {
        String rule = a.type() == CommissionType.FIXED_PER_BOOKING ? "fixed " + PartnerSupport.rand(a.fixedCents()) + " per booking"
            : new BigDecimal(a.rateBp()).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString() + "% of " + PartnerSupport.rand(basis)
                + (a.basis() == EligibleBasis.NET_AMOUNT_PAID ? " (net amount paid)" : " (booking total)") + ", rounded half-up";
        String cap = a.maximumCents() != null && amount == a.maximumCents() ? "; capped at " + PartnerSupport.rand(a.maximumCents()) : "";
        return rule + cap + " under agreement " + a.reference() + " (from " + a.from() + (a.to() == null ? "" : " to " + a.to()) + "); booking made "
            + b.createdAt().atZoneSameInstant(PartnerSupport.ZONE).toLocalDate() + ", referral captured "
            + b.capturedAt().atZoneSameInstant(PartnerSupport.ZONE).toLocalDate();
    }

    private int balance(UUID bookingId) {
        Integer v = jdbc.queryForObject("select coalesce(sum(amount_cents), 0) from partner_commissions where booking_id = ?", Integer.class, bookingId);
        return v == null ? 0 : v;
    }
    private BookingRow lockBooking(UUID id) { return booking(id, true); }
    private BookingRow booking(UUID id, boolean lock) {
        return jdbc.query("""
            select id, reference, status, booking_type, total_amount_cents, amount_paid_cents, created_at, demo_data, customer_email, referral_partner_id,
                referral_captured_at from bookings where id = ?""" + (lock ? " for update" : ""), (rs, i) -> new BookingRow((UUID) rs.getObject(1),
            rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6), PartnerSupport.time(rs.getTimestamp(7)), rs.getBoolean(8),
            rs.getString(9), (UUID) rs.getObject(10), PartnerSupport.time(rs.getTimestamp(11))), id).stream().findFirst().orElse(null);
    }
    Agreement agreement(UUID id) { return jdbc.queryForObject("select * from partner_agreements where id = ?", (rs, i) -> agreement(rs), id); }
    static Agreement agreement(ResultSet rs) throws SQLException {
        return new Agreement((UUID) rs.getObject("id"), rs.getString("agreement_reference"), rs.getObject("effective_from", LocalDate.class),
            rs.getObject("effective_to", LocalDate.class), CommissionType.valueOf(rs.getString("commission_type")), (Integer) rs.getObject("rate_basis_points"),
            (Integer) rs.getObject("fixed_amount_cents"), EligibleBasis.valueOf(rs.getString("eligible_basis")),
            new HashSet<>(Arrays.asList(rs.getString("eligible_booking_types").split(","))), rs.getInt("attribution_window_days"),
            RefundTreatment.valueOf(rs.getString("refund_treatment")), (Integer) rs.getObject("minimum_eligible_cents"), (Integer) rs.getObject("maximum_commission_cents"));
    }

    @Transactional(readOnly = true)
    public List<CommissionLine> lines(String where, Object... args) {
        return jdbc.query("""
            select c.id, b.reference, c.kind, c.amount_cents, c.basis_amount_cents, c.calculation, a.agreement_reference, c.payout_id, p.payment_reference, c.created_at
            from partner_commissions c join bookings b on b.id = c.booking_id join partner_agreements a on a.id = c.agreement_id
            left join partner_payouts p on p.id = c.payout_id where """ + " " + where + " order by c.created_at, c.kind", (rs, i) -> new CommissionLine((UUID) rs.getObject(1),
            rs.getString(2), CommissionKind.valueOf(rs.getString(3)), rs.getInt(4), rs.getInt(5), rs.getString(6), rs.getString(7), (UUID) rs.getObject(8),
            rs.getString(9), PartnerSupport.time(rs.getTimestamp(10))), args);
    }
}
