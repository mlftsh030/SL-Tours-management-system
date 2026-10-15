import type { ActivityArea, ActivityCategory } from "../../types/api";

export type Route = "REFERRAL" | "SUPPLIER";
export type RouteStatus = "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED";
export type BusinessType = "HOTEL" | "GUESTHOUSE" | "ACCOMMODATION_HOST" | "CONCIERGE" | "TRAVEL_AGENT" | "SAFARI_OPERATOR" | "ACTIVITY_PROVIDER" | "LOCAL_GUIDE" | "OTHER";
export type CommissionKind = "EARNED" | "REFUND_ADJUSTMENT" | "REVERSAL";
export type ProposalStatus = "SUBMITTED" | "CHANGES_REQUESTED" | "APPROVED" | "REJECTED" | "WITHDRAWN";
export interface RouteView { route: Route; status: RouteStatus; decidedBy: string | null; decidedAt: string | null; reason: string | null }
export interface CommissionLine { id: string; bookingReference: string; kind: CommissionKind; amountCents: number; basisAmountCents: number; calculation: string;
  agreementReference: string; payoutId: string | null; payoutReference: string | null; createdAt: string }
export interface PayoutView { id: string; amountCents: number; paidOn: string; method: string; paymentReference: string; notes: string | null; recordedBy: string; recordedAt: string }
export interface SupplierPaymentView { id: string; amountCents: number; paidOn: string; method: string; paymentReference: string; bookingReference: string | null;
  description: string; recordedBy: string; recordedAt: string }
export interface ProposalInput { title: string; category: ActivityCategory; area: ActivityArea; summary: string; description: string; durationMinutes: number;
  address: string | null; website: string | null; proposedPrices: string; proposedSchedule: string; participationInfo: string; supplierNotes: string | null }
export interface ProposalView extends ProposalInput { id: string; version: number; reference: string; partnerName: string; status: ProposalStatus;
  decisionReason: string | null; decidedAt: string | null; activitySlug: string | null; activityStatus: string | null; createdAt: string; updatedAt: string }
export interface PortalProfile { reference: string; businessName: string; routes: RouteView[]; referralCode: string | null; referralLink: string | null;
  agreements: Array<{ route: Route; agreementReference: string; effectiveFrom: string; effectiveTo: string | null; terms: string }> }
export interface PortalReferral { kind: string; reference: string; product: string; receivedOn: string; status: string; commission: string }
export interface PortalStatement { commissions: CommissionLine[]; payouts: PayoutView[]; unpaidCents: number; supplierPayments: SupplierPaymentView[] }

export const businessTypeLabels: Record<BusinessType, string> = {
  HOTEL: "Hotel", GUESTHOUSE: "Guesthouse or B&B", ACCOMMODATION_HOST: "Accommodation host", CONCIERGE: "Concierge", TRAVEL_AGENT: "Travel agent",
  SAFARI_OPERATOR: "Safari operator", ACTIVITY_PROVIDER: "Activity provider", LOCAL_GUIDE: "Local guide", OTHER: "Other"
};
export const routeLabels: Record<Route, string> = { REFERRAL: "Referral partner", SUPPLIER: "Experience supplier" };
export const routeStatusLabels: Record<RouteStatus, string> = { PENDING: "Under review", APPROVED: "Approved", REJECTED: "Not approved", SUSPENDED: "Suspended" };
export const commissionKindLabels: Record<CommissionKind, string> = { EARNED: "Earned", REFUND_ADJUSTMENT: "Refund adjustment", REVERSAL: "Reversal" };
export const proposalStatusLabels: Record<ProposalStatus, string> = { SUBMITTED: "Awaiting review", CHANGES_REQUESTED: "Changes requested", APPROVED: "Approved",
  REJECTED: "Not approved", WITHDRAWN: "Withdrawn" };
