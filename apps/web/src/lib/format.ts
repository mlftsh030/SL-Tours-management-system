export function formatMoney(cents: number) {
  if (cents === 0) {
    return "Custom quote";
  }
  return new Intl.NumberFormat("en-ZA", {
    style: "currency",
    currency: "ZAR",
    maximumFractionDigits: 0
  }).format(cents / 100);
}

export function priceLabel(pricingType: string) {
  if (pricingType === "PER_TRIP") {
    return "per trip";
  }
  if (pricingType === "CUSTOM") {
    return "tailored";
  }
  return "per person";
}

/** Financial amounts retain cents, including a zero balance. */
export function formatAmount(cents: number) {
  return new Intl.NumberFormat("en-ZA", {
    style: "currency", currency: "ZAR", minimumFractionDigits: 2, maximumFractionDigits: 2
  }).format(cents / 100);
}
