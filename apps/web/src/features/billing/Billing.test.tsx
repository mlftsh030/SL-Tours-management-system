import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { DocumentDetail, DocumentView, ShareResult } from "./billingTypes";
import { DocumentPage } from "./DocumentPage";
import { DocumentPreview } from "./DocumentPreview";
import { emptyForm, emptyLine, toContent } from "./editorForm";
import { formatMoney, parseMoney } from "./money";
import { ShareDialog } from "./ShareDialog";

const view: DocumentView = {
  title: "INVOICE", watermark: null, filenameBase: "INV-100099",
  info: [{ label: "DATE", value: "14-07-2026" }, { label: "CLIENT", value: "Sample Client" }, { label: "INVOICE NUMBER", value: "100099" }],
  costHeader: "COST - R",
  items: [{ title: "Transfer", amount: null, pieces: [
    { kind: "LABELLED", label: "Pick up", text: "Cape Town international airport", bullets: [] },
    { kind: "LABELLED", label: "Drop off", text: "Nieuwe Sion Estate", bullets: [] },
    { kind: "GAP", label: null, text: null, bullets: [] },
    { kind: "LABELLED", label: "Pick up", text: "Nieuwe Sion Estate", bullets: [] },
    { kind: "BULLETS", label: "Drop off", text: null, bullets: ["Kenilworth", "Gugulethu", "Macasgavi", "Crossroads", "Mfuleni"] }] }],
  blankRows: 2, totals: [{ label: "GRAND TOTAL:", amount: "R2 850", grand: true }], notesHeading: null, notes: [],
  bankingHeading: "BANKING DETAILS", banking: [{ label: "ACCOUNT NAME", value: "SL TOURS (PTY) LTD" }, { label: "BANK", value: "FNB SA" }, { label: "ACCOUNT NUMBER", value: "00000000000" }],
  termsHeading: "TERMS AND CONDITIONS", terms: ["Submit proof of payment via WhatsApp or email."],
  company: { name: "SL Tours (Pty) Ltd", tagline: "SL Tours offers excellent opportunities to explore South Africa.", phone: "+27 67 012 7776", whatsapp: "+27 67 012 7776",
    email: "tours.st@outlook.com", website: null, address: "100 Example Street Cape Town 8001", logoRef: "bundled:sl-tours-logo" }
};

function detail(status: DocumentDetail["summary"]["status"]): DocumentDetail {
  return {
    summary: { id: "doc-1", kind: "INVOICE", status, number: status === "DRAFT" ? null : 100100, displayNumber: status === "DRAFT" ? "Draft doc-1" : "100100",
      customerName: "Sample Client", customerEmail: "yl@example.com", bookingReference: null, issueDate: status === "DRAFT" ? null : "2026-07-14", dueDate: null,
      serviceStart: null, currency: "ZAR", totalCents: 285000, paidCents: 0, balanceCents: 285000, paymentStatus: "UNPAID", overdue: false, updatedAt: "2026-07-14T10:00:00Z" },
    version: 3, content: { customer: { name: "Sample Client", contactPerson: null, email: "yl@example.com", whatsapp: "0825551234" }, issueDate: null, dueDate: null,
      serviceStart: null, serviceEnd: null, bookingReference: null, currency: "ZAR", passengers: null, vehicle: null, itineraryNotes: null, lines: [], discount: null,
      applyTax: false, customerNotes: null, terms: null, template: "STANDARD" },
    internalNotes: "Pays late", totals: { lineTotals: [285000], subtotal: 285000, discount: 0, taxable: 285000, tax: 0, total: 285000, taxRateBasisPoints: null, taxLabel: null },
    view, payments: [], pendingCents: 0, attachments: [], links: [], events: [], sourceQuotation: null, convertedInvoice: null, revisionOf: null, supersededBy: null,
    pendingRevision: null, createdBy: "staff@sltours.local", createdAt: "2026-07-14T09:00:00Z", issuedBy: status === "DRAFT" ? null : "admin@sltours.local",
    issuedAt: null, voidedBy: null, voidReason: null, pdfFilename: "INV-100100.pdf", docxFilename: "INV-100100.docx", numberingConfirmed: true,
    enquiryReference: null, linkedBookingReference: null, customerId: null
  };
}

const json = (body: unknown, status = 200) => Promise.resolve(new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
let calls: Array<{ url: string; method: string; body: unknown }> = [];
function stubFetch(handler: (url: string, method: string) => Promise<Response> | undefined) {
  calls = [];
  vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input), method = init?.method ?? "GET";
    calls.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    if (url.includes("/api/v1/auth/csrf")) return json({ token: "t", headerName: "X-XSRF-TOKEN" });
    return handler(url, method) ?? Promise.reject(new Error(`Unexpected ${method} ${url}`));
  }));
}
function wrap(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><MemoryRouter>{ui}</MemoryRouter></QueryClientProvider>);
}
afterEach(() => vi.unstubAllGlobals());

