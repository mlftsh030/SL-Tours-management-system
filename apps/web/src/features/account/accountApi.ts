import { capeTown } from "../../lib/dates";
import { request } from "../../lib/api";
import type { BookingResponse, PageResult } from "../../types/api";
import type { CustomerDocument } from "../billing/billingTypes";
import type { LinkedRecords } from "../auth/authApi";

export interface Profile { name: string; email: string; phone: string | null; emailVerified: boolean; memberSince: string }
export type EnquiryStatus = "NEW" | "IN_PROGRESS" | "AWAITING_CUSTOMER" | "CONVERTED" | "CLOSED";
export interface CustomerEnquiry {
  reference: string; type: "GENERAL" | "TOUR" | "PRIVATE_TRIP"; source: "CONTACT_FORM" | "TRIP_PLANNER" | "PRIVATE_TRIP_QUOTE" | "ASSISTANT";
  status: EnquiryStatus; tourTitle: string | null; requestedDate: string | null; passengers: number | null; message: string;
  bookingReference: string | null; createdAt: string;
}

export const getProfile = () => request<Profile>("/api/v1/account/profile");
export const updateProfile = (body: { name: string; phone: string | null }) =>
  request<Profile>("/api/v1/account/profile", { method: "PUT", body: JSON.stringify(body) });
export const listEnquiries = () => request<CustomerEnquiry[]>("/api/v1/account/enquiries");
export const listBookings = (page: number) => request<PageResult<BookingResponse>>(`/api/v1/account/bookings?page=${page}&size=10`);
export const listDocuments = () => request<CustomerDocument[]>("/api/v1/account/documents");
export const documentPdfUrl = (id: string) => `/api/v1/account/documents/${id}/pdf`;
export function uploadProof(id: string, file: File) {
  const body = new FormData(); body.append("file", file);
  return request<CustomerDocument>(`/api/v1/account/documents/${id}/proof`, { method: "POST", body });
}
export const linkGuestRecords = () => request<LinkedRecords>("/api/v1/account/guest-records", { method: "POST" });

export const enquiryStatusLabels: Record<EnquiryStatus, string> = {
  NEW: "Received", IN_PROGRESS: "In progress", AWAITING_CUSTOMER: "Waiting for your reply", CONVERTED: "Booked", CLOSED: "Closed"
};
export const enquiryTypeLabels: Record<CustomerEnquiry["type"], string> = { GENERAL: "General enquiry", TOUR: "Tour enquiry", PRIVATE_TRIP: "Private trip quote" };
export const bookingStatusLabels: Record<string, string> = {
  ENQUIRY: "Requested", QUOTE_PENDING: "Quote being prepared", QUOTE_SENT: "Quote sent", AWAITING_PAYMENT: "Awaiting payment", CONFIRMED: "Confirmed",
  GUIDE_ASSIGNED: "Guide assigned", READY: "Ready", IN_PROGRESS: "In progress", COMPLETED: "Completed", CANCELLED: "Cancelled", REFUNDED: "Refunded"
};
export const formatDateTime = (iso: string) => capeTown(iso, { day: "numeric", month: "short", year: "numeric" });
