import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AccountPage } from "./AccountPage";

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
const invoice = { id: "d1", kind: "INVOICE", status: "ISSUED", displayNumber: "100120", issueDate: "2026-10-01", dueDate: "2026-10-08", currency: "ZAR",
  totalCents: 630000, paidCents: 0, balanceCents: 630000, paymentStatus: "UNPAID", bookingReference: null, filename: "INV-100120.pdf",
  canUploadProof: true, proofsUploaded: 0, lastProofAt: null };
function server(overrides: Record<string, unknown> = {}) {
  const routes: Record<string, unknown> = {
    "/api/v1/auth/me": { authenticated: true, email: "thandi@example.com", name: "Thandi", role: "CUSTOMER", emailVerified: false },
    "/api/v1/account/profile": { name: "Thandi", email: "thandi@example.com", phone: null, emailVerified: false, memberSince: "2026-10-01T10:00:00Z" },
    "/api/v1/account/enquiries": [], "/api/v1/account/documents": [],
    "/api/v1/account/bookings?page=0&size=10": { content: [], number: 0, totalPages: 0, totalElements: 0 }, ...overrides
  };
  return vi.fn((url: string) => url.endsWith("/csrf") ? json({ token: "t", headerName: "X-CSRF-TOKEN" }) : url in routes ? json(routes[url]) : json({ message: "missing " + url }, 404));
}
const show = (path: string) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <MemoryRouter initialEntries={[path]}><Routes><Route path="/account/*" element={<AccountPage />} /><Route path="/login" element={<p>Sign in page</p>} /></Routes></MemoryRouter>
</QueryClientProvider>);
afterEach(() => vi.unstubAllGlobals());

describe("customer account area (UI only)", () => {
  it("has its own navigation, asks for email confirmation and suggests what to do next", async () => {
    vi.stubGlobal("fetch", server());
    show("/account");
    const nav = await screen.findByRole("navigation", { name: "My account" });
    expect(within(nav).getAllByRole("link").map(l => l.textContent)).toEqual(["Overview", "Enquiries", "Bookings", "Invoices & quotes", "Profile"]);
    expect(screen.getByRole("heading", { name: "Confirm your email address" })).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "Explore tours" })).toHaveAttribute("href", "/experiences");
    expect(screen.getByRole("link", { name: "Request a quote" })).toHaveAttribute("href", "/book-private-trip");
    expect(screen.queryByRole("button", { name: "Find earlier bookings" })).not.toBeInTheDocument();
  });

  it("sends unauthenticated visitors to sign in", async () => {
    vi.stubGlobal("fetch", server({ "/api/v1/auth/me": { authenticated: false, email: null, name: null, role: null } }));
    show("/account/documents");
    expect(await screen.findByText("Sign in page")).toBeInTheDocument();
  });

  it("lets the customer download invoices and send proof of payment, refusing oversized files", async () => {
    vi.stubGlobal("fetch", server({ "/api/v1/account/documents": [invoice] }));
    show("/account/documents");
    expect(await screen.findByRole("link", { name: "Download PDF" })).toHaveAttribute("href", "/api/v1/account/documents/d1/pdf");
    const input = screen.getByLabelText(/Send proof of payment/, { selector: "input" });
    await userEvent.upload(input, new File([new Uint8Array(16 * 1024 * 1024)], "big.pdf", { type: "application/pdf" }));
    await userEvent.click(screen.getByRole("button", { name: "Upload" }));
    expect(screen.getByText(/larger than 15 MB/)).toBeInTheDocument();
    expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes("/proof"))).toBe(false);
  });
});
