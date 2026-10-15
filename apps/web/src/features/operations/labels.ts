import { capeTown } from "../../lib/dates";
/** Turns API enum values such as AWAITING_CUSTOMER into readable labels. */
export const humanize = (value: string | null | undefined) =>
  value ? value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, " ") : "—";
export const enquiryStatusLabels: Record<string, string> = {
  NEW: "New", IN_PROGRESS: "In progress", AWAITING_CUSTOMER: "Awaiting customer", CONVERTED: "Converted to booking", CLOSED: "Closed"
};
export const enquirySourceLabels: Record<string, string> = {
  CONTACT_FORM: "Contact form", TRIP_PLANNER: "Trip planner", PRIVATE_TRIP_QUOTE: "Private trip quote", ASSISTANT: "Planning assistant"
};
export const dateTime = (value: string | null | undefined) => value ? capeTown(value, { dateStyle: "medium", timeStyle: "short" }) : "—";
export const categoryLabels: Record<string, string> = {
  SEDAN: "Sedan", SUV: "SUV", VAN: "Van", LUXURY_VAN: "Luxury van", MINIBUS: "Minibus", COACH: "Coach"
};
export const assignmentStatusLabels: Record<string, string> = {
  SCHEDULED: "Scheduled", AWAITING_RELEASE: "Awaiting release", RELEASED: "Vehicle out", RETURNED: "Returned", CLOSED: "Closed", CANCELLED: "Cancelled"
};
export const fuelLabels: Record<string, string> = { EMPTY: "Empty", QUARTER: "¼", HALF: "½", THREE_QUARTERS: "¾", FULL: "Full" };
/** Converts a rand amount typed by an admin into integer cents without floating-point error. */
export function randToCents(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  if (!/^\d+(?:\.\d{1,2})?$/.test(trimmed)) throw new Error("Enter an amount in rand with up to two decimal places");
  const [rands, cents = ""] = trimmed.split(".");
  return Number(rands) * 100 + Number(cents.padEnd(2, "0"));
}
export const centsToRand = (cents: number | null | undefined) => cents == null ? "" : (cents / 100).toFixed(2);
export const hours = (minutes: number) => `${minutes / 60} ${minutes === 60 ? "hour" : "hours"}`;
