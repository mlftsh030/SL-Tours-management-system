package com.sltours.partners.service;
import com.sltours.partners.dto.PartnerDtos.*;
import com.sltours.partners.model.PartnerEnums.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a signed-in partner may see: their own profile, terms, referrals and statement. Referrals show the booking or
 * enquiry reference, product, date received and a coarse status only - never customer names, contact details, travel
 * details, chats, driver documents or inspection records. Every query is scoped by the partner resolved from the login.
 */
@Service
public class PartnerPortalService {
    private final JdbcTemplate jdbc;
    private final PartnerAdminService admin;
    private final CommissionService commissions;
    private final PartnerFinanceService finance;
    private final SupplierProposalService proposals;
    private final String baseUrl;
    public PartnerPortalService(JdbcTemplate jdbc, PartnerAdminService admin, CommissionService commissions, PartnerFinanceService finance,
            SupplierProposalService proposals, @Value("${sltours.public-base-url:https://sl-tours.com}") String baseUrl) {
        this.jdbc = jdbc; this.admin = admin; this.commissions = commissions; this.finance = finance; this.proposals = proposals;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** The partner for the signed-in account. Disabled accounts cannot sign in, so this only needs the role and link. */
    public UUID require(Authentication authentication) {
        if (authentication == null) throw new AccessDeniedException("Partner access required");
        return jdbc.query("""
            select p.id from partners p join user_accounts u on u.id = p.account_id where u.email = ? and u.role = 'PARTNER' and u.enabled""",
            (rs, i) -> (UUID) rs.getObject(1), authentication.getName()).stream().findFirst()
            .orElseThrow(() -> new AccessDeniedException("Partner access required"));
    }

    @Transactional(readOnly = true)
    public PortalProfile profile(UUID partnerId) {
        var row = jdbc.queryForObject("select reference, business_name, referral_code from partners where id = ?",
            (rs, i) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, partnerId);
        List<RouteView> routes = admin.routes(partnerId).stream()
            .map(r -> new RouteView(r.route(), r.status(), null, r.decidedAt(), r.status() == RouteStatus.APPROVED ? null : r.reason())).toList();
        boolean referralActive = routes.stream().anyMatch(r -> r.route() == Route.REFERRAL && r.status() == RouteStatus.APPROVED);
        List<PortalAgreement> agreements = admin.agreements(partnerId).stream().map(a -> new PortalAgreement(a.route(), a.agreementReference(),
            a.effectiveFrom(), a.effectiveTo(), terms(a))).toList();
        return new PortalProfile(row[0], row[1], routes, referralActive ? row[2] : null, referralActive ? baseUrl + "/?ref=" + row[2] : null, agreements);
    }

    @Transactional(readOnly = true)
    public List<PortalReferral> referrals(UUID partnerId) {
        List<PortalReferral> list = new ArrayList<>(jdbc.query("""
            select id, reference, booking_type, created_at, status from bookings where referral_partner_id = ? order by created_at desc""",
            (rs, i) -> new PortalReferral("Booking", rs.getString(2), "PRIVATE_TRIP".equals(rs.getString(3)) ? "Private trip" : "Tour",
                PartnerSupport.time(rs.getTimestamp(4)).atZoneSameInstant(PartnerSupport.ZONE).toLocalDate(), bookingStatus(rs.getString(5)),
                commissions.status((UUID) rs.getObject(1))), partnerId));
        list.addAll(jdbc.query("""
            select reference, created_at, status from enquiries where referral_partner_id = ? order by created_at desc""",
            (rs, i) -> new PortalReferral("Enquiry", rs.getString(1), "Enquiry", PartnerSupport.time(rs.getTimestamp(2)).atZoneSameInstant(PartnerSupport.ZONE).toLocalDate(),
                switch (rs.getString(3)) { case "CONVERTED" -> "Became a booking"; case "NEW", "IN_PROGRESS", "AWAITING_CUSTOMER" -> "Received"; default -> "Closed"; },
                "Enquiries do not earn commission; a resulting booking may"), partnerId));
        list.sort(Comparator.comparing(PortalReferral::receivedOn).reversed());
        return list;
    }

    @Transactional(readOnly = true)
    public PortalStatement statement(UUID partnerId) {
        return new PortalStatement(commissions.lines("c.partner_id = ?", partnerId), finance.payouts(partnerId), finance.unpaid(partnerId),
            finance.supplierPayments(partnerId));
    }

    static String bookingStatus(String status) {
        return switch (status) {
            case "ENQUIRY", "QUOTE_PENDING", "QUOTE_SENT" -> "Received";
            case "AWAITING_PAYMENT" -> "Awaiting payment";
            case "CONFIRMED", "GUIDE_ASSIGNED", "READY", "IN_PROGRESS" -> "Confirmed";
            case "COMPLETED" -> "Completed";
            case "CANCELLED" -> "Cancelled";
            case "REFUNDED" -> "Refunded";
            default -> "Received";
        };
    }

    static String terms(AgreementView a) {
        if (a.route() == Route.SUPPLIER) return a.supplierTerms();
        String value = a.commissionType() == CommissionType.PERCENTAGE
            ? new java.math.BigDecimal(a.rateBasisPoints()).movePointLeft(2).stripTrailingZeros().toPlainString() + "% of the "
                + (a.eligibleBasis() == EligibleBasis.NET_AMOUNT_PAID ? "net amount paid" : "booking total")
            : PartnerSupport.rand(a.fixedAmountCents()) + " per booking";
        String types = a.eligibleBookingTypes().replace("PRIVATE_TRIP", "private trips").replace("TOUR", "tours").replace(",", " and ");
        return value + " on completed, fully paid " + types + " made within " + a.attributionWindowDays() + " days of the referral"
            + (a.refundTreatment() == RefundTreatment.PROPORTIONAL ? "; refunds reduce commission proportionally" : "; any refund reverses the commission")
            + (a.minimumEligibleCents() == null ? "" : "; minimum eligible amount " + PartnerSupport.rand(a.minimumEligibleCents()))
            + (a.maximumCommissionCents() == null ? "" : "; at most " + PartnerSupport.rand(a.maximumCommissionCents()) + " per booking");
    }
}
