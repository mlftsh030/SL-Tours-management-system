/** Mirrors com.sltours.billing.dto.BillingDtos and BillingTypes. Money is always integer cents. */
export type DocumentKind = "QUOTATION" | "INVOICE";
export type DocumentStatus = "DRAFT" | "ISSUED" | "ACCEPTED" | "DECLINED" | "VOID";
export type PaymentStatus = "NOT_APPLICABLE" | "UNPAID" | "PARTIALLY_PAID" | "PAID";
export type PaymentMethod = "EFT" | "CASH" | "CARD" | "OTHER";
export type PaymentState = "PENDING" | "CONFIRMED" | "REJECTED" | "REVERSED";
export type LineKind = "SERVICE" | "ADJUSTMENT";
export type DiscountType = "NONE" | "AMOUNT" | "PERCENT";
export type Template = "STANDARD" | "DETAILED";
export type Channel = "WHATSAPP" | "EMAIL" | "NATIVE" | "LINK";

export interface Customer { name: string; contactPerson: string | null; email: string | null; whatsapp: string | null; }
export interface Leg { pickup: string | null; dropOffs: string[]; }
export interface Line {
  title: string | null; legs: Leg[]; details: string | null; quantity: number; unit: string | null; unitPriceCents: number; kind: LineKind;
  tourSlug: string | null; cataloguePriceCents: number | null; priceOverrideReason: string | null;
}
export interface Discount { type: DiscountType; value: number; label: string | null; }
export interface DocumentContent {
  customer: Customer; issueDate: string | null; dueDate: string | null; serviceStart: string | null; serviceEnd: string | null;
  bookingReference: string | null; currency: string | null; passengers: number | null; vehicle: string | null; itineraryNotes: string | null;
  lines: Line[]; discount: Discount | null; applyTax: boolean; customerNotes: string | null; terms: string[] | null; template: Template;
}
export interface Totals {
  lineTotals: number[]; subtotal: number; discount: number; taxable: number; tax: number; total: number;
  taxRateBasisPoints: number | null; taxLabel: string | null;
}

export type PieceKind = "LABELLED" | "BULLETS" | "TEXT" | "GAP";
export interface DocumentView {
  title: string; watermark: string | null; filenameBase: string; info: Array<{ label: string; value: string }>; costHeader: string;
  items: Array<{ title: string | null; pieces: Array<{ kind: PieceKind; label: string | null; text: string | null; bullets: string[] }>; amount: string | null }>;
  blankRows: number; totals: Array<{ label: string; amount: string; grand: boolean }>; notesHeading: string | null; notes: string[];
  bankingHeading: string | null; banking: Array<{ label: string; value: string }>; termsHeading: string | null; terms: string[];
  company: { name: string; tagline: string | null; phone: string | null; whatsapp: string | null; email: string | null; website: string | null; address: string; logoRef: string | null };
}

