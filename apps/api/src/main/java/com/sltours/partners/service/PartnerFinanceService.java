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
 * Administrator-recorded money paid out: referral commission payouts and, separately, supplier payments. Nothing is paid
 * automatically; these records note payments made outside the system, with their payment references.
 */
@Service
public class PartnerFinanceService {
    private final JdbcTemplate jdbc;
    private final PartnerSupport support;
    private final AuditService audit;
    public PartnerFinanceService(JdbcTemplate jdbc, PartnerSupport support, AuditService audit) { this.jdbc = jdbc; this.support = support; this.audit = audit; }

    /**
     * Settles the chosen unpaid commission lines. The amount must equal their total (adjustments included), so a payout
     * always matches the ledger. Retrying with the same key returns the original payout; two payouts can never claim the
     * same line because the partner row is locked and lines are only claimed while unpaid.
     */
    @Transactional
    public PayoutView recordPayout(UUID partnerId, NewPayout p) {
        var previous = payoutByKey(p.idempotencyKey());
        if (previous.isPresent()) {
            if (!previous.get().partnerId().equals(partnerId)) throw new ConflictException("This payout key was already used for another partner");
            return previous.get().view();
        }
        support.lock(partnerId);
        Set<UUID> ids = new LinkedHashSet<>(p.commissionIds());
        List<Integer> amounts = new ArrayList<>();
        for (UUID id : ids) {
            var line = jdbc.query("select amount_cents from partner_commissions where id = ? and partner_id = ? and payout_id is null",
                (rs, i) -> rs.getInt(1), id, partnerId);
            if (line.isEmpty()) throw new ConflictException("A selected commission line is already paid out or does not belong to this partner. Reload and try again.");
            amounts.add(line.get(0));
        }
        long total = amounts.stream().mapToLong(Integer::longValue).sum();
        if (total <= 0) throw new IllegalArgumentException("The selected lines total " + PartnerSupport.rand(total) + "; a payout must be more than R0.00");
        if (total != p.amountCents())
            throw new IllegalArgumentException("The payout amount must equal the selected lines: " + PartnerSupport.rand(total));
        UUID payoutId = UUID.randomUUID();
        jdbc.update("""
            insert into partner_payouts (id, partner_id, amount_cents, paid_on, method, payment_reference, notes, idempotency_key, recorded_by, recorded_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", payoutId, partnerId, p.amountCents(), p.paidOn(), p.method().trim(), p.paymentReference().trim(),
            p.notes(), p.idempotencyKey(), PartnerSupport.actor(), OffsetDateTime.now());
        int claimed = 0;
        for (UUID id : ids) claimed += jdbc.update("update partner_commissions set payout_id = ? where id = ? and payout_id is null", payoutId, id);
        if (claimed != ids.size()) throw new ConflictException("Some lines were paid out at the same time. Reload and try again.");
        audit.record("PARTNER_PAYOUT_RECORDED", partnerId.toString(), PartnerSupport.rand(p.amountCents()) + " ref " + p.paymentReference().trim()
            + " for " + ids.size() + " line(s), paid " + p.paidOn());
        return payoutByKey(p.idempotencyKey()).orElseThrow().view();
    }

    @Transactional
    public SupplierPaymentView recordSupplierPayment(UUID partnerId, NewSupplierPayment p) {
        var previous = jdbc.query("select partner_id from supplier_payments where idempotency_key = ?", (rs, i) -> (UUID) rs.getObject(1), p.idempotencyKey());
        if (!previous.isEmpty()) {
            if (!previous.get(0).equals(partnerId)) throw new ConflictException("This payment key was already used for another partner");
            return supplierPaymentByKey(p.idempotencyKey());
        }
        support.lock(partnerId);
        RouteStatus status = support.routeStatus(partnerId, Route.SUPPLIER).orElse(null);
        if (status != RouteStatus.APPROVED && status != RouteStatus.SUSPENDED)
            throw new IllegalArgumentException("Supplier payments need an approved supplier route");
        UUID bookingId = null;
        if (p.bookingReference() != null && !p.bookingReference().isBlank())
            bookingId = jdbc.query("select id from bookings where reference = ?", (rs, i) -> (UUID) rs.getObject(1), p.bookingReference().trim())
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        jdbc.update("""
            insert into supplier_payments (id, partner_id, amount_cents, paid_on, method, payment_reference, booking_id, description, idempotency_key, recorded_by, recorded_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), partnerId, p.amountCents(), p.paidOn(), p.method().trim(), p.paymentReference().trim(),
            bookingId, p.description().trim(), p.idempotencyKey(), PartnerSupport.actor(), OffsetDateTime.now());
        audit.record("SUPPLIER_PAYMENT_RECORDED", partnerId.toString(), PartnerSupport.rand(p.amountCents()) + " ref " + p.paymentReference().trim()
            + (p.bookingReference() == null || p.bookingReference().isBlank() ? "" : " booking " + p.bookingReference().trim()));
        return supplierPaymentByKey(p.idempotencyKey());
    }

    private SupplierPaymentView supplierPaymentByKey(UUID key) {
        return jdbc.queryForObject("""
            select s.id, s.amount_cents, s.paid_on, s.method, s.payment_reference, b.reference, s.description, s.recorded_by, s.recorded_at
            from supplier_payments s left join bookings b on b.id = s.booking_id where s.idempotency_key = ?""",
            (rs, i) -> new SupplierPaymentView((UUID) rs.getObject(1), rs.getInt(2), rs.getObject(3, LocalDate.class), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), PartnerSupport.time(rs.getTimestamp(9))), key);
    }

    record StoredPayout(UUID partnerId, PayoutView view) {}
    private Optional<StoredPayout> payoutByKey(UUID key) {
        return jdbc.query("select * from partner_payouts where idempotency_key = ?", (rs, i) -> new StoredPayout((UUID) rs.getObject("partner_id"), payout(rs)), key)
            .stream().findFirst();
    }
    private static PayoutView payout(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PayoutView((UUID) rs.getObject("id"), rs.getInt("amount_cents"), rs.getObject("paid_on", LocalDate.class), rs.getString("method"),
            rs.getString("payment_reference"), rs.getString("notes"), rs.getString("recorded_by"), PartnerSupport.time(rs.getTimestamp("recorded_at")));
    }
    @Transactional(readOnly = true)
    public List<PayoutView> payouts(UUID partnerId) {
        return jdbc.query("select * from partner_payouts where partner_id = ? order by paid_on desc, recorded_at desc", (rs, i) -> payout(rs), partnerId);
    }
    @Transactional(readOnly = true)
    public long unpaid(UUID partnerId) {
        Long v = jdbc.queryForObject("select coalesce(sum(amount_cents), 0) from partner_commissions where partner_id = ? and payout_id is null", Long.class, partnerId);
        return v == null ? 0 : v;
    }
    @Transactional(readOnly = true)
    public List<SupplierPaymentView> supplierPayments(UUID partnerId) {
        return jdbc.query("""
            select s.id, s.amount_cents, s.paid_on, s.method, s.payment_reference, b.reference, s.description, s.recorded_by, s.recorded_at
            from supplier_payments s left join bookings b on b.id = s.booking_id where s.partner_id = ? order by s.recorded_at desc""",
            (rs, i) -> new SupplierPaymentView((UUID) rs.getObject(1), rs.getInt(2), rs.getObject(3, LocalDate.class), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), PartnerSupport.time(rs.getTimestamp(9))), partnerId);
    }
}
