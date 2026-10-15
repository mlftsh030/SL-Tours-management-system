import { Search } from "lucide-react";
import { useMemo } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listTours } from "../../lib/api";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { EmptyState, ErrorState, LoadingState } from "../../components/StateBlocks";
import { PageBanner } from "../../components/PageBanner";
import { PackageCard } from "./PackageCard";
import type { TourCategory } from "../../types/api";

const filters: Array<{ value: string; label: string; categories: TourCategory[] }> = [
  { value: "all", label: "All tours", categories: [] },
  { value: "city-culture", label: "City & Culture", categories: ["CITY", "CULTURE"] },
  { value: "coast-nature", label: "Coast & Nature", categories: ["PENINSULA", "NATURE", "GARDEN_ROUTE"] },
  { value: "wine-food", label: "Wine & Food", categories: ["WINELANDS"] },
  { value: "safari", label: "Safari", categories: ["SAFARI"] },
  { value: "multi-day", label: "Multi-Day", categories: ["GARDEN_ROUTE"] },
  { value: "private", label: "Private", categories: ["PRIVATE"] },
  { value: "transfer", label: "Transfers", categories: ["TRANSFER"] }
];

const banners = {
  all: {
    title: "Our Tours",
    subtitle: "Day trips, safaris, and journeys with a local team",
    image: "/assets/gallery/16.webp"
  },
  private: {
    title: "Private Tours",
    subtitle: "Tailor-made days at your own pace, with no waiting for other guests",
    image: "/assets/gallery/27.webp"
  },
  transfer: {
    title: "Transfers",
    subtitle: "Reliable airport and private transfers across Cape Town",
    image: "/assets/gallery/1.webp"
  }
};

export function ExperiencesPage({ focus }: { focus?: "private" | "transfer" }) {
  const banner = banners[focus ?? "all"];
  useDocumentTitle(focus ? `${banner.title} | SL Tours Cape Town` : "Cape Town Tours & Prices | SL Tours",
    "Guided Cape Town tours with published prices: Cape Point, the city, a township visit, the Winelands, the Garden Route, an Aquila safari, the West Coast, airport transfers and private tours.");
  // Category and search live in the URL (?category=wine-food&q=penguins) so a filtered list can be shared, reloaded and
  // reached with Back. Unknown categories fall back to the page's default; other parameters are kept.
  const defaultFilter = focus ?? "all";
  const [searchParams, setSearchParams] = useSearchParams();
  const requested = searchParams.get("category");
  const filter = requested && filters.some(f => f.value === requested) ? requested : defaultFilter;
  const query = searchParams.get("q") ?? "";
  const update = (changes: Record<string, string | null>, replace: boolean) => setSearchParams(current => {
    const next = new URLSearchParams(current);
    Object.entries(changes).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
    return next;
  }, { replace });
  // Choosing a category adds a history entry; typing replaces it so Back does not step through every keystroke.
  const setFilter = (value: string) => update({ category: value === defaultFilter ? null : value }, false);
  const setQuery = (value: string) => update({ q: value || null }, true);
  const filtered = filter !== defaultFilter || query.trim() !== "";
  const clearFilters = () => update({ category: null, q: null }, false);
  const tours = useQuery({ queryKey: ["tours"], queryFn: () => listTours() });

  const filteredTours = useMemo(() => {
    return (tours.data ?? []).filter((tour) => {
      const haystack = [tour.title, tour.shortDescription, tour.category, ...tour.tags].join(" ").toLowerCase();
      const matchesQuery = haystack.includes(query.trim().toLowerCase());
      const categories = filters.find((item) => item.value === filter)?.categories ?? [];
      const matchesFilter = categories.length === 0 || categories.includes(tour.category);
      return matchesQuery && matchesFilter;
    });
  }, [filter, query, tours.data]);

  return (
    <>
      <PageBanner title={banner.title} subtitle={banner.subtitle} image={banner.image} />
      <section className="section tours-section">
        {focus === "private" && (
          <aside className="private-trip-cta">
            <div>
              <p className="eyebrow">By the hour</p>
              <h2>Need a vehicle and driver for your own plans?</h2>
              <p>Book a Private Trip: choose your time, see the available vehicles and the full price before you request.</p>
            </div>
            <Link className="button button-primary" to="/book-private-trip">Book a Private Trip</Link>
          </aside>
        )}
        <div className="tours-toolbar">
          <div className="filter-row" role="group" aria-label="Filter tours">
            {filters.map((item) => (
              <button
                key={item.value}
                type="button"
                className={filter === item.value ? "chip chip-active" : "chip"}
                aria-pressed={filter === item.value}
                onClick={() => setFilter(item.value)}
              >
                {item.label}
              </button>
            ))}
          </div>
          <label className="search-control">
            <Search aria-hidden="true" />
            <span className="sr-only">Search tours</span>
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search tours"
            />
          </label>
        </div>
        {tours.isLoading && <LoadingState label="Loading tours" />}
        {tours.isError && <ErrorState message={(tours.error as Error).message} />}
        {tours.data && (
          <p className="result-count" aria-live="polite">
            {filteredTours.length} {filteredTours.length === 1 ? "tour" : "tours"}
            {filtered && filteredTours.length > 0 && <button type="button" className="text-link result-clear" onClick={clearFilters}>Clear filters</button>}
          </p>
        )}
        {tours.data && filteredTours.length === 0 && <EmptyState message="No tours match these filters.">
          <button type="button" className="button button-secondary" onClick={clearFilters}>Clear filters</button>
        </EmptyState>}
        <div className="package-grid">
          {filteredTours.map((tour) => (
            <PackageCard key={tour.slug} tour={tour} />
          ))}
        </div>
      </section>
    </>
  );
}
