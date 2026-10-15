import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { TourDetailPage, itineraryStep } from "./TourDetailPage";
import type { TourDetail } from "../../types/api";

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
const base: TourDetail = {
  slug: "cape-town-essentials", title: "Cape Town City & Heritage Tour", shortDescription: "Bo-Kaap and the District Six Museum.", duration: "Half day",
  basePriceCents: 90000, pricingType: "PER_PERSON", category: "CITY", featured: false, heroImage: "/assets/tours/city-tour.jpg", cardNote: null, tags: [],
  heroImageAlt: "Aerial view of Cape Town", groupSizeLabel: "Minimum 5 guests", minimumGuests: 5, fullDescription: "Explore Cape Town.", maximumGuests: 20,
  locations: [], highlights: ["Bo-Kaap", "District Six Museum"], included: ["Qualified guide"], excluded: ["Museum admission"],
  itinerary: ["09:00 Hotel pickup", "11:15 Company's Garden"], faqs: [], pickupAvailable: true, bannerImage: null, seoTitle: "City tour", seoDescription: "City",
  pickupSummary: "Your Cape Town hotel", availabilityLabel: "Daily, weather permitting", tourTypeLabel: "Guided city tour",
  itineraryNote: "Sample itinerary for illustration only.", practicalInfo: ["Cableway tickets are not included."], enquiryForm: "STANDARD"
};
const airport: TourDetail = { ...base, slug: "airport-transfer", title: "Airport Transfers", pricingType: "PER_TRIP", basePriceCents: 130000, category: "TRANSFER",
  groupSizeLabel: "Private", enquiryForm: "AIRPORT", itinerary: ["Step 1 Send your flight details"], pickupSummary: "Airport or your accommodation" };

function renderPage(tour: TourDetail, sent: Array<Record<string, unknown>> = []) {
  vi.stubGlobal("fetch", vi.fn((url: string, options?: RequestInit) => {
    if (url.endsWith("/csrf")) return json({ token: "t", headerName: "X-CSRF-TOKEN" });
    if (url.includes(`/api/v1/tours/${tour.slug}`)) return json(tour);
    if (url.includes("/api/v1/tours")) return json([tour, { ...base, slug: "cape-winelands-escape", title: "Cape Winelands Tour", category: "WINELANDS" }]);
    if (url.includes("/api/v1/enquiries")) { sent.push(JSON.parse(String(options?.body))); return json({ reference: "SLE-2026-0001", status: "NEW", message: "Thank you.", duplicate: false }, 201); }
    return Promise.reject(new Error(`Unexpected ${url}`));
  }));
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={[`/experiences/${tour.slug}`]}><Routes><Route path="/experiences/:slug" element={<TourDetailPage />} /></Routes></MemoryRouter>
  </QueryClientProvider>);
}
afterEach(() => vi.unstubAllGlobals());

describe("tour detail page", () => {
  it("shows the summary, a labelled sample itinerary, inclusions, practical notes, an enquiry form and related tours", async () => {
    renderPage(base);
    expect(await screen.findByRole("heading", { level: 1, name: "Cape Town City & Heritage Tour" })).toBeInTheDocument();
    expect(document.body.textContent?.replace(/\s+/g, " ")).toContain("R 900 per person");
    expect(screen.getByText("Pickup: Your Cape Town hotel")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Sample itinerary" })).toBeInTheDocument();
    expect(screen.getByText(/for illustration only/)).toBeInTheDocument();
    expect(screen.getByText("Cableway tickets are not included.")).toBeInTheDocument();
    const form = screen.getByRole("form", { name: "Enquire about Cape Town City & Heritage Tour" });
    for (const label of ["Your name", "Email address", "WhatsApp number", "Group size", "Preferred date", "Pickup location", /Special requests/])
      expect(within(form).getByLabelText(label)).toBeInTheDocument();
    expect(within(form).getByRole("button", { name: "Send Enquiry" })).toBeInTheDocument();
    expect(screen.queryByText(/Book Online|Book Experience/)).not.toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Cape Winelands Tour" })).toBeInTheDocument();
  });

  it("collects flight, direction, destination and luggage for airport transfers and sends them with the tour", async () => {
    const sent: Array<Record<string, unknown>> = [];
    renderPage(airport, sent);
    const form = await screen.findByRole("form", { name: "Enquire about Airport Transfers" });
    expect(screen.getByRole("heading", { name: "How it works" })).toBeInTheDocument();
    const f = within(form);
    await userEvent.type(f.getByLabelText("Your name"), "Ama Mensah");
    await userEvent.type(f.getByLabelText("Email address"), "ama@example.com");
    await userEvent.type(f.getByLabelText("WhatsApp number"), "+27 82 555 0000");
    await userEvent.type(f.getByLabelText("Passengers"), "3");
    await userEvent.selectOptions(f.getByLabelText("Direction"), "DEPARTURE");
    await userEvent.type(f.getByLabelText("Flight number"), "BA58");
    await userEvent.type(f.getByLabelText("Flight date"), "2030-01-15");
    await userEvent.type(f.getByLabelText("Departure time"), "19:30");
    await userEvent.type(f.getByLabelText("Pickup address"), "Sea Point guesthouse");
    await userEvent.type(f.getByLabelText("Luggage"), "3 large cases");
    const whatsapp = decodeURIComponent(f.getByRole("link", { name: "Send on WhatsApp" }).getAttribute("href") ?? "");
    expect(whatsapp).toContain("Airport Transfers");
    expect(whatsapp).toContain("Flight: BA58");
    await userEvent.click(f.getByRole("button", { name: "Send Enquiry" }));
    expect(await screen.findByText(/not a confirmed booking/)).toBeInTheDocument();
    expect(sent[0]).toMatchObject({ tourSlug: "airport-transfer", enquiryType: "TOUR", customerPhone: "+27 82 555 0000", passengers: 3,
      transfer: { direction: "DEPARTURE", flightNumber: "BA58", flightDate: "2030-01-15", flightTime: "19:30", destination: "Sea Point guesthouse", luggage: "3 large cases" } });
  });

  it("reads itinerary labels for times, multi-day times and steps", () => {
    expect(itineraryStep("07:30 Hotel pickup")).toEqual({ label: "07:30", text: "Hotel pickup" });
    expect(itineraryStep("Day 2, 08:30 Knysna")).toEqual({ label: "Day 2, 08:30", text: "Knysna" });
    expect(itineraryStep("Step 1 Send your flight details")).toEqual({ label: "Step 1", text: "Send your flight details" });
  });
});
