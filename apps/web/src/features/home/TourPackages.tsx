import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useRef } from "react";
import { Link } from "react-router-dom";
import { EmptyState, ErrorState, LoadingState } from "../../components/StateBlocks";
import { listTours } from "../../lib/api";
import { PackageCard } from "../tours/PackageCard";

export function TourPackages() {
  const tours = useQuery({ queryKey: ["tours", "featured"], queryFn: () => listTours(true) });
  const track = useRef<HTMLDivElement>(null);

  const scroll = (direction: 1 | -1) => {
    const element = track.current;
    if (!element) {
      return;
    }
    const card = element.querySelector<HTMLElement>(".package-card");
    element.scrollBy({ left: direction * ((card?.offsetWidth ?? 320) + 24), behavior: "smooth" });
  };

  return (
    <section className="packages-band" aria-labelledby="packages-title">
      <div className="packages-inner">
        <div className="packages-heading">
          <h2 id="packages-title">Popular Tour Packages</h2>
          <Link to="/experiences">View All Tours</Link>
        </div>
        {tours.isLoading && <LoadingState label="Loading tour packages" />}
        {tours.isError && <ErrorState message={(tours.error as Error).message} />}
        {tours.data?.length === 0 && <EmptyState message="No tour packages are currently published." />}
        {tours.data && tours.data.length > 0 && (
          <div className="packages-carousel">
            <button
              className="slider-arrow slider-arrow-prev"
              type="button"
              onClick={() => scroll(-1)}
              aria-label="Previous tours"
            >
              <ChevronLeft aria-hidden="true" />
            </button>
            <div className="packages-track" ref={track}>
              {tours.data.map((tour) => (
                <PackageCard key={tour.slug} tour={tour} />
              ))}
            </div>
            <button
              className="slider-arrow slider-arrow-next"
              type="button"
              onClick={() => scroll(1)}
              aria-label="Next tours"
            >
              <ChevronRight aria-hidden="true" />
            </button>
          </div>
        )}
      </div>
    </section>
  );
}
