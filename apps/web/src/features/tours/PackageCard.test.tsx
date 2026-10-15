import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { PackageCard } from "./PackageCard";
import type { TourSummary } from "../../types/api";

const tour: TourSummary = {
  slug: "cape-peninsula-journey", title: "Cape Peninsula & Cape Point", shortDescription: "Coastal drives, Cape Point and the penguins.",
  duration: "Full day", basePriceCents: 210000, pricingType: "PER_PERSON", category: "PENINSULA", featured: true,
  heroImage: "/assets/tours/cape-point-tour.jpg", cardNote: "Included: Cape Point and Boulders entry", tags: [],
  heroImageAlt: "Cape of Good Hope sign", groupSizeLabel: "Minimum 5 guests", minimumGuests: 5
};
const renderIn = (ui: React.ReactNode) => render(<MemoryRouter>{ui}</MemoryRouter>);

describe("tour cards", () => {
  it("shows the image, price per person and enquiry actions", () => {
    renderIn(<PackageCard tour={tour} />);
    expect(screen.getByRole("img", { name: "Cape of Good Hope sign" })).toHaveAttribute("src", "/assets/tours/cape-point-tour.jpg");
    expect(screen.getByText("Full day")).toBeInTheDocument();
    expect(document.body.textContent?.replace(/\s+/g, " ")).toContain("R 2 100");
  });

  it("keeps the published unit: per trip for transfers and a quote for custom tours", () => {
    renderIn(<><PackageCard tour={{ ...tour, slug: "airport-transfer", title: "Airport Transfers", pricingType: "PER_TRIP", basePriceCents: 130000 }} />
      <PackageCard tour={{ ...tour, slug: "private-custom-tour", title: "Private & Custom Tours", pricingType: "CUSTOM", basePriceCents: 0 }} /></>);
    const text = document.body.textContent?.replace(/\s+/g, " ") ?? "";
    expect(text).toContain("R 1 300");
    expect(text).toContain("per trip");
    expect(text).toContain("Quoteon request");
    expect(text).not.toContain("R 0");
  });

  it("tells visitors the summary and group rule before they open the tour, and how smaller groups are handled", () => {
    renderIn(<><PackageCard tour={tour} />
      <PackageCard tour={{ ...tour, slug: "airport-transfer", title: "Airport Transfers", pricingType: "PER_TRIP", minimumGuests: 1, groupSizeLabel: "Any group size" }} /></>);
    const [peninsula, transfer] = screen.getAllByRole("article");
    expect(peninsula).toHaveTextContent("Coastal drives, Cape Point and the penguins.");
    expect(peninsula).toHaveTextContent("Minimum 5 guests");
    expect(peninsula).toHaveTextContent("Fewer than 5? Ask for a private quote");
    expect(transfer).toHaveTextContent("Any group size");
    expect(transfer).not.toHaveTextContent("Ask for a private quote");
  });

  it("homepage package cards send enquiries rather than offering an online booking", () => {
    renderIn(<PackageCard tour={tour} />);
    expect(screen.getByRole("link", { name: "View Details" })).toHaveAttribute("href", "/experiences/cape-peninsula-journey");
    expect(screen.getByRole("link", { name: "Enquire about Cape Peninsula & Cape Point" })).toHaveAttribute("href", "/experiences/cape-peninsula-journey#enquire");
    expect(screen.queryByRole("link", { name: /Book Online/ })).not.toBeInTheDocument();
  });
});
