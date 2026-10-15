import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CurrencyProvider } from "../../lib/currency";
import { applyReferral, readReferral } from "../../lib/referral";
import { EnquiryForm } from "../enquiries/EnquiryForm";
import { PartnerPortalPage } from "./PartnerPortalPage";
import { PartnerWithUsPage } from "./PartnerWithUsPage";

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
const wrap = (ui: React.ReactNode, path = "/") => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>
  <CurrencyProvider><MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter></CurrencyProvider></QueryClientProvider>);
beforeEach(() => localStorage.clear());
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); });

describe("partner with us", () => {
  it("requires a route and consent, and submits the application without inventing terms", async () => {
    const bodies: Array<Record<string, unknown>> = [];
    vi.stubGlobal("fetch", vi.fn((url: string, options?: RequestInit) => {
      if (url.endsWith("/csrf")) return json({ token: "t", headerName: "X-CSRF-TOKEN" });
      if (url.endsWith("/api/v1/partner-applications")) { bodies.push(JSON.parse(String(options?.body))); return json({ reference: "PTN-2026-ABCDE", message: "Thank you.", duplicate: false }, 201); }
      return Promise.reject(new Error(url));
    }));
    wrap(<PartnerWithUsPage />);
    expect(screen.getByText(/agreed individually in writing before anything is earned or paid/)).toBeInTheDocument();
    expect(screen.queryByText(/\d+%/)).not.toBeInTheDocument();
    const form = screen.getByRole("form", { name: "Partner application" });
    await userEvent.type(screen.getByLabelText("Business name"), "Harbour Hotel");
    await userEvent.type(screen.getByLabelText("Contact person"), "Lerato M");
    await userEvent.type(screen.getByLabelText("Business email"), "lerato@harbour.example");
    await userEvent.type(screen.getByLabelText("About your business"), "Boutique hotel in Sea Point");
    await userEvent.type(screen.getByLabelText(/Choose a password/), "a-long-password-1");
    await userEvent.click(screen.getByRole("checkbox", { name: /may use these details/ }));
    await userEvent.click(screen.getByRole("button", { name: "Send application" }));
    expect(await screen.findByText("Choose at least one way to work with us")).toBeInTheDocument();
    expect(bodies).toHaveLength(0);
    await userEvent.click(screen.getByRole("checkbox", { name: /Offer experiences/ }));
    expect(form).toContainElement(screen.getByLabelText("Experiences or services you would like to offer"));
    await userEvent.type(screen.getByLabelText("Experiences or services you would like to offer"), "Sunset walks");
    await userEvent.click(screen.getByRole("checkbox", { name: /Refer guests/ }));
    await userEvent.click(screen.getByRole("button", { name: "Send application" }));
    expect(await screen.findByText("PTN-2026-ABCDE")).toBeInTheDocument();
    expect(bodies[0]).toMatchObject({ routes: ["SUPPLIER", "REFERRAL"], businessName: "Harbour Hotel", businessType: "HOTEL", privacyConsent: true,
      servicesOffered: "Sunset walks", website: null });
  });
});

