import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CustomersPage } from "./CustomersPage";
import { ExportsPanel } from "../reports/ExportsPanel";

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
const summary = { id: "c1", name: "Thandi M", email: "thandi@example.com", phone: "+27 82 555 0101", hasAccount: true, lastLoginAt: null, activeSessions: 0,
  enquiries: 2, openEnquiries: 1, bookings: 1, upcomingBookings: 1, invoices: 1, outstandingCents: 450000, lastActivityAt: "2026-10-05T10:00:00Z", demoData: false };
const detail = { summary, version: 3, country: "South Africa", notes: null, accountEmail: "thandi@example.com", accountVerified: true, mergedIntoId: null,
  enquiries: [{ reference: "SLE-2026-AAAAA", status: "CONVERTED", type: "TOUR", source: "CONTACT_FORM", tourTitle: "Cape Point", requestedDate: "2026-11-01", createdAt: "2026-10-01T10:00:00Z", updatedAt: "2026-10-02T10:00:00Z" }],
  bookings: [{ reference: "SLT-2026-BBBBBB", title: "Cape Point and 1 more", status: "AWAITING_PAYMENT", travelDate: "2026-11-01", guests: 3, totalCents: 643500, paidCents: 200000, assigned: false, sourceQuotation: "1002" }],
  documents: [{ id: "d1", kind: "INVOICE", status: "ISSUED", displayNumber: "100106", issueDate: "2026-10-03", dueDate: "2026-10-10", currency: "ZAR", totalCents: 643500, paidCents: 200000, paymentStatus: "PARTIALLY_PAID", bookingReference: "SLT-2026-BBBBBB" }],
  payments: [{ id: "p1", documentId: "d1", invoiceNumber: "100106", amountCents: 200000, paidOn: "2026-10-04", method: "EFT", status: "CONFIRMED", receiptNumber: 1002, recordedAt: "2026-10-04T09:00:00Z" }],
  merges: [] };
const show = (path: string, isAdmin: boolean) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <MemoryRouter initialEntries={[path]}><Routes><Route path="/operations/customers/*" element={<CustomersPage isAdmin={isAdmin} />} />
    <Route path="/operations/exports" element={<ExportsPanel />} /></Routes></MemoryRouter></QueryClientProvider>);
afterEach(() => vi.unstubAllGlobals());

describe("customer records for staff", () => {
  it("searches by any identifier and shows outstanding balances", async () => {
    const urls: string[] = [];
    vi.stubGlobal("fetch", vi.fn((url: string) => { urls.push(url); return json({ content: [summary], number: 0, totalPages: 1, totalElements: 1 }); }));
    show("/operations/customers", false);
    expect(await screen.findByRole("link", { name: "Thandi M" })).toHaveAttribute("href", "/operations/customers/c1");
    expect(screen.getByText("R4 500")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Search"), "SLT-2026-BBBBBB");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(urls[urls.length - 1]).toContain("q=SLT-2026-BBBBBB");
  });

  it("shows the whole history, and only administrators can merge", async () => {
    vi.stubGlobal("fetch", vi.fn(() => json(detail)));
    const { unmount } = show("/operations/customers/c1", false);
    expect(await screen.findByRole("heading", { name: "Thandi M" })).toBeInTheDocument();
    const bookings = screen.getByRole("heading", { name: "Bookings" }).closest("section")!;
    expect(within(bookings).getByText(/from quotation 1002/)).toBeInTheDocument();
    expect(within(bookings).getByText("Not assigned")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Invoice 100106" })).toHaveAttribute("href", "/operations/billing/d1");
    expect(screen.queryByRole("heading", { name: /Merge a duplicate/ })).not.toBeInTheDocument();
    unmount();
    show("/operations/customers/c1", true);
    expect(await screen.findByRole("heading", { name: /Merge a duplicate/ })).toBeInTheDocument();
  });

  it("offers CSV exports for the chosen dates", () => {
    show("/operations/exports", true);
    const links = screen.getAllByRole("link", { name: "Download CSV" }).map(l => l.getAttribute("href"));
    expect(links).toHaveLength(5);
    expect(links[0]).toMatch(/^\/api\/v1\/admin\/exports\/customers\?from=\d{4}-01-01&to=\d{4}-\d{2}-\d{2}$/);
  });
});
