import type { DocumentContent, DocumentKind, LineKind, Template, DiscountType } from "./billingTypes";
import { centsToInput, parseMoney } from "./money";

/** Editor state: what staff type, kept as text until it is converted for the server. */
export interface LegForm { pickup: string; dropOffs: string; }
export interface LineForm {
  key: string; title: string; legs: LegForm[]; details: string; quantity: string; unit: string; price: string; kind: LineKind;
  tourSlug: string; overrideReason: string;
}
export interface EditorForm {
  customer: { name: string; contactPerson: string; email: string; whatsapp: string };
  bookingReference: string; issueDate: string; dueDate: string; serviceStart: string; serviceEnd: string; currency: string;
  passengers: string; vehicle: string; itineraryNotes: string; lines: LineForm[];
  discountType: DiscountType; discountValue: string; discountLabel: string; applyTax: boolean;
  customerNotes: string; customTerms: boolean; terms: string; template: Template; internalNotes: string;
}

let counter = 0;
export const newKey = () => `line-${Date.now()}-${counter++}`;
export const emptyLeg = (): LegForm => ({ pickup: "", dropOffs: "" });
export const emptyLine = (kind: LineKind = "SERVICE"): LineForm => ({
  key: newKey(), title: kind === "ADJUSTMENT" ? "Less: deposit already paid" : "", legs: kind === "ADJUSTMENT" ? [] : [emptyLeg()], details: "",
  quantity: "1", unit: "", price: "", kind, tourSlug: "", overrideReason: ""
});
export const emptyForm = (): EditorForm => ({
  customer: { name: "", contactPerson: "", email: "", whatsapp: "" }, bookingReference: "", issueDate: "", dueDate: "", serviceStart: "",
  serviceEnd: "", currency: "ZAR", passengers: "", vehicle: "", itineraryNotes: "", lines: [emptyLine()], discountType: "NONE", discountValue: "",
  discountLabel: "", applyTax: false, customerNotes: "", customTerms: false, terms: "", template: "STANDARD", internalNotes: ""
});

const text = (v: string) => v.trim() === "" ? null : v.trim();
const lines = (v: string) => v.split("\n").map(s => s.trim()).filter(Boolean);

/** Converts the form for the server. Throws an Error naming the field when an amount or number cannot be read. */
export function toContent(f: EditorForm): DocumentContent {
  return {
    customer: { name: f.customer.name.trim(), contactPerson: text(f.customer.contactPerson), email: text(f.customer.email), whatsapp: text(f.customer.whatsapp) },
    issueDate: text(f.issueDate), dueDate: text(f.dueDate), serviceStart: text(f.serviceStart), serviceEnd: text(f.serviceEnd),
    bookingReference: text(f.bookingReference), currency: f.currency || "ZAR",
    passengers: f.passengers.trim() ? Number(f.passengers) : null, vehicle: text(f.vehicle), itineraryNotes: text(f.itineraryNotes),
    lines: f.lines.map((l, i) => {
      let price: number | null;
      try { price = parseMoney(l.price, l.kind === "ADJUSTMENT"); }
      catch (e) { throw new Error(`Service ${i + 1}: ${(e as Error).message}`); }
      const quantity = Number(l.quantity.replace(",", "."));
      if (!Number.isFinite(quantity) || quantity <= 0) throw new Error(`Service ${i + 1}: enter a quantity above 0`);
      return {
        title: text(l.title), details: text(l.details), quantity, unit: text(l.unit), unitPriceCents: price ?? 0, kind: l.kind,
        legs: l.legs.map(g => ({ pickup: text(g.pickup), dropOffs: lines(g.dropOffs) })).filter(g => g.pickup || g.dropOffs.length),
        tourSlug: text(l.tourSlug), cataloguePriceCents: null, priceOverrideReason: text(l.overrideReason)
      };
    }),
    discount: f.discountType === "NONE" || !f.discountValue.trim() ? null : {
      type: f.discountType, label: text(f.discountLabel),
      value: f.discountType === "AMOUNT" ? (parseMoney(f.discountValue) ?? 0) : Math.round(Number(f.discountValue.replace(",", ".")) * 100)
    },
    applyTax: f.applyTax, customerNotes: text(f.customerNotes), terms: f.customTerms ? lines(f.terms) : null, template: f.template
  };
}

export function fromContent(c: DocumentContent, internalNotes: string | null): EditorForm {
  return {
    customer: { name: c.customer.name, contactPerson: c.customer.contactPerson ?? "", email: c.customer.email ?? "", whatsapp: c.customer.whatsapp ?? "" },
    bookingReference: c.bookingReference ?? "", issueDate: c.issueDate ?? "", dueDate: c.dueDate ?? "", serviceStart: c.serviceStart ?? "",
    serviceEnd: c.serviceEnd ?? "", currency: c.currency ?? "ZAR", passengers: c.passengers == null ? "" : String(c.passengers), vehicle: c.vehicle ?? "",
    itineraryNotes: c.itineraryNotes ?? "",
    lines: c.lines.map(l => ({
      key: newKey(), title: l.title ?? "", details: l.details ?? "", quantity: String(l.quantity), unit: l.unit ?? "", price: centsToInput(l.unitPriceCents),
      kind: l.kind ?? "SERVICE", tourSlug: l.tourSlug ?? "", overrideReason: l.priceOverrideReason ?? "",
      legs: l.legs.map(g => ({ pickup: g.pickup ?? "", dropOffs: g.dropOffs.join("\n") }))
    })),
    discountType: c.discount?.type ?? "NONE",
    discountValue: !c.discount ? "" : c.discount.type === "AMOUNT" ? centsToInput(c.discount.value) : String(c.discount.value / 100),
    discountLabel: c.discount?.label ?? "", applyTax: c.applyTax, customerNotes: c.customerNotes ?? "", customTerms: c.terms != null,
    terms: (c.terms ?? []).join("\n"), template: c.template ?? "STANDARD", internalNotes: internalNotes ?? ""
  };
}

/** Unsaved editor work kept in this browser so a refresh or lost connection does not lose it. */
export const localDraftKey = (id: string | undefined, kind: DocumentKind, booking: string) => `sltours.billing.draft.${id ?? `new.${kind}.${booking || "blank"}`}`;
export function readLocalDraft(key: string): { form: EditorForm; savedAt: string } | null {
  try { const raw = localStorage.getItem(key); return raw ? JSON.parse(raw) : null; } catch { return null; }
}
export function writeLocalDraft(key: string, form: EditorForm) {
  try { localStorage.setItem(key, JSON.stringify({ form, savedAt: new Date().toISOString() })); } catch { /* Storage may be full or blocked. */ }
}
export function clearLocalDraft(key: string) { try { localStorage.removeItem(key); } catch { /* ignore */ } }