describe("referrals on public forms", () => {
  it("stores a valid referral, discloses it on the form, sends it with the enquiry, and can be removed", async () => {
    const bodies: Array<Record<string, unknown>> = [];
    vi.stubGlobal("fetch", vi.fn((url: string, options?: RequestInit) => {
      if (url.endsWith("/csrf")) return json({ token: "t", headerName: "X-CSRF-TOKEN" });
      if (url.includes("/api/v1/partners/referrals/HARBOUR2")) return json({ code: "HARBOUR2", partnerName: "Harbour Hotel" });
      if (url.includes("/api/v1/partners/referrals/")) return json({ message: "This referral code is not active" }, 404);
      if (url.endsWith("/api/v1/enquiries")) { bodies.push(JSON.parse(String(options?.body))); return json({ reference: "SLE-2026-ABCDE", status: "NEW", message: "Thank you.", duplicate: false }, 201); }
      return Promise.reject(new Error(url));
    }));
    await expect(applyReferral("NOPE1234", "LINK")).rejects.toThrow();
    expect(readReferral()).toBeNull();
    await applyReferral("harbour2", "LINK");
    render(<QueryClientProvider client={new QueryClient()}><EnquiryForm /></QueryClientProvider>);
    expect(screen.getByRole("note")).toHaveTextContent("Referred by Harbour Hotel. Harbour Hotel may receive a commission from SL Tours if you book; your price is the same.");
    await userEvent.type(screen.getByLabelText("Name"), "Thandi Nkosi");
    await userEvent.type(screen.getByLabelText("Email"), "thandi@example.com");
    await userEvent.type(screen.getByLabelText("Message"), "Airport pickup please");
    await userEvent.click(screen.getByRole("button", { name: "Send enquiry" }));
    await screen.findByText("Enquiry received");
    expect(bodies[0].referral).toMatchObject({ code: "HARBOUR2", source: "LINK" });
    expect(typeof (bodies[0].referral as { capturedAt: string }).capturedAt).toBe("string");
  });

  it("lets a visitor enter or remove a code", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => url.includes("/api/v1/partners/referrals/DESK2345") ? json({ code: "DESK2345", partnerName: "Concierge Desk" })
      : url.includes("/api/v1/partners/referrals/") ? json({ message: "not active" }, 404) : Promise.reject(new Error(url))));
    render(<QueryClientProvider client={new QueryClient()}><EnquiryForm /></QueryClientProvider>);
    await userEvent.click(screen.getByRole("button", { name: "Have a partner referral code?" }));
    await userEvent.type(screen.getByLabelText("Referral code"), "WRONG999");
    await userEvent.click(screen.getByRole("button", { name: "Apply" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("not active");
    await userEvent.clear(screen.getByLabelText("Referral code"));
    await userEvent.type(screen.getByLabelText("Referral code"), "desk2345");
    await userEvent.click(screen.getByRole("button", { name: "Apply" }));
    expect(await screen.findByText(/Referred by/)).toHaveTextContent("Concierge Desk");
    expect(readReferral()).toMatchObject({ code: "DESK2345", source: "CODE" });
    await userEvent.click(screen.getByRole("button", { name: "Remove referral" }));
    await waitFor(() => expect(readReferral()).toBeNull());
    expect(screen.getByRole("button", { name: "Have a partner referral code?" })).toBeInTheDocument();
  });
});

describe("partner area", () => {
  it("shows the partner's link, terms, and referrals by reference and status only", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => {
      if (url.endsWith("/api/v1/auth/me")) return json({ authenticated: true, email: "p@example.com", name: "Lerato", role: "PARTNER" });
      if (url.endsWith("/api/v1/partner/me")) return json({ reference: "PTN-2026-ABCDE", businessName: "Harbour Hotel", referralCode: "HARBOUR2",
        referralLink: "https://sl-tours.com/?ref=HARBOUR2", routes: [{ route: "REFERRAL", status: "APPROVED", decidedBy: null, decidedAt: null, reason: null }],
        agreements: [{ route: "REFERRAL", agreementReference: "AGR-1", effectiveFrom: "2026-10-01", effectiveTo: null, terms: "Terms as recorded" }] });
      if (url.endsWith("/api/v1/partner/referrals")) return json([{ kind: "Booking", reference: "SLT-2026-AAAAAA", product: "Tour", receivedOn: "2026-10-03",
        status: "Completed", commission: "Commission R120.00 (not yet paid out)" }]);
      return Promise.reject(new Error(url));
    }));
    wrap(<Routes><Route path="/partner/*" element={<PartnerPortalPage />} /></Routes>, "/partner");
    expect(await screen.findByRole("heading", { name: "Harbour Hotel" })).toBeInTheDocument();
    expect(screen.getByLabelText("Referral link")).toHaveValue("https://sl-tours.com/?ref=HARBOUR2");
    expect(screen.getByText("Terms as recorded")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Experiences" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("link", { name: "Referrals" }));
    expect(await screen.findByText("SLT-2026-AAAAAA")).toBeInTheDocument();
    expect(screen.getByText("Commission R120.00 (not yet paid out)")).toBeInTheDocument();
    expect(screen.getByText(/names and contact details are not shown/)).toBeInTheDocument();
  });

  it("sends other roles away from the partner area", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => url.endsWith("/api/v1/auth/me")
      ? json({ authenticated: true, email: "c@example.com", name: "C", role: "CUSTOMER" }) : Promise.reject(new Error(url))));
    wrap(<Routes><Route path="/partner/*" element={<PartnerPortalPage />} /><Route path="/account" element={<p>Customer account</p>} /></Routes>, "/partner");
    expect(await screen.findByText("Customer account")).toBeInTheDocument();
  });
});
