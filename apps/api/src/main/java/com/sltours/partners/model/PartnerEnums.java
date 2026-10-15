package com.sltours.partners.model;

public final class PartnerEnums {
    private PartnerEnums() {}
    public enum BusinessType { HOTEL, GUESTHOUSE, ACCOMMODATION_HOST, CONCIERGE, TRAVEL_AGENT, SAFARI_OPERATOR, ACTIVITY_PROVIDER, LOCAL_GUIDE, OTHER }
    /** REFERRAL: sends customers to SL Tours for commission. SUPPLIER: offers experiences SL Tours may sell. Reviewed separately. */
    public enum Route { REFERRAL, SUPPLIER }
    public enum RouteStatus { PENDING, APPROVED, REJECTED, SUSPENDED }
    public enum Decision { APPROVE, REJECT, SUSPEND, REINSTATE }
    public enum CommissionType { PERCENTAGE, FIXED_PER_BOOKING }
    /** NET_AMOUNT_PAID: payments less refunds, capped at the booking total. BOOKING_TOTAL: the booking's total price. */
    public enum EligibleBasis { NET_AMOUNT_PAID, BOOKING_TOTAL }
    /** PROPORTIONAL: each refund reduces commission in proportion. FULL_REVERSAL: any refund reverses the remaining commission. */
    public enum RefundTreatment { PROPORTIONAL, FULL_REVERSAL }
    public enum CommissionKind { EARNED, REFUND_ADJUSTMENT, REVERSAL }
    /** LINK: the visitor opened a referral link. CODE: typed on a form. ENQUIRY: carried from a converted enquiry. ADMIN: set by an administrator. */
    public enum ReferralSource { LINK, CODE, ENQUIRY, ADMIN }
    public enum ProposalStatus { SUBMITTED, CHANGES_REQUESTED, APPROVED, REJECTED, WITHDRAWN }
    public enum ProposalDecision { APPROVE, REQUEST_CHANGES, REJECT }
    public enum PaymentKind { PAYMENT, REFUND }
}
