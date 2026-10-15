import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { formatAmount } from "../../lib/format";
import { ResourcePicker } from "./AssignmentsPanel";
import { confirmPrivateTrip, createAssignment, getPrivateTrip, resourceOptions } from "./fleetApi";
import { assignmentStatusLabels, categoryLabels, dateTime, hours } from "./labels";
import type { OperationsBooking, PriceQuote } from "../../types/api";

const CLOSED = ["CANCELLED", "REFUNDED", "COMPLETED"];

/** Vehicle, driver and (for private trips) price details for a booking under review. */
export function BookingResources({ booking, isAdmin }: { booking: OperationsBooking; isAdmin: boolean }) {
  return booking.bookingType === "PRIVATE_TRIP"
    ? <PrivateTripResources reference={booking.reference} isAdmin={isAdmin} />
    : <TourResources booking={booking} isAdmin={isAdmin} />;
}

export function QuoteBreakdown({ quote }: { quote: PriceQuote }) {
  if (quote.rateSource === "MANUAL") return <dl className="price-breakdown"><div><dt>Manually quoted total</dt><dd>{formatAmount(quote.totalCents)}</dd></div></dl>;
  return <dl className="price-breakdown">
    <div><dt>Hourly rate{quote.sampleRate ? " (sample rate)" : ""}</dt><dd>{formatAmount(quote.hourlyRateCents!)}</dd></div>
    <div><dt>Requested</dt><dd>{hours(quote.requestedMinutes)}</dd></div>
    {quote.minimumApplied && <div><dt>Minimum charge</dt><dd>{hours(quote.minimumMinutes)}</dd></div>}
    <div><dt>Billable{quote.roundedUp ? " (rounded up)" : ""}</dt><dd>{hours(quote.billableMinutes)}</dd></div>
    <div><dt>Base price</dt><dd>{formatAmount(quote.baseCents)}</dd></div>
    {quote.fees.map(fee => <div key={fee.name}><dt>{fee.name}</dt><dd>{formatAmount(fee.totalCents)}</dd></div>)}
    <div className="price-total"><dt>Total</dt><dd>{formatAmount(quote.totalCents)}</dd></div>
  </dl>;
}

function PrivateTripResources({ reference, isAdmin }: { reference: string; isAdmin: boolean }) {
  const client = useQueryClient();
  const trip = useQuery({ queryKey: ["admin", "private-trip", reference], queryFn: () => getPrivateTrip(reference), retry: false });
  const [vehicleId, setVehicleId] = useState(""); const [driverId, setDriverId] = useState("");
  const options = useQuery({
    queryKey: ["admin", "options", reference], enabled: Boolean(trip.data && !trip.data.assignment && isAdmin),
    queryFn: () => resourceOptions(trip.data!.startsAt, trip.data!.endsAt, trip.data!.passengers)
  });
  const confirm = useMutation({
    mutationFn: () => confirmPrivateTrip(reference, trip.data!.bookingVersion, vehicleId || trip.data!.requestedVehicleId!, driverId),
    onSuccess: async next => { client.setQueryData(["admin", "private-trip", reference], next); await client.invalidateQueries({ queryKey: ["admin"] }); }
  });
  if (trip.isLoading) return <LoadingState label="Loading private trip" />;
  if (trip.isError) return <ErrorState message={trip.error.message} />;
  const t = trip.data!;
  return <div className="admin-convert">
    <h3>Private trip</h3>
    <dl className="admin-detail-grid">
      <div><dt>Start</dt><dd>{dateTime(t.startsAt)}</dd></div><div><dt>Duration</dt><dd>{hours(t.durationMinutes)}</dd></div>
      <div><dt>Requested vehicle</dt><dd>{t.requestedVehicleName ?? categoryLabels[t.requestedCategory]}</dd></div><div><dt>Passengers</dt><dd>{t.passengers}</dd></div>
      <div><dt>Destination / itinerary</dt><dd>{t.destination}</dd></div><div><dt>Special requests</dt><dd>{t.specialRequests ?? "—"}</dd></div>
    </dl>
    <h3>Price quoted to the customer</h3><QuoteBreakdown quote={t.quote} />
    {t.assignment ? <p className="admin-ok">Assigned: {t.assignment.vehicleName} ({t.assignment.registration}) with {t.assignment.driverName} · {assignmentStatusLabels[t.assignment.status]}</p>
      : isAdmin && !CLOSED.includes(t.status) && options.data && <form onSubmit={event => { event.preventDefault(); confirm.mutate(); }}>
        <h3>{t.status === "CONFIRMED" ? "Assign a new vehicle & driver" : "Confirm & assign"}</h3>
        <p className="form-note">Availability, restrictions and driver eligibility are rechecked when you confirm. If someone else books the same vehicle first, you will be asked to choose another.</p>
        <ResourcePicker options={options.data} vehicleId={vehicleId || t.requestedVehicleId || ""} driverId={driverId} onVehicle={setVehicleId} onDriver={setDriverId} />
        {confirm.isError && <ErrorState message={confirm.error.message} />}
        <button className="button button-primary" type="submit" disabled={confirm.isPending || !driverId}>{confirm.isPending ? "Saving…" : t.status === "CONFIRMED" ? "Assign" : "Confirm booking"}</button>
      </form>}
  </div>;
}

