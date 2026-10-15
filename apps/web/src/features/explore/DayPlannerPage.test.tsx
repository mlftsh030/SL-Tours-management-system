import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CurrencyProvider } from "../../lib/currency";
import { activityCard } from "../../test/fixtures";
import { DayPlannerPage } from "./DayPlannerPage";
import { ExplorePage } from "./ExplorePage";

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
const card = (slug: string, category: string, extra = {}) => activityCard(slug, { category: category as never, ...extra });
const wrap = (ui: React.ReactNode, path = "/") => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <CurrencyProvider><MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter></CurrencyProvider></QueryClientProvider>);
afterEach(() => vi.unstubAllGlobals());

describe("Explore Cape Town", () => {
  it("opens each region with six listings and shows the rest on request, but shows every match when filtered", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => url.includes("/api/v1/activities")
      ? json(Array.from({ length: 8 }, (_, i) => card(`place-${i}`, "NATURE", { title: `Place ${i}`, themes: i === 7 ? ["FAMILY"] : ["SCENIC"] })))
      : Promise.reject(new Error(url))));
    wrap(<ExplorePage />, "/explore");
    await screen.findByRole("link", { name: "Place 0" });
    expect(screen.queryByRole("link", { name: "Place 6" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Show all 8 in Cape Town" }));
    expect(screen.getByRole("link", { name: "Place 7" })).toBeInTheDocument();
    cleanup();

    wrap(<ExplorePage />, "/explore?theme=SCENIC");
    await screen.findByRole("link", { name: "Place 6" });
    expect(screen.queryByRole("link", { name: "Place 7" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Show all/ })).not.toBeInTheDocument();
  });

  it("groups listings by region with Cape Town first, keeps events separate, shows pictures and no prices, and filters by category", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => url.includes("/api/v1/activities") ? json([
      card("caves", "MUSEUM_HISTORY", { title: "Cango Caves", themes: ["HERITAGE", "FAMILY"], region: "GARDEN_ROUTE_KLEIN_KAROO", regionLabel: "Garden Route & Klein Karoo",
        town: "Oudtshoorn", area: null, areaLabel: null }),
      card("ice-rink", "ICE_SKATING", { title: "Ice rink", themes: ["FAMILY"] }),
      card("concerts", "CINEMA", { title: "Summer concerts", themes: ["EVENTS"], kind: "EVENT", eventSchedule: "Summer season, November to March" }),
      card("gallery", "ART_CULTURE", { title: "A gallery", themes: ["ART"], image: { ...activityCard("x").image!, depiction: "ILLUSTRATIVE" } })])
      : Promise.reject(new Error(url))));
    wrap(<ExplorePage />, "/explore");
    await screen.findByRole("link", { name: "Cango Caves" });
    const headings = screen.getAllByRole("heading", { level: 2 });
    expect(headings.map(h => h.textContent)).toEqual(["Put a day together in Cape Town", "Cape Town", "Garden Route & Klein Karoo", "Events and performances"]);
    expect(screen.getAllByRole("article")).toHaveLength(4);
    expect(screen.getAllByRole("article").every(a => a.querySelector("img"))).toBe(true);
    expect(screen.getByText("Illustrative image—actual location may differ")).toBeInTheDocument();
    expect(screen.getByText("Summer season, November to March")).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/R\s?\d|from, per adult|Free/);
    expect(screen.getByRole("link", { name: "Plan a visit: Ice rink" })).toHaveAttribute("href", "/explore/plan?include=ice-rink");
    expect(screen.getByRole("link", { name: "Enquire about transport to Cango Caves" })).toHaveAttribute("href", "/book-private-trip?destination=Cango%20Caves");
    await userEvent.click(screen.getByRole("button", { name: "History & heritage" }));
    expect(screen.getAllByRole("article")).toHaveLength(1);
    expect(screen.getByText(/^1 listing/)).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText("Region"), "CAPE_TOWN");
    expect(screen.getByText("Nothing matches these filters.")).toBeInTheDocument();
  });

  it("sends the travellers, residency and budget, and shows unknown costs as unverified", async () => {
    const bodies: Array<Record<string, unknown>> = [];
    vi.stubGlobal("fetch", vi.fn((url: string, options?: RequestInit) => {
      if (url.endsWith("/csrf")) return json({ token: "t", headerName: "X-CSRF-TOKEN" });
      if (url.includes("/api/v1/activities")) return json([card("aquarium", "WILDLIFE")]);
      if (url.endsWith("/api/v1/itineraries")) {
        bodies.push(JSON.parse(String(options?.body)));
        return json({ date: "2026-11-02", travellers: "2 adults, children aged 4, 9", transport: [], food: [],
          stops: [{ order: 1, type: "ACTIVITY", slug: "cape-point", title: "Cape Point", area: "CAPE_POINT", areaLabel: "Cape Point", start: "10:00:00", end: "13:00:00",
            travelMinutes: 90, notes: [], costs: [{ label: "Admission", quantity: 4, unitAmountCents: null, amountCents: null, kind: "UNKNOWN",
              detail: "Published prices end on 2026-10-31; prices for 2026-11-02 are not verified yet", sourceUrl: null, verifiedOn: null }],
            cautions: ["Participation limits (such as age or height) are not published"] }],
          totals: { verifiedCents: 0, assumptionCents: 0, estimatedCents: 0, knownTotalCents: 0, unknownItems: 1 }, budgetCents: 250000,
          verdict: "CANNOT_CONFIRM", verdictMessage: "We can't confirm this fits your budget.", assumptions: ["Travel times are planning estimates"],
          exclusions: ["food and drinks"], warnings: [], skipped: [],
          confidence: { pricesConfirmed: false, scheduleConfirmed: true, suitabilityConfirmed: false, reasons: [] } });
      }
      return Promise.reject(new Error(url));
    }));
    wrap(<DayPlannerPage />, "/explore/plan?include=aquarium");
    expect(await screen.findByText(/Your plan will include aquarium/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/Children's ages/), "4, 9");
    await userEvent.type(screen.getByLabelText(/Total budget/), "2500");
    await userEvent.selectOptions(screen.getByLabelText("Travellers are"), "SA_RESIDENT");
    await userEvent.click(screen.getByRole("button", { name: "Plan my day" }));
    expect(await screen.findByText("We can't confirm this fits your budget.")).toBeInTheDocument();
    expect(screen.getByText("Not verified")).toBeInTheDocument();
    expect(screen.getByText(/not verified yet/)).toBeInTheDocument();
    expect(screen.getByText(/Participation limits \(such as age or height\) are not published/)).toBeInTheDocument();
    expect(screen.getByText("Suitability: some participation limits are not published")).toBeInTheDocument();
    expect(bodies[0]).toMatchObject({ adults: 2, childAges: [4, 9], residency: "SA_RESIDENT", budgetCents: 250000, foodPerPersonCents: null, mustInclude: ["aquarium"] });
  });

  it("pre-fills the planner from the assistant's fallback link and ignores invalid values", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => url.includes("/api/v1/activities") ? json([card("aquarium", "WILDLIFE")]) : Promise.reject(new Error(url))));
    wrap(<DayPlannerPage />, "/explore/plan?date=2026-11-07&start=10:00&adults=3&children=6,11&residency=SA_RESIDENT&area=WATERFRONT"
      + "&transport=SL_TOURS_PRIVATE_VEHICLE&interests=WILDLIFE,NOPE&budget=4000&stops=9");
    expect(await screen.findByLabelText("Date")).toHaveValue("2026-11-07");
    expect(screen.getByLabelText("Start")).toHaveValue("10:00");
    expect(screen.getByLabelText("Adults")).toHaveValue(3);
    expect(screen.getByLabelText(/Children's ages/)).toHaveValue("6, 11");
    expect(screen.getByLabelText("Travellers are")).toHaveValue("SA_RESIDENT");
    expect(screen.getByLabelText("Getting around")).toHaveValue("SL_TOURS_PRIVATE_VEHICLE");
    expect(screen.getByLabelText(/Total budget/)).toHaveValue("4000");
    expect(screen.getByLabelText("Number of stops")).toHaveValue("3"); // 9 is not allowed
    expect(await screen.findByRole("checkbox", { name: "Wildlife" })).toBeChecked();
  });
});