describe("money", () => {
  it("prints amounts like the SL Tours template and parses what staff type", () => {
    expect(formatMoney(285000)).toBe("R2 850");
    expect(formatMoney(285050)).toBe("R2 850,50");
    expect(formatMoney(120000, "USD")).toBe("USD 1 200");
    expect(parseMoney("2 850,50")).toBe(285050);
    expect(parseMoney("R2850")).toBe(285000);
    expect(parseMoney("")).toBeNull();
    expect(() => parseMoney("12.345")).toThrow(/two decimals/);
    expect(() => parseMoney("-50")).toThrow();
    expect(parseMoney("-50", true)).toBe(-5000);
  });
});

describe("editor form", () => {
  it("keeps place names as typed, turns drop-off lines into a list and sends a percentage discount in basis points", () => {
    const form = emptyForm();
    form.customer.name = " Sample Client ";
    form.lines = [{ ...emptyLine(), title: "Transfer", price: "2850", legs: [
      { pickup: "Cape Town international airport", dropOffs: "Nieuwe Sion Estate" },
      { pickup: "Nieuwe Sion Estate", dropOffs: "Kenilworth\n Gugulethu \n\nMacasgavi\nCrossroads\nMfuleni" }] },
      { ...emptyLine("ADJUSTMENT"), price: "-500" }];
    form.discountType = "PERCENT"; form.discountValue = "12,5";
    const c = toContent(form);
    expect(c.customer.name).toBe("Sample Client");
    expect(c.lines[0].legs[1].dropOffs).toEqual(["Kenilworth", "Gugulethu", "Macasgavi", "Crossroads", "Mfuleni"]);
    expect(c.lines[0].legs[0].pickup).toBe("Cape Town international airport");
    expect(c.lines[0].unitPriceCents).toBe(285000);
    expect(c.lines[1]).toMatchObject({ kind: "ADJUSTMENT", unitPriceCents: -50000 });
    expect(c.discount).toEqual({ type: "PERCENT", value: 1250, label: null });
    expect(c.terms).toBeNull();
  });

  it("names the service when an amount cannot be read", () => {
    const form = emptyForm();
    form.customer.name = "X";
    form.lines = [{ ...emptyLine(), price: "abc" }];
    expect(() => toContent(form)).toThrow(/Service 1/);
  });
});

describe("DocumentPreview", () => {
  it("shows the template's sections with bold labels and a bulleted drop-off list", () => {
    wrap(<DocumentPreview view={view} />);
    const paper = screen.getByRole("document", { name: "Preview of INV-100099" });
    expect(within(paper).getByText("INVOICE")).toBeInTheDocument();
    expect(within(paper).getAllByText("Pick up:")).toHaveLength(2);
    expect(within(paper).getAllByRole("listitem").map(li => li.textContent)).toEqual(expect.arrayContaining(["Kenilworth", "Mfuleni"]));
    expect(within(paper).getByText("R2 850")).toBeInTheDocument();
    expect(within(paper).getByText("00000000000")).toBeInTheDocument();
    expect(within(paper).getByRole("img", { name: "SL Tours (Pty) Ltd logo" })).toHaveAttribute("src", "/assets/sl-tours-logo-original.jpg");
    expect(paper.textContent).not.toMatch(/Dropp/);
  });
});

