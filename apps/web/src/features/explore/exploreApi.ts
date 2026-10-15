import { request } from "../../lib/api";
import type { ActivityCard, ActivityPage, Itinerary } from "../../types/api";

export const listActivities = (params: { category?: string; area?: string; region?: string; theme?: string; q?: string }) => {
  const query = new URLSearchParams(Object.entries(params).filter(([, v]) => v) as Array<[string, string]>);
  return request<ActivityCard[]>(`/api/v1/activities${query.toString() ? `?${query}` : ""}`);
};
export const getActivity = (slug: string) => request<ActivityPage>(`/api/v1/activities/${encodeURIComponent(slug)}`);
export const planDay = (body: unknown) => request<Itinerary>("/api/v1/itineraries", { method: "POST", body: JSON.stringify(body) });
