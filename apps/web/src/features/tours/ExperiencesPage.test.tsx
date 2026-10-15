import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ExperiencesPage } from "./ExperiencesPage";
import type { TourSummary } from "../../types/api";

const tour = (slug: string, title: string, category: TourSummary["category"]): TourSummary => ({ slug, title, shortDescription: `${title} summary`,
  duration: "Full day", basePriceCents: 100000, pricingType: "PER_PERSON", category, featured: false, heroImage: "/x.jpg", cardNote: null, tags: [],
  heroImageAlt: title, groupSizeLabel: null, minimumGuests: 1 });
const tours = [tour("cape-winelands-escape", "Cape Winelands Tour", "WINELANDS"), tour("cape-peninsula-journey", "Cape Peninsula", "PENINSULA"),
  tour("cape-town-essentials", "City & Heritage", "CITY")];

function Url() { const l = useLocation(); return <output data-testid="url">{l.search}</output>; }
function renderAt(url: string) {
  vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response(JSON.stringify(tours), { headers: { "Content-Type": "application/json" } }))));
  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter initialEntries={[url]}><ExperiencesPage /><Url /></MemoryRouter></QueryClientProvider>);
}
afterEach(() => vi.unstubAllGlobals());

describe("tour filters", () => {
  it("restores category and search from a shared link, ignoring surrounding spaces", async () => {
    renderAt("/experiences?category=wine-food&q=%20cape%20&utm_source=hotel");
    expect(await screen.findByRole("heading", { name: "Cape Winelands Tour" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Cape Peninsula" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Wine & Food" })).toHaveAttribute("aria-pressed", "true");
  });

  it("clears filters from the empty state and keeps unrelated parameters", async () => {
    renderAt("/experiences?category=safari&utm_source=hotel");
    await screen.findByText("No tours match these filters.");
    await userEvent.click(screen.getByRole("button", { name: "Clear filters" }));
    expect(await screen.findByRole("heading", { name: "Cape Peninsula" })).toBeInTheDocument();
    expect(screen.getByTestId("url")).toHaveTextContent("?utm_source=hotel");
  });

  it("writes the chosen category to the address and treats an unknown one as all tours", async () => {
    renderAt("/experiences?category=nonsense");
    expect(await screen.findAllByRole("article")).toHaveLength(3);
    await userEvent.click(screen.getByRole("button", { name: "City & Culture" }));
    expect(screen.getByTestId("url")).toHaveTextContent("category=city-culture");
    expect(screen.getAllByRole("article")).toHaveLength(1);
  });
});
