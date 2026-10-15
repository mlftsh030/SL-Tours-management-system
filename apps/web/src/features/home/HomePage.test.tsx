import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CurrencyProvider, CurrencySelect, Price } from "../../lib/currency";
import type { TourSummary } from "../../types/api";
import { activityCard, vehicleOption } from "../../test/fixtures";
import { HomePage } from "../tours/HomePage";

const capePoint: TourSummary = {
  slug: "cape-peninsula-journey",
  title: "Cape Peninsula Journey",
  shortDescription: "Cape Point, penguins, and coastal villages.",
  duration: "Full day",
  basePriceCents: 210000,
  pricingType: "PER_PERSON",
  category: "PENINSULA",
  featured: true,
  heroImage: "/assets/cape-point-tour.jpg",
  cardNote: "Included: Entrance fees, toll fee and coffee.",
  tags: ["nature"], heroImageAlt: "Cape of Good Hope sign", groupSizeLabel: "Minimum 5 guests", minimumGuests: 5
};

function jsonResponse(body: unknown) {
  return Promise.resolve(
    new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } })
  );
}

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.stubGlobal(
    "fetch",
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("frankfurter")) {
        return jsonResponse({ rates: { USD: 0.055, EUR: 0.05, GBP: 0.043 } });
      }
      if (url.includes("/api/v1/tours")) {
        return jsonResponse([capePoint]);
      }
      if (url.includes("/api/v1/private-trips/vehicle-options")) {
        return jsonResponse([vehicleOption("hyundai-staria", "Hyundai Staria"),
          vehicleOption("coach-72-seater", "Large-capacity coach", { typeLabel: "72-seater", category: "COACH", passengerCapacity: 72, capacityConfirmed: false,
            imageAlt: "A large coach on a highway" })]);
      }
      if (url.includes("/api/v1/activities")) {
        return jsonResponse([activityCard("cape-point", { title: "Cape Point", area: "CAPE_POINT", areaLabel: "Cape Point", summary: "The south-western tip." })]);
      }
      return Promise.reject(new Error(`Unexpected request ${url}`));
    })
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function renderWithProviders(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <CurrencyProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </CurrencyProvider>
    </QueryClientProvider>
  );
}

describe("HomePage", () => {
  it("keeps the established sections and introduces every main section with a link to its page", async () => {
    renderWithProviders(<HomePage />);
    expect(screen.getByRole("heading", { level: 1, name: "Exceptional" })).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Book a Tour" })[0]).toHaveAttribute("href", "/experiences");
    const order = ["Cape Town Tours With a Local Team", "Popular Tour Packages", "Private Trips", "Things to Do in Cape Town", "Pictures Taken On Tour",
      "Why Book With Us?"].map(name => screen.getByRole("heading", { name }));
    for (let i = 1; i < order.length; i++) expect(order[i - 1].compareDocumentPosition(order[i]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.queryByAltText(/owner/i)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "About SL Tours" })).toHaveAttribute("href", "/about");
    expect(screen.getByRole("link", { name: "View All Tours" })).toHaveAttribute("href", "/experiences");
    expect(screen.getByRole("link", { name: "Book a Private Trip" })).toHaveAttribute("href", "/book-private-trip");
    expect(screen.getByRole("link", { name: "View Things to Do in Cape Town" })).toHaveAttribute("href", "/explore");
    expect(screen.getByRole("link", { name: "View Gallery" })).toHaveAttribute("href", "/gallery");
    expect(screen.getByRole("link", { name: "Send an Enquiry" })).toHaveAttribute("href", "/contact");
    expect(screen.getByRole("link", { name: "Rate us on Google" })).toHaveAttribute("href", "https://g.page/r/CRyeNopih97yEAE/review");

    const card = (await screen.findByRole("heading", { name: "Cape Peninsula Journey" })).closest("article");
    const inCard = within(card as HTMLElement);
    expect(inCard.getByText("Included: Entrance fees, toll fee and coffee.")).toBeInTheDocument();
    expect(card?.textContent?.replace(/\s+/g, " ")).toContain("R 2 100");
    expect(inCard.getByRole("link", { name: "Enquire about Cape Peninsula Journey" })).toHaveAttribute("href", "/experiences/cape-peninsula-journey#enquire");
    expect(inCard.queryByRole("link", { name: /Book Online/ })).not.toBeInTheDocument();
    const whatsapp = inCard.getByRole("link", { name: "Enquire on WhatsApp" }).getAttribute("href") ?? "";
    expect(decodeURIComponent(whatsapp)).toContain("I would like to enquire about: Cape Peninsula Journey");
    expect(await screen.findByRole("heading", { name: "Cape Point" })).toBeInTheDocument();
  });

  it("shows every vehicle option with its picture in tour-style cards, with controls, and carries the choice into the booking flow", async () => {
    renderWithProviders(<HomePage />);
    const show = await screen.findByRole("region", { name: "Vehicles for private trips" });
    const slides = within(show);
    expect(slides.getByRole("heading", { name: "Hyundai Staria" })).toBeInTheDocument();
    expect(slides.getByText("Up to 8 passengers")).toBeInTheDocument();
    expect(slides.getByText("Up to 72 passengers (capacity to be confirmed)")).toBeInTheDocument();
    expect(slides.getByRole("img", { name: "A large coach on a highway" })).toHaveAttribute("src", "/assets/vehicles/coach-72-seater-480.jpg");
    expect(slides.getAllByText("Illustrative image—actual vehicle may differ")).toHaveLength(2);
    expect(slides.getByText(/Availability is confirmed for your date/)).toBeInTheDocument();
    expect(show.textContent).not.toMatch(/R\s?\d/); // no prices on the homepage
    expect(slides.getByRole("link", { name: "Enquire about the Hyundai Staria" })).toHaveAttribute("href", "/book-private-trip?vehicle=hyundai-staria");
    expect(slides.getByText("1 of 2")).toBeInTheDocument();
    await userEvent.click(slides.getByRole("button", { name: "Next vehicle" }));
    expect(slides.getByText("2 of 2")).toBeInTheDocument();
    expect(slides.getByRole("button", { name: "Show Large-capacity coach" })).toHaveAttribute("aria-current", "true");
    slides.getByRole("button", { name: "Previous vehicle" }).focus();
    await userEvent.keyboard("{ArrowLeft}");
    expect(slides.getByText("1 of 2")).toBeInTheDocument();
  });
});

describe("currency conversion", () => {
  it("converts ZAR prices with live rates and remembers the choice", async () => {
    renderWithProviders(
      <>
        <CurrencySelect />
        <Price cents={210000} />
      </>
    );

    expect(document.body.textContent?.replace(/\s+/g, " ")).toContain("R 2 100");

    await userEvent.selectOptions(screen.getByLabelText("Currency"), "USD");

    const converted = await screen.findByText("≈ $116");
    expect(converted).toHaveAttribute("title", expect.stringContaining("approx., live rate"));
    expect(localStorage.getItem("slToursSelectedCurrency")).toBe("USD");
  });
});
