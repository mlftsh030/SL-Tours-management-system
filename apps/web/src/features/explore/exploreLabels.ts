import type { ActivityArea, ActivityCategory, ActivityRegion, ActivityTheme, CostKind, OperatingStatus, PriceStatus, Residency } from "../../types/api";

export const categoryLabels: Record<ActivityCategory, string> = {
  NATURE: "Nature & views", WILDLIFE: "Wildlife", MUSEUM_HISTORY: "History & museums", ART_CULTURE: "Art & culture", WINE: "Wine", FOOD_MARKET: "Food & markets",
  BEACH: "Beaches", HIKING: "Hiking", SCENIC_DRIVE: "Scenic drives", FAMILY_LEISURE: "Family fun", GO_KARTING: "Go-karting", ICE_SKATING: "Ice skating",
  CINEMA: "Movies", ADVENTURE: "Adventure"
};
export const areaLabels: Record<ActivityArea, string> = {
  CITY_CENTRE: "City centre", WATERFRONT: "V&A Waterfront", TABLE_MOUNTAIN: "Table Mountain", ATLANTIC_SEABOARD: "Atlantic Seaboard",
  SOUTHERN_SUBURBS: "Southern Suburbs", CONSTANTIA: "Constantia", HOUT_BAY_CHAPMANS: "Hout Bay & Chapman's Peak", FALSE_BAY: "False Bay",
  CAPE_POINT: "Cape Point", NORTHERN_SUBURBS: "Northern Suburbs"
};
/** Regions in display order; Cape Town comes first. */
export const regionLabels: Record<ActivityRegion, string> = {
  CAPE_TOWN: "Cape Town", CAPE_WINELANDS: "Cape Winelands", OVERBERG: "Overberg", WEST_COAST: "West Coast",
  GARDEN_ROUTE_KLEIN_KAROO: "Garden Route & Klein Karoo", CAPE_KAROO: "Cape Karoo"
};
export const regionOrder = Object.keys(regionLabels) as ActivityRegion[];
export const themeLabels: Record<ActivityTheme, string> = {
  ART: "Art & galleries", HERITAGE: "History & heritage", HIKING: "Hiking & walks", ADVENTURE: "Adventure", OCEAN: "Ocean & beaches",
  WILDLIFE: "Wildlife & nature", FOOD: "Food & markets", WINE: "Wine", WORKSHOPS: "Workshops & classes", FAMILY: "Family",
  SCENIC: "Scenic spots & drives", CYCLING_RIDING: "Cycling & riding", WELLNESS: "Wellness", EVENTS: "Events & performances", ESCAPES: "Weekend escapes"
};
export const operatingLabels: Record<OperatingStatus, string | null> = {
  OPEN: null, PARTLY_CLOSED: "Partly closed", SEASONAL: "Seasonal", CLOSED: "Closed", UNKNOWN: "Check if open"
};
export const residencyLabels: Record<Exclude<Residency, "ANY">, string> = {
  SA_RESIDENT: "South African citizens and residents", SADC: "SADC nationals", AFRICAN: "Other African nationals", INTERNATIONAL: "International visitors"
};
export const priceStatusText: Record<PriceStatus, string> = {
  VERIFIED: "Prices checked on the official website", VARIES_AT_BOOKING: "Price set when you book", NOT_PUBLISHED: "Prices not published by the operator",
  FREE: "Free", NOT_RECORDED: "Price not recorded by SL Tours"
};
export const costKindLabels: Record<CostKind, string> = {
  VERIFIED: "Published price", ASSUMPTION: "Assumption", ESTIMATE: "Estimate", UNKNOWN: "Not verified", EXCLUDED: "Not included"
};
const DAY_NAMES: Record<string, string> = { MON: "Mon", TUE: "Tue", WED: "Wed", THU: "Thu", FRI: "Fri", SAT: "Sat", SUN: "Sun" };
export function dayList(days: string | null) {
  if (!days) return "Every day";
  const list = days.split(",");
  if (list.join(",") === "MON,TUE,WED,THU,FRI") return "Weekdays";
  if (list.join(",") === "SAT,SUN") return "Weekends";
  return list.map(d => DAY_NAMES[d] ?? d).join(", ");
}
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
export const monthDay = (md: string) => `${Number(md.slice(3))} ${MONTHS[Number(md.slice(0, 2)) - 1]}`;
export const hhmm = (t: string | null) => t ? t.slice(0, 5) : "";
export const minutesLabel = (m: number) => m < 60 ? `${m} min` : `${Math.floor(m / 60)} h${m % 60 ? ` ${m % 60} min` : ""}`;
export const shortDate = (iso: string | null) => iso ? new Date(`${iso}T00:00:00`).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "";
