import { request } from "../../lib/api";
import type { AdminTour, EnquiryDetail, EnquirySummary, OperationsBooking, PageResult, SaveTour } from "../../types/api";
export const listAdminTours = (page: number) => request<PageResult<AdminTour>>(`/api/v1/admin/tours?page=${page}&size=20`);
export const saveAdminTour = (tour: SaveTour, id?: string) => request<AdminTour>(`/api/v1/admin/tours${id ? `/${id}` : ""}`, {
  method: id ? "PUT" : "POST", body: JSON.stringify(tour)
});
export const updateBookingStatus = (booking: OperationsBooking, status: string) => request<OperationsBooking>(`/api/v1/admin/bookings/${encodeURIComponent(booking.reference)}/status`, {
  method: "PATCH", body: JSON.stringify({ status, version: booking.version })
});
export interface EnquiryFilters { q: string; status: string; source: string; }
export const listEnquiries = (page: number, filters: EnquiryFilters) => {
  const params = new URLSearchParams({ page: String(page), size: "20" });
  if (filters.q.trim()) params.set("q", filters.q.trim());
  if (filters.status) params.set("status", filters.status);
  if (filters.source) params.set("source", filters.source);
  return request<PageResult<EnquirySummary>>(`/api/v1/admin/enquiries?${params}`);
};
const enquiryPath = (reference: string) => `/api/v1/admin/enquiries/${encodeURIComponent(reference)}`;
export const getEnquiry = (reference: string) => request<EnquiryDetail>(enquiryPath(reference));
export const updateEnquiryStatus = (enquiry: EnquiryDetail, status: string) =>
  request<EnquiryDetail>(enquiryPath(enquiry.reference), { method: "PATCH", body: JSON.stringify({ status, version: enquiry.version }) });
export const addEnquiryNote = (reference: string, body: string) =>
  request<EnquiryDetail>(`${enquiryPath(reference)}/notes`, { method: "POST", body: JSON.stringify({ body }) });
export const convertEnquiry = (enquiry: EnquiryDetail, payload: { tourSlug: string; travelDate: string; guests: number; pickupLocation?: string; notes?: string }) =>
  request<{ enquiryReference: string; bookingReference: string }>(`${enquiryPath(enquiry.reference)}/convert`, {
    method: "POST", body: JSON.stringify({ ...payload, version: enquiry.version })
  });
