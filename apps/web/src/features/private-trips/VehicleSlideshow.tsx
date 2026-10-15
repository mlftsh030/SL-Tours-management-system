import { useRef, useState, type KeyboardEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { ChevronLeft, ChevronRight, UsersRound } from "lucide-react";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { listVehicleOptions } from "./privateTripApi";
import { capacityText, VehicleOptionImage } from "./VehicleOptionCard";

/**
 * Vehicles customers can request for private trips, in the same carousel and cards as the tour packages. Availability is
 * confirmed for each trip. No automatic movement.
 */
export function VehicleSlideshow() {
  const fleet = useQuery({ queryKey: ["private-trips", "vehicle-options"], queryFn: listVehicleOptions });
  const track = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(0);
  if (fleet.isLoading) return <LoadingState label="Loading vehicles" />;
  if (fleet.isError) return <ErrorState message="Vehicles could not be loaded. You can still request a private trip." />;
  const vehicles = fleet.data ?? [];
  if (vehicles.length === 0) return <p>Our vehicles are being listed. <Link to="/book-private-trip">Tell us about your trip</Link> and we will suggest the right vehicle.</p>;

  const cards = () => [...(track.current?.querySelectorAll<HTMLElement>(".package-card") ?? [])];
  const show = (index: number) => {
    const list = cards();
    const next = (index + list.length) % list.length;
    const target = list[next];
    setActive(next);
    if (target && track.current?.scrollTo) track.current.scrollTo({ left: target.offsetLeft - list[0].offsetLeft, behavior: "smooth" });
  };
  const onScroll = () => {
    const list = cards();
    if (!track.current || list.length === 0) return;
    const step = list.length > 1 ? list[1].offsetLeft - list[0].offsetLeft : 1;
    setActive(Math.min(list.length - 1, Math.round(track.current.scrollLeft / step)));
  };
  const onKey = (event: KeyboardEvent) => {
    if (event.key === "ArrowLeft") { event.preventDefault(); show(active - 1); }
    if (event.key === "ArrowRight") { event.preventDefault(); show(active + 1); }
  };

  return <div role="region" aria-roledescription="carousel" aria-label="Vehicles for private trips" onKeyDown={onKey}>
    <div className="packages-carousel">
    <button className="slider-arrow slider-arrow-prev" type="button" onClick={() => show(active - 1)} aria-label="Previous vehicle"><ChevronLeft aria-hidden="true" /></button>
    <div className="packages-track" ref={track} onScroll={onScroll}>
      {vehicles.map((v, i) => <article key={v.slug} className="package-card" role="group" aria-roledescription="slide" aria-label={`${i + 1} of ${vehicles.length}: ${v.name}`}>
        <div className="package-image">
          <VehicleOptionImage option={v} sizes="(max-width: 700px) 85vw, 400px" />
          <span className="package-ribbon">{v.typeLabel}</span>
        </div>
        <div className="package-body">
          <div className="package-heading"><h3>{v.name}</h3></div>
          <p className="package-meta"><UsersRound aria-hidden="true" />{capacityText(v)}</p>
          <p className="activity-card-summary">{v.description}</p>
          <div className="package-actions">
            <Link className="button button-primary" to={`/book-private-trip?vehicle=${encodeURIComponent(v.slug)}`} aria-label={`Enquire about the ${v.name}`}>Enquire</Link>
          </div>
        </div>
      </article>)}
    </div>
    <button className="slider-arrow slider-arrow-next" type="button" onClick={() => show(active + 1)} aria-label="Next vehicle"><ChevronRight aria-hidden="true" /></button>
    </div>
    {vehicles.length > 1 && <div className="carousel-dots" role="group" aria-label="Choose a vehicle">
      {vehicles.map((v, i) => <button key={v.slug} type="button" className={i === active ? "slider-dot slider-dot-active" : "slider-dot"}
        aria-label={`Show ${v.name}`} aria-current={i === active ? "true" : undefined} onClick={() => show(i)} />)}
      <span className="sr-only" aria-live="polite">{active + 1} of {vehicles.length}</span>
    </div>}
    <p className="form-note vehicle-options-note">Availability is confirmed for your date and group. Pictures marked illustrative show the model or
      type of vehicle, not a vehicle owned by SL Tours.</p>
  </div>;
}
