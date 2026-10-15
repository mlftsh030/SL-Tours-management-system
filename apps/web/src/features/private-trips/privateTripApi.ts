import { withReferral } from "../../lib/referral";
import { request } from "../../lib/api";
import type { EnquiryReceipt, PricingDisclosures, PrivateTripReceipt, PrivateTripSearch, VehicleCard, VehicleOption } from "../../types/api";

export interface TripQuery { date: string; time: string; durationMinutes: number; passengers: number; }
export const listFleet = () => request<{ vehicles: VehicleCard[]; disclosures: PricingDisclosures }>("/api/v1/private-trips/vehicles");
export const searchPrivateTrips = (q: TripQuery) =>
  request<PrivateTripSearch>(`/api/v1/private-trips/availability?${new URLSearchParams({ date: q.date, time: q.time,
    durationMinutes: String(q.durationMinutes), passengers: String(q.passengers) })}`);
export const requestPrivateTrip = (body: object) => request<PrivateTripReceipt>("/api/v1/private-trips/requests", { method: "POST", body: JSON.stringify(withReferral(body)) });
export const requestPrivateTripQuote = (body: object) => request<EnquiryReceipt>("/api/v1/private-trips/quote-requests", { method: "POST", body: JSON.stringify(withReferral(body)) });
export const listVehicleOptions = () => request<VehicleOption[]>("/api/v1/private-trips/vehicle-options");
