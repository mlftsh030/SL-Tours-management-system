import { request } from "../../lib/api";
import type { PortalProfile, PortalReferral, PortalStatement, ProposalInput, ProposalView } from "./partnerTypes";

export const applyAsPartner = (body: unknown) =>
  request<{ reference: string; message: string; duplicate: boolean }>("/api/v1/partner-applications", { method: "POST", body: JSON.stringify(body) });
export const portal = {
  me: () => request<PortalProfile>("/api/v1/partner/me"),
  referrals: () => request<PortalReferral[]>("/api/v1/partner/referrals"),
  statement: () => request<PortalStatement>("/api/v1/partner/statement"),
  proposals: () => request<ProposalView[]>("/api/v1/partner/proposals"),
  submit: (p: ProposalInput) => request<ProposalView>("/api/v1/partner/proposals", { method: "POST", body: JSON.stringify(p) }),
  update: (p: ProposalView, input: ProposalInput) =>
    request<ProposalView>(`/api/v1/partner/proposals/${p.id}?version=${p.version}`, { method: "PUT", body: JSON.stringify(input) }),
  withdraw: (p: ProposalView) => request<ProposalView>(`/api/v1/partner/proposals/${p.id}/withdraw?version=${p.version}`, { method: "POST" })
};
