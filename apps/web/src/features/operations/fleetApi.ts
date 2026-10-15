import { request } from "../../lib/api";
import type {
  AdminAssignment, AdminPrivateTrip, DriverDetail, DriverSummary, InspectionComparison, InspectionView, PendingReview, PricingView,
  ResourceOptions, SaveVehicle, SiteImage, VehicleDetail, VehicleSummary
} from "../../types/api";

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });
const form = (data: FormData): RequestInit => ({ method: "POST", body: data });

export const listVehicles = () => request<VehicleSummary[]>("/api/v1/admin/vehicles");
export const getVehicle = (id: string) => request<VehicleDetail>(`/api/v1/admin/vehicles/${id}`);
export const saveVehicle = (vehicle: SaveVehicle, id?: string) =>
  request<VehicleDetail>(`/api/v1/admin/vehicles${id ? `/${id}` : ""}`, json(id ? "PUT" : "POST", vehicle));
export const uploadVehicleImage = (id: string, file: File, altText: string) => {
  const data = new FormData(); data.append("file", file); data.append("altText", altText);
  return request<VehicleDetail>(`/api/v1/admin/vehicles/${id}/images`, form(data));
};
export const removeVehicleImage = (id: string, imageId: string) => request<VehicleDetail>(`/api/v1/admin/vehicles/${id}/images/${imageId}`, { method: "DELETE" });
export const openRestriction = (id: string, type: string, reason: string) =>
  request<VehicleDetail>(`/api/v1/admin/vehicles/${id}/restrictions`, json("POST", { type, reason }));
export const clearRestriction = (id: string, restrictionId: string, version: number, note: string) =>
  request<VehicleDetail>(`/api/v1/admin/vehicles/${id}/restrictions/${restrictionId}/clear`, json("POST", { version, note }));

export const listDrivers = () => request<DriverSummary[]>("/api/v1/admin/drivers");
export const getDriver = (id: string) => request<DriverDetail>(`/api/v1/admin/drivers/${id}`);
export const createDriver = (body: unknown) => request<DriverDetail>("/api/v1/admin/drivers", json("POST", body));
export const updateDriver = (id: string, body: unknown) => request<DriverDetail>(`/api/v1/admin/drivers/${id}`, json("PUT", body));
export const createDriverAccount = (id: string, email: string, initialPassword: string) =>
  request<DriverDetail>(`/api/v1/admin/drivers/${id}/account`, json("POST", { email, initialPassword }));
export const setDriverAccountEnabled = (id: string, enabled: boolean) => request<DriverDetail>(`/api/v1/admin/drivers/${id}/account`, json("PATCH", { enabled }));
export const setDriverVerification = (id: string, version: number, status: string, notes: string) =>
  request<DriverDetail>(`/api/v1/admin/drivers/${id}/verification`, json("PATCH", { version, status, notes }));
export const uploadDriverDocument = (id: string, type: string, expiresOn: string, file: File) => {
  const data = new FormData(); data.append("file", file); data.append("type", type); if (expiresOn) data.append("expiresOn", expiresOn);
  return request<DriverDetail>(`/api/v1/admin/drivers/${id}/documents`, form(data));
};
export const reviewDriverDocument = (id: string, documentId: string, version: number, status: string, notes: string) =>
  request<DriverDetail>(`/api/v1/admin/drivers/${id}/documents/${documentId}`, json("PATCH", { version, status, notes }));

export const markVehicleGenuine = (id: string) => request<VehicleDetail>(`/api/v1/admin/vehicles/${id}/genuine`, { method: "POST" });
export const markDriverGenuine = (id: string) => request<DriverDetail>(`/api/v1/admin/drivers/${id}/genuine`, { method: "POST" });
export const assignmentsNeedingAttention = () => request<AdminAssignment[]>("/api/v1/admin/assignments/attention");
export const listAssignments = (scope: "active" | "recent") => request<AdminAssignment[]>(`/api/v1/admin/assignments?scope=${scope}`);
export const resourceOptions = (startsAt: string, endsAt: string, passengers: number) =>
  request<ResourceOptions>(`/api/v1/admin/assignments/options?${new URLSearchParams({ startsAt, endsAt, passengers: String(passengers) })}`);
export const createAssignment = (body: { bookingReference: string; vehicleId: string; driverId: string; startsAt: string; endsAt: string }) =>
  request<AdminAssignment>("/api/v1/admin/assignments", json("POST", body));
export const changeAssignment = (id: string, version: number, vehicleId: string, driverId: string) =>
  request<AdminAssignment>(`/api/v1/admin/assignments/${id}`, json("PUT", { version, vehicleId, driverId }));
export const cancelAssignment = (id: string, version: number, reason: string) =>
  request<AdminAssignment>(`/api/v1/admin/assignments/${id}/cancel`, json("POST", { version, reason }));
export const compareInspections = (assignmentId: string) => request<InspectionComparison>(`/api/v1/admin/assignments/${assignmentId}/inspections`);
export const pendingInspections = () => request<PendingReview[]>("/api/v1/admin/inspections/pending");
export const reviewInspection = (id: string, decision: "approve" | "reject", version: number, notes: string) =>
  request<InspectionView>(`/api/v1/admin/inspections/${id}/${decision}`, json("POST", { version, notes }));

export const getPrivateTrip = (reference: string) => request<AdminPrivateTrip>(`/api/v1/admin/private-trips/${encodeURIComponent(reference)}`);
export const confirmPrivateTrip = (reference: string, version: number, vehicleId: string, driverId: string) =>
  request<AdminPrivateTrip>(`/api/v1/admin/private-trips/${encodeURIComponent(reference)}/confirm`, json("POST", { version, vehicleId, driverId }));
export const convertEnquiryToTrip = (reference: string, body: unknown) =>
  request<AdminPrivateTrip>(`/api/v1/admin/enquiries/${encodeURIComponent(reference)}/convert-private-trip`, json("POST", body));

export const getPricing = () => request<PricingView>("/api/v1/admin/pricing");
export const savePricingSettings = (body: unknown) => request<PricingView>("/api/v1/admin/pricing/settings", json("PUT", body));
export const saveRate = (category: string, body: unknown) => request<PricingView>(`/api/v1/admin/pricing/rates/${category}`, json("PUT", body));
export const saveFee = (body: unknown, id?: string) => request<PricingView>(`/api/v1/admin/pricing/fees${id ? `/${id}` : ""}`, json(id ? "PUT" : "POST", body));

export const listSiteImages = (slot: string) => request<SiteImage[]>(`/api/v1/admin/site-images?slot=${slot}`);
export const uploadSiteImage = (slot: string, file: File, altText: string) => {
  const data = new FormData(); data.append("file", file); data.append("slot", slot); data.append("altText", altText);
  return request<SiteImage>("/api/v1/admin/site-images", form(data));
};
export const updateSiteImage = (id: string, body: unknown) => request<SiteImage>(`/api/v1/admin/site-images/${id}`, json("PUT", body));
export const deleteSiteImage = (id: string) => request<void>(`/api/v1/admin/site-images/${id}`, { method: "DELETE" });
