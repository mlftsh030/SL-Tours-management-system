const whatsappNumber = "27670127776";

export const contact = {
  phoneDisplay: "+27 67 012 7776",
  phoneHref: "tel:+27670127776",
  email: "tours.st@outlook.com",
  serviceArea: "Cape Town & Western Cape",
  whatsapp: `https://wa.me/${whatsappNumber}`,
  facebook: "https://www.facebook.com/sl.tours.ct",
  instagram: "https://www.instagram.com/sl_tours/",
  tiktok: "https://www.tiktok.com/@sl_tours",
  /** SL Tours' Google Maps listing, where reviews can be read; googleReview opens the form to write one. */
  googleProfile: "https://g.page/r/CRyeNopih97yEAE",
  googleReview: "https://g.page/r/CRyeNopih97yEAE/review",
  tripadvisorReview:
    "https://www.tripadvisor.com/UserReviewEdit-g312659-d33018429-SL_Tours-Cape_Town_Central_Western_Cape.html"
};

export function whatsappReserveUrl(tourName: string) {
  const message = `Hello SL Tours, I would like to reserve ${tourName}. Please share the next available date, what is included, and pickup details.`;
  return `${contact.whatsapp}?text=${encodeURIComponent(message)}`;
}

export const address = "100 Example Street, Cape Town";

/** Formats a published price with its unit: per person, per trip, or a quote on request. */
export function priceUnit(pricingType: string) {
  return pricingType === "PER_TRIP" ? "per trip" : pricingType === "CUSTOM" ? "" : "per person";
}

/** WhatsApp message for a tour, with whatever the visitor has already filled in. */
export function tourWhatsappUrl(tourTitle: string, details: Array<[string, string | number | null | undefined]> = []) {
  const lines = [`Hello SL Tours, I would like to enquire about: ${tourTitle}.`,
    ...details.filter(([, v]) => v !== null && v !== undefined && String(v).trim() !== "").map(([k, v]) => `${k}: ${v}`)];
  if (details.length === 0) lines.push("Please share availability, what is included and pickup details.");
  return `${contact.whatsapp}?text=${encodeURIComponent(lines.join("\n"))}`;
}
