/** Formats cents the way SL Tours documents print them: "R2 850", "R2 850,50", "USD 1 200". Same rules as the server. */
export function formatMoney(cents: number, currency = "ZAR") {
  const negative = cents < 0;
  const abs = Math.abs(cents);
  const whole = Math.floor(abs / 100), part = abs % 100;
  const grouped = String(whole).replace(/\B(?=(\d{3})+(?!\d))/g, " ");
  const prefix = currency === "ZAR" ? "R" : `${currency} `;
  return `${negative ? "-" : ""}${prefix}${grouped}${part ? `,${String(part).padStart(2, "0")}` : ""}`;
}

/** Parses a rand amount typed by staff ("2850", "2 850,50", "2850.5") into cents, or null when blank. Throws on invalid input. */
export function parseMoney(value: string, allowNegative = false): number | null {
  const cleaned = value.replace(/[\sR]/g, "").replace(",", ".");
  if (!cleaned) return null;
  const pattern = allowNegative ? /^-?\d+(?:\.\d{1,2})?$/ : /^\d+(?:\.\d{1,2})?$/;
  if (!pattern.test(cleaned)) throw new Error("Enter an amount in rand with up to two decimals, e.g. 2850 or 2850,50");
  const negative = cleaned.startsWith("-");
  const [rands, cents = ""] = cleaned.replace("-", "").split(".");
  const total = Number(rands) * 100 + Number(cents.padEnd(2, "0"));
  return negative ? -total : total;
}

export const centsToInput = (cents: number | null | undefined) => cents == null ? "" : (cents / 100).toFixed(2).replace(/\.00$/, "");

export const documentStatusLabels: Record<string, string> = { DRAFT: "Draft", ISSUED: "Issued", ACCEPTED: "Accepted", DECLINED: "Declined", VOID: "Void" };
export const paymentStatusLabels: Record<string, string> = { NOT_APPLICABLE: "—", UNPAID: "Unpaid", PARTIALLY_PAID: "Part paid", PAID: "Paid" };
export const paymentStateLabels: Record<string, string> = { PENDING: "Waiting for confirmation", CONFIRMED: "Confirmed", REJECTED: "Rejected", REVERSED: "Reversed" };
export const methodLabels: Record<string, string> = { EFT: "EFT / bank transfer", CASH: "Cash", CARD: "Card", OTHER: "Other" };
export const kindLabels: Record<string, string> = { INVOICE: "Invoice", QUOTATION: "Quotation" };
export const displayDate = (iso: string | null | undefined) => iso ? iso.split("-").reverse().join("-") : "—";
