import type { ActivityCard, VehicleOption } from "../types/api";

/** A vehicle option as the API returns it. */
export const vehicleOption = (slug: string, name: string, extra: Partial<VehicleOption> = {}): VehicleOption => ({
  slug, name, typeLabel: "People carrier", category: "VAN", passengerCapacity: 8, capacityConfirmed: true, capacityNote: "Within the manufacturer's seating.",
  description: `${name} with a professional driver.`, imageUrl: `/assets/vehicles/${slug}.jpg`, imageThumbnailUrl: `/assets/vehicles/${slug}-480.jpg`,
  imageAlt: `${name} parked at a roadside`, illustrativeImage: true, imageCredit: "A Photographer", imageSourcePage: "https://commons.wikimedia.org/wiki/File:Example.jpg",
  imageLicence: "CC BY-SA 4.0", imageLicenceUrl: "https://creativecommons.org/licenses/by-sa/4.0", checkedOn: "2026-10-04", ...extra
});

/** A Things to Do card as the API returns it (no prices). */
export const activityCard = (slug: string, extra: Partial<ActivityCard> = {}): ActivityCard => ({
  slug, title: slug, category: "NATURE", themes: ["SCENIC"], region: "CAPE_TOWN", regionLabel: "Cape Town", town: "Cape Town", area: "WATERFRONT",
  areaLabel: "V&A Waterfront", summary: "Summary", durationMinutes: 90, kind: "ATTRACTION", eventSchedule: null,
  image: { kind: "IMAGE", url: `/assets/things-to-do/${slug}.jpg`, thumbnailUrl: `/assets/things-to-do/${slug}-480.jpg`, width: 960, height: 640,
    altText: `Picture of ${slug}`, caption: null, credit: "A Photographer", sourcePage: "https://commons.wikimedia.org/wiki/File:Example.jpg",
    licence: "CC BY-SA 4.0", licenceUrl: null, depiction: "EXACT" },
  indoor: false, weatherDependent: false, operatingStatus: "OPEN", lastVerifiedOn: "2026-10-04", ...extra
});