describe("DocumentPage", () => {
  beforeEach(() => stubFetch(url => url.endsWith("/documents/doc-1") ? json(detail("DRAFT")) : undefined));

  it("lets staff edit and download a draft but leaves issuing to administrators", async () => {
    wrap(<DocumentPage id="doc-1" isAdmin={false} />);
    expect(await screen.findByRole("heading", { name: "Invoice draft" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Edit draft" })).toHaveAttribute("href", "/operations/billing/doc-1/edit");
    expect(screen.getByRole("link", { name: "Word (.docx)" })).toHaveAttribute("href", "/api/v1/admin/billing/documents/doc-1/docx");
    expect(screen.queryByRole("button", { name: /Issue invoice/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Discard draft/ })).not.toBeInTheDocument();
    expect(screen.getByText(/Changes made in Word stay in that file/)).toBeInTheDocument();
    expect(screen.getByText("Pays late")).toBeInTheDocument();
  });

  it("asks an administrator to confirm before issuing, then sends the version once", async () => {
    stubFetch((url, method) => method === "POST" && url.endsWith("/issue") ? json(detail("ISSUED")) : url.endsWith("/documents/doc-1") ? json(detail("DRAFT")) : undefined);
    wrap(<DocumentPage id="doc-1" isAdmin />);
    await userEvent.click(await screen.findByRole("button", { name: "Issue invoice" }));
    expect(screen.getByRole("alertdialog", { name: "Issue this invoice?" })).toBeInTheDocument();
    const confirm = screen.getByRole("button", { name: "Issue for R2 850" });
    await userEvent.click(confirm);
    expect(await screen.findByRole("heading", { name: "Invoice 100100" })).toBeInTheDocument();
    const issues = calls.filter(c => c.url.endsWith("/issue"));
    expect(issues).toHaveLength(1);
    expect(issues[0].body).toEqual({ version: 3 });
  });
});

describe("ShareDialog", () => {
  const share: ShareResult = {
    linkId: "l1", url: "http://localhost:8080/api/v1/public/documents/abc", expiresAt: "2026-07-28T10:00:00Z", subject: "SL Tours invoice 100100",
    message: "Hello Sample Client,\n\nPlease find invoice 100100 here:\nhttp://localhost:8080/api/v1/public/documents/abc", filename: "INV-100100.pdf",
    whatsappUrl: "https://wa.me/27825551234?text=Hello", mailtoUrl: "mailto:yl@example.com?subject=SL%20Tours%20invoice%20100100&body=Hello",
    customerNumberMissing: false, customerEmailMissing: false
  };
  beforeEach(() => stubFetch((url, method) => {
    if (url.endsWith("/capabilities")) return json({ whatsappApiConfigured: false, emailConfigured: false, nativeShareRecommended: true, defaultLinkDays: 14,
      maxLinkDays: 90, whatsappNote: "Nothing is sent automatically.", emailNote: "No email service is set up." });
    if (method === "POST" && url.endsWith("/share")) return json(share);
    if (method === "POST" && url.endsWith("/share-events")) return Promise.resolve(new Response(null, { status: 204 }));
    if (url.endsWith("/pdf")) return Promise.resolve(new Response(new Blob(["%PDF-1.7"]), { status: 200 }));
    return undefined;
  }));

  it("prepares a WhatsApp message for staff to send and records only that WhatsApp was opened", async () => {
    wrap(<ShareDialog document={detail("ISSUED")} filename="INV-100100.pdf" pdfUrl="/api/v1/admin/billing/documents/doc-1/pdf" onClose={() => undefined} />);
    await userEvent.selectOptions(screen.getByLabelText("Customer link works for"), "7");
    await userEvent.click(screen.getByRole("button", { name: "Prepare WhatsApp message" }));
    const message = await screen.findByLabelText(/^Message/);
    await userEvent.type(message, "{end} Thanks!");
    const open = screen.getByRole("link", { name: "Open WhatsApp" });
    expect(open).toHaveAttribute("href", `https://wa.me/27825551234?text=${encodeURIComponent(`${share.message} Thanks!`)}`);
    expect(open).toHaveAttribute("target", "_blank");
    expect(screen.getByText(/You press send yourself\. Opening WhatsApp does not mean the customer received the message\./)).toBeInTheDocument();
    expect(calls.find(c => c.url.endsWith("/share"))?.body).toEqual({ channel: "WHATSAPP", expiresInDays: 7 });
    open.addEventListener("click", e => e.preventDefault());
    await userEvent.click(open);
    await waitFor(() => expect(calls.find(c => c.url.endsWith("/share-events"))?.body).toEqual({ action: "WHATSAPP_OPENED", channel: "WHATSAPP" }));
    expect(document.body.textContent).not.toMatch(/\bsent\b(?! automatically)/i);
  });

  it("falls back to download-and-attach when the browser cannot share files", async () => {
    vi.stubGlobal("navigator", { ...navigator, canShare: undefined, share: undefined });
    wrap(<ShareDialog document={detail("ISSUED")} filename="INV-100100.pdf" pdfUrl="/api/v1/admin/billing/documents/doc-1/pdf" onClose={() => undefined} />);
    await userEvent.click(screen.getByRole("button", { name: "Share the PDF file…" }));
    expect(await screen.findByText(/cannot attach files to WhatsApp directly/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Download INV-100100.pdf" })).toHaveAttribute("href", "/api/v1/admin/billing/documents/doc-1/pdf?download=true");
  });

  it("hands the PDF to the device share sheet when supported", async () => {
    const shareFn = vi.fn(() => Promise.resolve());
    vi.stubGlobal("navigator", { ...navigator, canShare: () => true, share: shareFn });
    wrap(<ShareDialog document={detail("ISSUED")} filename="INV-100100.pdf" pdfUrl="/api/v1/admin/billing/documents/doc-1/pdf" onClose={() => undefined} />);
    await userEvent.click(screen.getByRole("button", { name: "Share the PDF file…" }));
    await waitFor(() => expect(shareFn).toHaveBeenCalledTimes(1));
    const arg = (shareFn.mock.calls[0] as unknown as [{ files: File[] }])[0];
    expect(arg.files[0].name).toBe("INV-100100.pdf");
    expect(arg.files[0].type).toBe("application/pdf");
    expect(await screen.findByText(/Check in WhatsApp or email that it was sent/)).toBeInTheDocument();
  });
});