export interface DocumentSummary {
  id: string; kind: DocumentKind; status: DocumentStatus; number: number | null; displayNumber: string; customerName: string;
  customerEmail: string | null; bookingReference: string | null; issueDate: string | null; dueDate: string | null; serviceStart: string | null;
  currency: string; totalCents: number; paidCents: number; balanceCents: number; paymentStatus: PaymentStatus; overdue: boolean; updatedAt: string;
}
export interface Related { id: string; kind: DocumentKind; status: DocumentStatus; displayNumber: string; }
export interface Attachment { id: string; paymentId: string | null; url: string; filename: string | null; contentType: string; sizeBytes: number; label: string | null; uploadedBy: string; uploadedAt: string; }
export interface Payment {
  id: string; version: number; amountCents: number; paidOn: string; method: PaymentMethod; reference: string | null; notes: string | null;
  status: PaymentState; receiptNumber: number | null; receiptFilename: string | null; recordedBy: string; recordedAt: string;
  decidedBy: string | null; decidedAt: string | null; rejectReason: string | null; attachments: Attachment[];
  reversedBy: string | null; reversedAt: string | null; reverseReason: string | null;
}
export interface ShareLink { id: string; paymentId: string | null; channel: Channel; createdBy: string; createdAt: string; expiresAt: string; revokedAt: string | null; revokedBy: string | null; lastOpenedAt: string | null; openCount: number; active: boolean; }
export interface HistoryEvent { event: string; channel: Channel | null; actor: string; detail: string | null; createdAt: string; paymentId: string | null; }
export interface DocumentDetail {
  summary: DocumentSummary; version: number; content: DocumentContent; internalNotes: string | null; totals: Totals; view: DocumentView | null;
  payments: Payment[]; pendingCents: number; attachments: Attachment[]; links: ShareLink[]; events: HistoryEvent[];
  sourceQuotation: Related | null; convertedInvoice: Related | null; revisionOf: Related | null; supersededBy: Related | null; pendingRevision: Related | null;
  createdBy: string; createdAt: string; issuedBy: string | null; issuedAt: string | null; voidedBy: string | null; voidReason: string | null;
  pdfFilename: string; docxFilename: string; numberingConfirmed: boolean;
  /** The enquiry this answers, and the booking it belongs to (for a quotation: the booking created from it). */
  enquiryReference: string | null; linkedBookingReference: string | null; customerId: string | null;
}
export interface PreviewResult { totals: Totals; view: DocumentView; warnings: string[]; }
export interface ShareResult {
  linkId: string; url: string; expiresAt: string; message: string; subject: string; whatsappUrl: string; mailtoUrl: string; filename: string;
  customerNumberMissing: boolean; customerEmailMissing: boolean;
}
export type ShareAction = "WHATSAPP_OPENED" | "EMAIL_OPENED" | "NATIVE_SHARED" | "PDF_DOWNLOADED" | "DOCX_DOWNLOADED" | "LINK_COPIED";
export interface Capabilities { whatsappApiConfigured: boolean; emailConfigured: boolean; nativeShareRecommended: boolean; defaultLinkDays: number; maxLinkDays: number; whatsappNote: string; emailNote: string; }
export interface CustomerSuggestion { name: string; contactPerson: string | null; email: string | null; whatsapp: string | null; source: string; bookingReference: string | null; }
export interface BookingPrefill { bookingId: string; bookingReference: string; content: DocumentContent; notes: string[]; }
export interface Sequence { kind: "INVOICE" | "QUOTATION" | "RECEIPT"; nextNumber: number; confirmed: boolean; highestUsed: number; updatedBy: string; updatedAt: string; }
export interface BillingSettings {
  version: number; companyName: string; tagline: string | null; phone: string | null; whatsapp: string | null; email: string | null; website: string | null;
  address: string; addressNeedsReview: boolean; logoUrl: string; customLogo: boolean; bankAccountName: string | null; bankName: string | null;
  bankAccountNumber: string | null; bankAccountType: string | null; bankBranchCode: string | null; bankSwiftCode: string | null;
  paymentInstructions: string | null; invoiceTerms: string; quotationTerms: string; currency: string; taxEnabled: boolean; taxLabel: string | null;
  taxRateBasisPoints: number | null; taxNumber: string | null; defaultDueDays: number | null; quotationValidDays: number | null;
  numbering: Sequence[]; updatedBy: string; updatedAt: string;
}
export interface CustomerDocument {
  id: string; kind: DocumentKind; status: DocumentStatus; displayNumber: string; issueDate: string; dueDate: string | null; currency: string;
  totalCents: number; paidCents: number; balanceCents: number; paymentStatus: PaymentStatus; bookingReference: string | null; filename: string;
  /** An issued invoice with a balance: the customer may send proof of payment for staff to check. */
  canUploadProof: boolean; proofsUploaded: number; lastProofAt: string | null;
}
