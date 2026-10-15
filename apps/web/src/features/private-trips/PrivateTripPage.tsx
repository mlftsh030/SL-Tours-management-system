import { PrivacyNote } from "../../app/PrivacyPage";
import { ReferralNotice } from "../partners/ReferralNotice";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { CheckCircle2, Info } from "lucide-react";
import { PageBanner } from "../../components/PageBanner";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { ApiRequestError } from "../../lib/api";
import { formatAmount } from "../../lib/format";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { useIdempotencyKey } from "../../lib/useIdempotencyKey";
import { capeTown, localDate, wallClock } from "../../lib/dates";
import { useSession } from "../auth/useSession";
import { listFleet, listVehicleOptions, requestPrivateTrip, requestPrivateTripQuote, searchPrivateTrips, type TripQuery } from "./privateTripApi";
import { VehicleCard } from "./VehicleCard";
import { capacityText, VehicleOptionCard } from "./VehicleOptionCard";
import type { PriceQuote, PricingDisclosures } from "../../types/api";

const durations = Array.from({ length: 23 }, (_, i) => 60 + i * 30);
const hoursLabel = (minutes: number) => `${minutes / 60} ${minutes === 60 ? "hour" : "hours"}`;
const tomorrow = () => localDate(1);

export function PrivateTripPage() {
  useDocumentTitle("Book a Private Trip | SL Tours Cape Town", "Hire a vehicle with a professional SL Tours driver by the hour. See available vehicles and the full price before you request.");
  const session = useSession();
  const customer = session.data?.role === "CUSTOMER" ? session.data : null;
  const [params, setParams] = useSearchParams();
  // Things to Do pages link here with ?destination= so the place is carried into the enquiry.
  const [trip, setTrip] = useState({ date: tomorrow(), time: "09:00", durationMinutes: 240, passengers: 2, pickup: "", destination: params.get("destination") ?? "" });
  const [query, setQuery] = useState<TripQuery | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [mode, setMode] = useState<"book" | "quote">("book");
  const [contact, setContact] = useState({ name: "", email: "", phone: "", requests: "" });
  const submission = useIdempotencyKey();
  const [notice, setNotice] = useState("");
  const [unavailable, setUnavailable] = useState("");
  const fleet = useQuery({ queryKey: ["private-trips", "fleet"], queryFn: listFleet });
  const options = useQuery({ queryKey: ["private-trips", "vehicle-options"], queryFn: listVehicleOptions });
  // A vehicle chosen here or elsewhere (for example the homepage slideshow) arrives as ?vehicle=SLUG.
  const wanted = params.get("vehicle");
  const chosen = wanted ? options.data?.find(o => o.slug === wanted) ?? null : null;
  const choose = (slug: string | null) => {
    const next = new URLSearchParams(params);
    if (slug) next.set("vehicle", slug); else next.delete("vehicle");
    setParams(next, { replace: true }); setSelected(null); setUnavailable("");
    if (slug) document.getElementById("trip-form")?.scrollIntoView?.({ behavior: "smooth", block: "start" });
  };
  const search = useQuery({ queryKey: ["private-trips", "search", query], queryFn: () => searchPrivateTrips(query!), enabled: Boolean(query), retry: false });
  // Results, the selected vehicle and its price belong to the last search. Editing any field that decides availability
  // or price makes them stale: nothing from the old search can be shown as current or submitted until the search is updated.
  const stale = query !== null && (trip.date !== query.date || trip.time !== query.time
    || trip.durationMinutes !== query.durationMinutes || trip.passengers !== query.passengers);
  const vehicle = stale ? null : search.data?.vehicles.find(v => v.id === selected) ?? null;
  // Keep the chosen vehicle in the enquiry, visibly and editably, so a quote request still says which vehicle was wanted.
  useEffect(() => {
    if (chosen) setContact(c => !c.requests || c.requests.startsWith("Preferred vehicle:")
      ? { ...c, requests: `Preferred vehicle: ${chosen.name} (${capacityText(chosen).toLowerCase()})` } : c);
  }, [chosen?.slug]); // eslint-disable-line react-hooks/exhaustive-deps
  // After a search, pre-select only the model the visitor chose (a fleet vehicle linked to it). Vehicles of the same
  // category are named as alternatives the visitor must choose themselves; nothing else is substituted silently.
  const alternatives = chosen && search.data
    ? search.data.vehicles.filter(v => v.optionSlug !== chosen.slug && v.category === chosen.category).map(v => v.id) : [];
  useEffect(() => {
    if (!chosen || !search.data) return;
    const match = search.data.vehicles.find(v => v.optionSlug === chosen.slug && v.capacity >= search.data!.passengers);
    if (match) { setSelected(match.id); setMode(match.quote ? "book" : "quote"); setUnavailable(""); return; }
    const similar = search.data.vehicles.filter(v => v.category === chosen.category).map(v => v.name);
    setUnavailable(similar.length
      ? `No ${chosen.name} is listed as free for that time and group size. A similar vehicle is free: ${similar.join(", ")}. Choose it below if it suits you, or request a quote for the ${chosen.name}.`
      : `No ${chosen.name} or similar vehicle is listed as free for that time and group size. Choose another vehicle below, or request a quote and we will look for one.`);
  }, [search.data, chosen?.slug]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (customer) setContact(c => ({ ...c, name: customer.name ?? "", email: customer.email ?? "" })); }, [customer?.email]);

  const book = useMutation({
    mutationFn: (quote: PriceQuote) => {
      const content = { vehicleId: selected, date: query!.date, startTime: query!.time,
        durationMinutes: query!.durationMinutes, passengers: query!.passengers, pickupLocation: trip.pickup, destination: trip.destination,
        specialRequests: contact.requests || null, customerName: contact.name, customerEmail: contact.email, customerPhone: contact.phone };
      // Accepting a refreshed price confirms the same trip, so the price is not part of what identifies the request.
      return requestPrivateTrip({ ...content, expectedTotalCents: quote.totalCents, idempotencyKey: submission.keyFor(content) });
    },
    onError: async error => {
      // Price or availability changed: refresh the options so the customer sees the current backend figures.
      if (error instanceof ApiRequestError && error.status === 409) { setNotice(error.message); await search.refetch(); }
    }
  });
  const quoteRequest = useMutation({ mutationFn: () => {
    const content = { date: trip.date, startTime: trip.time,
      durationMinutes: trip.durationMinutes, passengers: trip.passengers, pickupLocation: trip.pickup, destination: trip.destination,
      preferredCategory: vehicle?.category ?? chosen?.category ?? null, preferredVehicle: chosen?.slug ?? null, specialRequests: contact.requests || null, customerName: contact.name,
      customerEmail: contact.email, customerPhone: contact.phone || null };
    return requestPrivateTripQuote({ ...content, idempotencyKey: submission.keyFor(content) });
  } });

  const update = (field: keyof typeof trip) => (event: { target: { value: string } }) =>
    setTrip(c => ({ ...c, [field]: field === "durationMinutes" || field === "passengers" ? Number(event.target.value) : event.target.value }));
  function findVehicles(event: FormEvent) {
    event.preventDefault(); setSelected(null); setNotice(""); book.reset(); setUnavailable("");
    // A new search is a new request; retries of the same request keep their key.
    submission.reset();
    setQuery({ date: trip.date, time: trip.time, durationMinutes: trip.durationMinutes, passengers: trip.passengers });
  }
  function submit(event: FormEvent) {
    event.preventDefault(); setNotice("");
    if (book.isPending || quoteRequest.isPending) return;
    if (mode === "quote") quoteRequest.mutate();
    else if (vehicle?.quote) book.mutate(vehicle.quote);
  }

  if (book.isSuccess) return <section className="section confirmation-page">
    <CheckCircle2 aria-hidden="true" />
    <p className="eyebrow">Private trip request received</p>
    <h1>{book.data.reference}</h1>
    <p>{book.data.message}</p>
    <dl>
      <div><dt>Vehicle</dt><dd>{book.data.vehicleName}</dd></div>
      <div><dt>When</dt><dd>{capeTown(book.data.startsAt, { dateStyle: "full", timeStyle: "short" })} (Cape Town time), {hoursLabel(book.data.quote.requestedMinutes)}</dd></div>
      <div><dt>Quoted total</dt><dd>{formatAmount(book.data.quote.totalCents)}</dd></div>
    </dl>
    <PriceBreakdown quote={book.data.quote} />
    {customer && <a className="button button-primary" href="/account">View my bookings</a>}
  </section>;
  if (quoteRequest.isSuccess) return <section className="section confirmation-page">
    <CheckCircle2 aria-hidden="true" />
    <p className="eyebrow">Quote request received</p>
    <h1>{quoteRequest.data.reference}</h1>
    <p>{quoteRequest.data.message}</p>
  </section>;

  const today = localDate();
  return <>
    <PageBanner title="Private Trips" subtitle="A vehicle and professional driver by the hour, for your own itinerary" image="/assets/gallery/27.webp" />
    <section className="section private-trip">
      <p className="form-note">Looking for a guided day instead? See our <Link to="/private-tours">private tours</Link> and <Link to="/transfers">airport transfers</Link>.</p>
      <form id="trip-form" className="form-surface private-trip-search" onSubmit={findVehicles}>
        <h2>Plan your trip</h2>
        {chosen && <p className="private-trip-note" role="status"><Info aria-hidden="true" />
          <span>You chose the <strong>{chosen.name}</strong> ({capacityText(chosen).toLowerCase()}). Enter your date, time and group to check availability.{" "}
          <button type="button" className="text-link" onClick={() => choose(null)}>Choose another vehicle</button></span></p>}
        <div className="form-grid">
          <label><span>Date</span><input type="date" min={today} required value={trip.date} onChange={update("date")} /></label>
          <label><span>Start time (Cape Town)</span><input type="time" required value={trip.time} onChange={update("time")} /></label>
          <label><span>How long?</span><select value={trip.durationMinutes} onChange={update("durationMinutes")}>{durations.map(m => <option key={m} value={m}>{hoursLabel(m)}</option>)}</select></label>
          <label><span>Passengers</span><input type="number" min={1} max={80} required value={trip.passengers} onChange={update("passengers")} /></label>
          <label className="full-field"><span>Pickup location</span><input required maxLength={240} value={trip.pickup} onChange={update("pickup")} placeholder="Hotel, address or airport" /></label>
          <label className="full-field"><span>Destination or planned itinerary</span><textarea required rows={3} maxLength={1200} value={trip.destination} onChange={update("destination")} placeholder="e.g. Chapman's Peak, Boulders Beach and Cape Point, back to the hotel" /></label>
        </div>
        <button className="button button-primary" type="submit">{query ? "Update available vehicles" : "Show available vehicles"}</button>
      </form>

      {!query && <>
        <div className="private-trip-heading" id="vehicles"><p className="eyebrow">Our vehicles</p><h2>Choose the right vehicle for your group</h2>
          <p>Passenger numbers exclude the driver. Availability is confirmed for your date and group before anything is booked.</p></div>
        {options.isLoading && <LoadingState label="Loading vehicles" />}{options.isError && <ErrorState message={options.error.message} />}
        <div className="vehicle-grid" role="group" aria-label="Vehicles you can request">{options.data?.map(o =>
          <VehicleOptionCard key={o.slug} option={o} selected={o.slug === chosen?.slug} onSelect={() => choose(o.slug === chosen?.slug ? null : o.slug)} />)}</div>
        <p className="form-note">Pictures marked illustrative show the model or type of vehicle, not a vehicle owned by SL Tours.</p>
        {fleet.data && <Disclosures disclosures={fleet.data.disclosures} />}
      </>}

      {query && stale && <p className="private-trip-note" role="status"><Info aria-hidden="true" />
        You changed your trip details. Select “Update available vehicles” to see which vehicles are free and their price for the new details.</p>}

      {query && !stale && <>
        <div className="private-trip-heading"><p className="eyebrow">Available for your trip</p>
          <h2>{wallClock(query.date, query.time, { dateStyle: "full", timeStyle: "short" })} Cape Town time · {hoursLabel(query.durationMinutes)} · {query.passengers} passengers</h2></div>
        {unavailable && <p className="private-trip-note" role="alert"><Info aria-hidden="true" />{unavailable}
          <button className="text-link" type="button" onClick={() => setMode("quote")}>Request a quote</button></p>}
        {search.isLoading && <LoadingState label="Checking availability" />}{search.isError && <ErrorState message={search.error.message} />}
        {search.data && !search.data.vehicles.length && <div className="form-surface"><p>No listed vehicle is free for that time and group size. Try another time, or ask us for a quote and we will find a solution.</p>
          <button className="button button-secondary" type="button" onClick={() => setMode("quote")}>Request a quote</button></div>}
        <div className="vehicle-grid">{search.data?.vehicles.map(v => <VehicleCard key={v.id} vehicle={v} selected={v.id === selected}
          note={chosen && alternatives.includes(v.id) ? `Alternative to the ${chosen.name} you chose` : undefined}
          actionLabel={v.quote ? "Choose this vehicle" : "Request a quote"} onSelect={() => { setSelected(v.id); setMode(v.quote ? "book" : "quote"); setNotice(""); }} />)}</div>
        {search.data && <Disclosures disclosures={search.data.disclosures} />}
      </>}

      {(vehicle || mode === "quote") && <div className="booking-grid private-trip-checkout">
        <form className="form-surface" onSubmit={submit}>
          <h2>{mode === "quote" ? "Request a quote" : "Your details"}</h2>
          <p className="private-trip-note"><Info aria-hidden="true" />{mode === "quote"
            ? "We will price your itinerary and reply with a quote. Nothing is booked yet."
            : "This is a booking request. SL Tours confirms the vehicle and assigns your driver before it becomes a confirmed booking."}</p>
          <div className="form-grid">
            <label><span>Name</span><input required maxLength={160} autoComplete="name" readOnly={Boolean(customer)} value={contact.name} onChange={e => setContact(c => ({ ...c, name: e.target.value }))} /></label>
            <label><span>Email</span><input required type="email" maxLength={180} autoComplete="email" readOnly={Boolean(customer)} value={contact.email} onChange={e => setContact(c => ({ ...c, email: e.target.value }))} /></label>
            <label className="full-field"><span>Mobile number</span><input required={mode === "book"} type="tel" maxLength={80} autoComplete="tel" value={contact.phone} onChange={e => setContact(c => ({ ...c, phone: e.target.value }))} placeholder="Your driver will use this on the day" /></label>
            <label className="full-field"><span>Special requests <em className="optional">(optional)</em></span><textarea rows={3} maxLength={1200} value={contact.requests} onChange={e => setContact(c => ({ ...c, requests: e.target.value }))} placeholder="Child seats, accessibility, luggage, stops" /></label>
          </div>
          {notice && <ErrorState message={`${notice} The options above have been refreshed.`} />}
          {book.isError && !notice && <ErrorState message={book.error.message} />}
          {quoteRequest.isError && <ErrorState message={quoteRequest.error.message} />}
          <ReferralNotice />
          <PrivacyNote />
          <button className="button button-primary" type="submit" disabled={book.isPending || quoteRequest.isPending || (mode === "book" && !vehicle?.quote)}>
            {book.isPending || quoteRequest.isPending ? "Sending…" : mode === "quote" ? "Send quote request" : "Request this trip"}</button>
          {mode === "book" && <button type="button" className="text-link private-trip-switch" onClick={() => { setMode("quote"); submission.reset(); }}>My itinerary needs a custom quote</button>}
        </form>
        {vehicle?.quote && mode === "book" && <aside className="booking-panel">
          {vehicle.imageUrl && <img src={vehicle.imageUrl} alt="" />}
          <h2>{vehicle.name}</h2>
          <p>{trip.pickup || "Your pickup"} · {hoursLabel(query!.durationMinutes)}</p>
          <PriceBreakdown quote={vehicle.quote} />
          <p>Prices are in South African rand and calculated by SL Tours. Payment instructions follow confirmation.</p>
        </aside>}
      </div>}
    </section>
  </>;
}

