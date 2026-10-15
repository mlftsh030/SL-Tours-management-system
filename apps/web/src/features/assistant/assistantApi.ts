import { request } from "../../lib/api";
import { withReferral } from "../../lib/referral";
import type { AssistantReply, AssistantStatus } from "../../types/api";

export const assistantStatus = () => request<AssistantStatus>("/api/v1/assistant/status");
export const startConversation = () => request<AssistantReply>("/api/v1/assistant/conversations", { method: "POST" });
export const sendMessage = (id: string, message: string) =>
  request<AssistantReply>(`/api/v1/assistant/conversations/${encodeURIComponent(id)}/messages`, { method: "POST", body: JSON.stringify({ message }) });
export const submitPlan = (id: string, body: { optionId: string; name: string; email: string; phone: string | null; note: string | null; confirmed: boolean; idempotencyKey: string }) =>
  request<AssistantReply>(`/api/v1/assistant/conversations/${encodeURIComponent(id)}/enquiry`, { method: "POST", body: JSON.stringify(withReferral(body)) });
export const endConversation = (id: string) => request<void>(`/api/v1/assistant/conversations/${encodeURIComponent(id)}`, { method: "DELETE" });
