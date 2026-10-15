import { dateTime } from "./labels";
import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { listOperationsBookings } from "../../lib/api";
import { updateBookingStatus } from "./operationsApi";
import { Pagination } from "./Pagination";
import { LoadingState, ErrorState } from "../../components/StateBlocks";
import { formatAmount as formatMoney } from "../../lib/format";
import type { OperationsBooking } from "../../types/api";
import { BookingResources } from "./BookingResources";
import { BookingFinance } from "./BookingFinance";
const statuses = ["ENQUIRY", "QUOTE_PENDING", "QUOTE_SENT", "AWAITING_PAYMENT", "CONFIRMED", "GUIDE_ASSIGNED", "READY", "IN_PROGRESS", "COMPLETED", "CANCELLED", "REFUNDED"];
export function BookingsPanel({ isAdmin = false }: { isAdmin?: boolean }) {
  const [page, setPage] = useState(0); const [status, setStatus] = useState("");
  const [selected, setSelected] = useState<OperationsBooking | null>(null); const [nextStatus, setNextStatus] = useState("");
  const client = useQueryClient();
  const data = useQuery({ queryKey: ["admin", "bookings", page, status], queryFn: () => listOperationsBookings(page, status), retry: false });
  // Payments change the booking's version, so use the freshest copy from the reloaded list.
  const update = useMutation({ mutationFn: () => updateBookingStatus(data.data?.content.find(b => b.reference === selected!.reference) ?? selected!, nextStatus), onSuccess: async () => {
    setSelected(null); await client.invalidateQueries({ queryKey: ["admin"] });
  } });
  return <section><div className="admin-heading"><div><p className="eyebrow">Reservations</p><h1>Customer bookings</h1><p>Review requests from guests and signed-in customers.</p></div><label>Status<select value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}><option value="">All statuses</option>{statuses.map(item => <option key={item}>{item}</option>)}</select></label></div>
    {data.isLoading && <LoadingState label="Loading bookings" />}{data.isError && <ErrorState message={data.error.message} />}
    {data.data && <><div className="admin-table-wrap"><table><thead><tr><th>Reference</th><th>Customer</th><th>Tour / date</th><th>Guests</th><th>Total</th><th>Status</th><th>Details</th></tr></thead><tbody>{data.data.content.map(booking => <tr key={booking.reference}><td>{booking.reference}</td><td>{booking.guest}<small>{booking.email}</small><span className="admin-badge">{booking.registeredCustomer ? "Account booking" : "Guest booking"}</span>{booking.demoData && <span className="admin-badge sample-badge">Sample data</span>}</td><td>{booking.tour}<small>{booking.travelDate}{booking.bookingType === "PRIVATE_TRIP" ? " · hourly" : ""}</small>{booking.assignmentStatus && <small>{booking.vehicle} · {booking.driver}</small>}</td><td>{booking.guests}</td><td>{formatMoney(booking.totalAmountCents)}</td><td><span className="admin-badge">{booking.status.replace(/_/g, " ")}</span></td><td><button className="button button-secondary" onClick={() => { setSelected(booking); setNextStatus(booking.status); update.reset(); }}>Review</button></td></tr>)}</tbody></table>{!data.data.content.length && <p className="admin-empty">No bookings match this filter.</p>}</div><Pagination page={page} totalPages={data.data.totalPages} totalElements={data.data.totalElements} onPage={setPage} /></>}
    {selected && <section className="form-surface admin-detail" aria-label="Booking details"><div className="admin-heading"><h2>{selected.reference}</h2><button className="button button-secondary" onClick={() => setSelected(null)}>Close</button></div><dl className="admin-detail-grid">{[
      ["Customer", selected.guest], ["Email", selected.email], ["Phone", selected.phone], ["Country", selected.country],
      ["Tour", selected.tour], ["Travel date", selected.travelDate], ["Pickup", selected.pickupLocation], ["Notes", selected.notes],
      ["Total", formatMoney(selected.totalAmountCents)], ["Amount paid", formatMoney(selected.amountPaidCents)],
      ["Vehicle", selected.vehicle], ["Driver", selected.driver],
      ["Received", dateTime(selected.createdAt)]
    ].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || "—"}</dd></div>)}</dl>
      <BookingResources key={`${selected.reference}-${selected.version}`} booking={selected} isAdmin={isAdmin} />
      <BookingFinance key={`finance-${selected.reference}`} reference={selected.reference} isAdmin={isAdmin} />
      <div className="admin-actions"><Link className="button button-secondary" to={`/operations/billing/new?kind=INVOICE&booking=${encodeURIComponent(selected.reference)}`}>Create invoice</Link>
        <Link className="button button-quiet" to={`/operations/billing/new?kind=QUOTATION&booking=${encodeURIComponent(selected.reference)}`}>Create quotation</Link></div>
      <form onSubmit={event => { event.preventDefault(); update.mutate(); }}><label>Booking status<select value={nextStatus} onChange={event => setNextStatus(event.target.value)}>{statuses.map(item => <option key={item}>{item}</option>)}</select></label><p className="form-note">Status changes are recorded. Completed and cancelled bookings cannot return to an earlier stage. Cancelling frees the booking's vehicle and driver; private trips are confirmed with “Confirm booking” above.</p>{update.isError && <ErrorState message={update.error.message} />}<button type="submit" className="button button-primary" disabled={update.isPending || nextStatus === selected.status}>{update.isPending ? "Saving…" : "Update status"}</button></form>
    </section>}
  </section>;
}
