import type {
  BookingResponse,
  OperationsBooking,
  OperationsDashboard,
  TourDetail,
  TourSummary,
  TripPlanningResponse
} from "../types/api";

import { isBackgroundRequest } from "./activity";
import { withReferral } from "./referral";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiRequestError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

async function csrfToken(): Promise<{ token: string; headerName: string }> {
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/csrf`, { credentials: "include", cache: "no-store" });
  if (!response.ok) throw new Error("Unable to initialize secure request. Refresh and try again.");
  return response.json();
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !headers.has("Content-Type") && !(options.body instanceof FormData)) headers.set("Content-Type", "application/json");
  // Polling without recent interaction must not keep an unattended session signed in.
  if (isBackgroundRequest()) headers.set("X-SLTours-Background", "1");
  if (!["GET", "HEAD", "OPTIONS"].includes(options.method ?? "GET")) {
    const csrf = await csrfToken();
    headers.set(csrf.headerName, csrf.token);
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options, headers, credentials: "include", cache: "no-store"
  });
  if (!response.ok) {
    let message = `Request failed with ${response.status}`;
    try {
      const error = await response.json() as { message?: string; errors?: Record<string, string> };
      // Field errors when there are any; otherwise the message (an empty `errors` object must not blank it out).
      const fields = Object.entries(error.errors ?? {});
      message = fields.length ? fields.map(([field, detail]) => `${field}: ${detail}`).join("; ") : error.message || message;
    } catch { /* Keep proxy failures readable. */ }
    if (response.status === 401 && !path.startsWith("/api/v1/auth/")) window.dispatchEvent(new Event("session-expired"));
    throw new ApiRequestError(message, response.status);
  }
  return response.status === 204 || response.headers.get("content-length") === "0" ? undefined as T : await response.json() as T;
}

export function listTours(featured?: boolean) {
  const suffix = featured ? "?featured=true" : "";
  return request<TourSummary[]>(`/api/v1/tours${suffix}`);
}

export function getTour(slug: string) {
  return request<TourDetail>(`/api/v1/tours/${slug}`);
}

export function createBooking(payload: object) {
  return request<BookingResponse>("/api/v1/bookings", {
    method: "POST",
    body: JSON.stringify(withReferral(payload))
  });
}

export function createTripPlanningRequest(payload: object) {
  return request<TripPlanningResponse>("/api/v1/trip-planning-requests", {
    method: "POST",
    body: JSON.stringify(withReferral(payload))
  });
}

export function getOperationsDashboard() {
  return request<OperationsDashboard>("/api/v1/admin/dashboard");
}

export function listOperationsBookings(page = 0, status = "") {
  return request<import("../types/api").PageResult<OperationsBooking>>(`/api/v1/admin/bookings?page=${page}&size=20${status ? `&status=${encodeURIComponent(status)}` : ""}`);
}
