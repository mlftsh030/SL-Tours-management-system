import { request } from "../../lib/api";
import type { PageResult } from "../../types/api";

export interface CustomerSummary {
  id: string; name: string; email: string | null; phone: string | null; hasAccount: boolean; lastLoginAt: string | null; activeSessions: number;
  enquiries: number; openEnquiries: number; bookings: number; upcomingBookings: number; invoices: number; outstandingCents: number;
  lastActivityAt: string | null; demoData: boolean;
}
export interface CustomerDetail {
  summary: CustomerSummary; version: number; country: string | null; notes: string | null; accountEmail: string | null; accountVerified: boolean;
  mergedIntoId: string | null;
  enquiries: Array<{ reference: string; status: string; type: string; source: string; tourTitle: string | null; requestedDate: string | null; createdAt: string; updatedAt: string }>;
  bookings: Array<{ reference: string; title: string; status: string; travelDate: string; guests: number; totalCents: number; paidCents: number; assigned: boolean; sourceQuotation: string | null }>;
  documents: Array<{ id: string; kind: string; status: string; displayNumber: string; issueDate: string | null; dueDate: string | null; currency: string; totalCents: number; paidCents: number; paymentStatus: string; bookingReference: string | null }>;
  payments: Array<{ id: string; documentId: string; invoiceNumber: string | null; amountCents: number; paidOn: string; method: string; status: string; receiptNumber: number | null; recordedAt: string }>;
  merges: Array<{ otherId: string; otherName: string; otherEmail: string | null; direction: "MERGED_IN" | "MERGED_INTO"; reason: string; mergedBy: string; mergedAt: string }>;
}
export interface DuplicateGroup { reason: "SAME_PHONE" | "SAME_NAME"; value: string; customers: CustomerSummary[] }

const base = "/api/v1/admin/customers";
export const searchCustomers = (q: string, page: number) =>
  request<PageResult<CustomerSummary>>(`${base}?page=${page}&size=20${q.trim() ? `&q=${encodeURIComponent(q.trim())}` : ""}`);
export const getCustomer = (id: string) => request<CustomerDetail>(`${base}/${id}`);
export const updateCustomer = (id: string, body: { version: number; name: string; phone: string | null; country: string | null; notes: string | null }) =>
  request<CustomerDetail>(`${base}/${id}`, { method: "PUT", body: JSON.stringify(body) });
export const listDuplicates = () => request<DuplicateGroup[]>(`${base}/duplicates`);
export const mergeCustomers = (body: { survivorId: string; mergedId: string; survivorVersion: number; mergedVersion: number; reason: string }) =>
  request<CustomerDetail>(`${base}/merge`, { method: "POST", body: JSON.stringify(body) });