function PriceBreakdown({ quote }: { quote: PriceQuote }) {
  const rows = useMemo(() => {
    if (quote.rateSource === "MANUAL") return [["Quoted total", formatAmount(quote.totalCents)]];
    const list: string[][] = [[`Hourly rate${quote.sampleRate ? " (sample)" : ""}`, formatAmount(quote.hourlyRateCents!)],
      ["Requested time", hoursLabel(quote.requestedMinutes)]];
    if (quote.minimumApplied) list.push(["Minimum charge", hoursLabel(quote.minimumMinutes)]);
    list.push([`Billable time${quote.roundedUp ? " (rounded up)" : ""}`, hoursLabel(quote.billableMinutes)],
      [`${formatAmount(quote.hourlyRateCents!)} × ${quote.billableMinutes / 60} h`, formatAmount(quote.baseCents)]);
    quote.fees.forEach(f => list.push([f.name + (f.type === "PER_BILLABLE_HOUR" ? ` (${formatAmount(f.amountCents)}/h)` : ""), formatAmount(f.totalCents)]));
    return list;
  }, [quote]);
  return <dl className="price-breakdown">{rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
    <div className="price-total"><dt>Total</dt><dd>{formatAmount(quote.totalCents)}</dd></div></dl>;
}

function Disclosures({ disclosures }: { disclosures: PricingDisclosures }) {
  return <aside className="private-trip-disclosures" aria-label="How private trips are priced">
    <h3>How pricing works</h3>
    <ul>
      <li>Price = hourly rate × billable hours. Each vehicle has a minimum booking time.</li>
      <li>Time is charged in {disclosures.billingIncrementMinutes === 60 ? "whole hours" : `${disclosures.billingIncrementMinutes}-minute steps`}, rounded up.</li>
      <li>{disclosures.overtimeHourlyRateCents ? `Extra time on the day: ${formatAmount(disclosures.overtimeHourlyRateCents)} per hour.` : "Charges for extra time on the day are not published yet; SL Tours confirms them with you before your trip is confirmed."}</li>
      <li>{disclosures.includedKmPerHour && disclosures.excessKmRateCents
        ? `Includes ${disclosures.includedKmPerHour} km per billable hour; further distance is ${formatAmount(disclosures.excessKmRateCents)} per km.`
        : "Distance limits and any charge for extra distance are not published yet; SL Tours confirms them with you before your trip is confirmed."}</li>
      {disclosures.fees.map(f => <li key={f.name}>{f.name}: {formatAmount(f.amountCents)} {f.type === "FIXED" ? "per trip" : "per billable hour"}.</li>)}
      {disclosures.sampleRatesShown && <li><strong>Development environment:</strong> prices marked “sample rate” are placeholders, not live SL Tours prices.</li>}
    </ul>
  </aside>;
}