function TourResources({ booking, isAdmin }: { booking: OperationsBooking; isAdmin: boolean }) {
  const client = useQueryClient();
  const [start, setStart] = useState(`${booking.travelDate}T08:00`); const [end, setEnd] = useState(`${booking.travelDate}T18:00`);
  const [window, setWindow] = useState<{ startsAt: string; endsAt: string } | null>(null);
  const [vehicleId, setVehicleId] = useState(""); const [driverId, setDriverId] = useState("");
  const options = useQuery({ queryKey: ["admin", "options", booking.reference, window], enabled: Boolean(window),
    queryFn: () => resourceOptions(window!.startsAt, window!.endsAt, booking.guests) });
  const assign = useMutation({
    mutationFn: () => createAssignment({ bookingReference: booking.reference, vehicleId, driverId, startsAt: window!.startsAt, endsAt: window!.endsAt }),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ["admin"] }); }
  });
  if (assign.data) return <p className="admin-ok">Assigned: {assign.data.vehicleName} with {assign.data.driverName}.</p>;
  if (booking.assignmentStatus) return <p className="admin-ok">Assigned: {booking.vehicle} with {booking.driver} · {assignmentStatusLabels[booking.assignmentStatus]}</p>;
  if (!isAdmin || CLOSED.includes(booking.status)) return <p className="form-note">No vehicle or driver assigned.</p>;
  const toIso = (local: string) => new Date(local).toISOString();
  return <div className="admin-convert"><h3>Assign vehicle &amp; driver</h3>
    <div className="admin-inline-form">
      <label>Starts<input type="datetime-local" value={start} onChange={event => { setStart(event.target.value); setWindow(null); }} /></label>
      <label>Ends<input type="datetime-local" value={end} onChange={event => { setEnd(event.target.value); setWindow(null); }} /></label>
      <button type="button" className="button button-secondary" onClick={() => setWindow({ startsAt: toIso(start), endsAt: toIso(end) })}>Check availability</button>
    </div>
    {options.isError && <ErrorState message={options.error.message} />}
    {options.data && <form onSubmit={event => { event.preventDefault(); assign.mutate(); }}>
      <ResourcePicker options={options.data} vehicleId={vehicleId} driverId={driverId} onVehicle={setVehicleId} onDriver={setDriverId} />
      {assign.isError && <ErrorState message={assign.error.message} />}
      <button className="button button-primary" type="submit" disabled={assign.isPending || !vehicleId || !driverId}>Assign</button>
    </form>}
  </div>;
}
