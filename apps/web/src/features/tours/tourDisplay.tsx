import { Price } from "../../lib/currency";
import { priceUnit } from "../../lib/contact";
import type { TourSummary } from "../../types/api";

/** Group size as published, e.g. "Minimum 5 guests", "Any group size" or "Private". */
export const groupLabel = (tour: Pick<TourSummary, "groupSizeLabel" | "minimumGuests">) =>
  tour.groupSizeLabel ?? (tour.minimumGuests > 1 ? `Minimum ${tour.minimumGuests} guests` : "Any group size");

/** The published price with its unit: per person, per trip or a quote on request. */
export function TourPrice({ tour }: { tour: Pick<TourSummary, "pricingType" | "basePriceCents"> }) {
  if (tour.pricingType === "CUSTOM") return <>Quote on request</>;
  return <><Price cents={tour.basePriceCents} /> {priceUnit(tour.pricingType)}</>;
}
