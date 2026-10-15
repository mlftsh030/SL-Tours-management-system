import { request } from "../../lib/api";
import type { PageResult } from "../../types/api";
import type {
  BillingSettings, BookingPrefill, Capabilities, Channel, CustomerSuggestion, DocumentContent, DocumentDetail, DocumentKind,
  DocumentStatus, DocumentSummary, PaymentMethod, PaymentStatus, PreviewResult, ShareAction, ShareResult
} from "./billingTypes";

const base = "/api/v1/admin/billing";
const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export interface DocumentFilters { kind: DocumentKind | ""; status: DocumentStatus | ""; paymentStatus: PaymentStatus | ""; q: string; from: string; to: string; overdue: boolean; }

export function listDocuments(page: number, f: DocumentFilters) {
  const params = new URLSearchParams({ page: String(page), size: "20" });
  if (f.kind) params.set("kind", f.kind);
  if (f.status) params.set("status", f.status);
  if (f.paymentStatus) params.set("paymentStatus", f.paymentStatus);
  if (f.q.trim()) params.set("q", f.q.trim());
  if (f.from) params.set("from", f.from);
  if (f.to) params.set("to", f.to);
  if (f.overdue) params.set("overdue", "true");
  return request<PageResult<DocumentSummary>>(`${base}/documents?${params}`);
}
export const getDocument = (id: string) => request<DocumentDetail>(`${base}/documents/${id}`);
export const createDocument = (kind: DocumentKind, idempotencyKey: string, content: DocumentContent, bookingReference: string | null, internalNotes: string | null,
  enquiryReference: string | null = null) =>
  request<DocumentDetail>(`${base}/documents`, json("POST", { kind, idempotencyKey, content, bookingReference, internalNotes, enquiryReference }));
export const updateDocument = (id: string, version: number, content: DocumentContent, bookingReference: string | null, internalNotes: string | null) =>
  request<DocumentDetail>(`${base}/documents/${id}`, json("PUT", { version, content, bookingReference, internalNotes }));
export const previewDocument = (kind: DocumentKind, content: DocumentContent, id?: string) =>
  request<PreviewResult>(`${base}/preview`, json("POST", { kind, content, id }));
export const issueDocument = (id: string, version: number) => request<DocumentDetail>(`${base}/documents/${id}/issue`, json("POST", { version }));
export const voidDocument = (id: string, version: number, reason: string) => request<DocumentDetail>(`${base}/documents/${id}/void`, json("POST", { version, reason }));
export const reviseDocument = (id: string) => request<DocumentDetail>(`${base}/documents/${id}/revise`, { method: "POST" });
export const duplicateDocument = (id: string) => request<DocumentDetail>(`${base}/documents/${id}/duplicate`, { method: "POST" });
export const decideQuotation = (id: string, version: number, decision: "ACCEPTED" | "DECLINED") =>
  request<DocumentDetail>(`${base}/documents/${id}/decision`, json("POST", { version, decision }));
export const convertQuotation = (id: string) => request<{ invoiceId: string; created: boolean }>(`${base}/documents/${id}/convert`, { method: "POST" });

export const recordPayment = (id: string, body: { idempotencyKey: string; amountCents: number; paidOn: string; method: PaymentMethod; reference: string; notes: string }) =>
  request<DocumentDetail>(`${base}/documents/${id}/payments`, json("POST", body));
export function attachProof(paymentId: string, file: File, label: string) {
  const data = new FormData(); data.append("file", file); if (label) data.append("label", label);
  return request<DocumentDetail>(`${base}/payments/${paymentId}/proofs`, { method: "POST", body: data });
}
export const confirmPayment = (paymentId: string, version: number) => request<DocumentDetail>(`${base}/payments/${paymentId}/confirm`, json("POST", { version }));
export const rejectPayment = (paymentId: string, version: number, reason: string) =>
  request<DocumentDetail>(`${base}/payments/${paymentId}/reject`, json("POST", { version, reason }));

export const shareDocument = (id: string, channel: Channel, expiresInDays: number, paymentId?: string) =>
  request<ShareResult>(`${base}/documents/${id}/share`, json("POST", { channel, expiresInDays, paymentId }));
export const recordShareAction = (id: string, action: ShareAction, channel?: Channel, paymentId?: string) =>
  request<void>(`${base}/documents/${id}/share-events`, json("POST", { action, channel, paymentId }));
export const revokeLink = (id: string, linkId: string) => request<void>(`${base}/documents/${id}/links/${linkId}`, { method: "DELETE" });
export const getCapabilities = () => request<Capabilities>(`${base}/capabilities`);

export const searchCustomers = (q: string) => request<CustomerSuggestion[]>(`${base}/customers?q=${encodeURIComponent(q)}`);
export const prefillFromEnquiry = (reference: string) =>
  request<{ enquiryReference: string; content: DocumentContent; notes: string[] }>(`${base}/enquiries/${encodeURIComponent(reference)}/prefill`);
/** Creates the booking for an accepted quotation, once (retries return the same booking). Administrators only. */
export const bookingFromQuotation = (id: string, body?: { travelDate?: string; guests?: number }) =>
  request<{ bookingId: string; bookingReference: string; created: boolean }>(`${base}/documents/${id}/booking`, json("POST", body ?? {}));
export const reversePayment = (paymentId: string, version: number, reason: string) =>
  request<DocumentDetail>(`${base}/payments/${paymentId}/reverse`, json("POST", { version, reason }));
export const prefillFromBooking = (reference: string) => request<BookingPrefill>(`${base}/bookings/${encodeURIComponent(reference)}/prefill`);

export const getBillingSettings = () => request<BillingSettings>(`${base}/settings`);
export const saveBillingSettings = (body: Omit<BillingSettings, "logoUrl" | "customLogo" | "numbering" | "updatedBy" | "updatedAt">) =>
  request<BillingSettings>(`${base}/settings`, json("PUT", body));
export function uploadLogo(file: File) {
  const data = new FormData(); data.append("file", file);
  return request<BillingSettings>(`${base}/settings/logo`, { method: "POST", body: data });
}
export const restoreOriginalLogo = () => request<BillingSettings>(`${base}/settings/logo`, { method: "DELETE" });
export const saveNumbering = (series: string, nextNumber: number, confirmed: boolean) =>
  request<BillingSettings>(`${base}/settings/numbering/${series}`, json("PUT", { nextNumber, confirmed }));

/** URLs for files. They are opened or downloaded by the browser with the staff session cookie. */
export const documentFileUrl = (id: string, type: "pdf" | "docx", download = false) =>
  `${base}/documents/${id}/${type}${type === "pdf" && download ? "?download=true" : ""}`;
export const receiptFileUrl = (paymentId: string, type: "pdf" | "docx", download = false) =>
  `${base}/payments/${paymentId}/receipt.${type}${type === "pdf" && download ? "?download=true" : ""}`;

/** Fetches a generated PDF so it can be handed to the device's share sheet. */
export async function fetchPdf(url: string, filename: string): Promise<File> {
  const response = await fetch(url, { credentials: "include", cache: "no-store" });
  if (!response.ok) throw new Error(`The PDF could not be loaded (${response.status})`);
  return new File([await response.blob()], filename, { type: "application/pdf" });
}

