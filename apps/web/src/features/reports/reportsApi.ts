import { request } from "../../lib/api";

export interface WorkQueues {
  followUps: Array<{ reference: string; customerId: string | null; customerName: string; status: string; type: string; createdAt: string; updatedAt: string; waitingHours: number }>;
  unassignedBookings: Array<{ reference: string; customerId: string | null; customerName: string; title: string; travelDate: string; guests: number; status: string }>;
  openInvoices: Array<{ id: string; number: string; customerId: string | null; customerName: string; dueDate: string | null; currency: string; totalCents: number; paidCents: number; pendingCents: number; overdue: boolean }>;
  paymentsToReview: Array<{ paymentId: string; documentId: string; invoiceNumber: string; customerName: string; amountCents: number; paidOn: string; method: string; reference: string | null; proofFiles: number; recordedAt: string }>;
  customerProofs: Array<{ attachmentId: string; documentId: string; invoiceNumber: string; customerName: string; uploadedBy: string; uploadedAt: string }>;
  duplicateGroups: number;
}
export const getWorkQueues = () => request<WorkQueues>("/api/v1/admin/work-queues");
export type ExportDataset = "customers" | "enquiries" | "bookings" | "invoices" | "payments";
export const exportUrl = (dataset: ExportDataset, from: string, to: string) =>
  `/api/v1/admin/exports/${dataset}?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
