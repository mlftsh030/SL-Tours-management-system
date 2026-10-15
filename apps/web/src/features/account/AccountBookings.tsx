import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { Pagination } from "../operations/Pagination";
import { displayDate, formatMoney } from "../billing/money";
import { bookingStatusLabels, listBookings } from "./accountApi";
import { EmptyAction } from "./EmptyAction";

export function AccountBookings() {
  const [page, setPage] = useState(0);
  const bookings = useQuery({ queryKey: ["account", "bookings", page], queryFn: () => listBookings(page), retry: false });
  return <section aria-labelledby="bookings-title">
    <h2 id="bookings-title">Bookings</h2>
    {bookings.isLoading && <LoadingState label="Loading your bookings" />}
    {bookings.isError && <ErrorState message={bookings.error.message} />}
    {bookings.data && !bookings.data.content.length && <EmptyAction title="No bookings yet" text="Bookings you make while signed in appear here with their status and payments."
      actions={[{ to: "/experiences", label: "Explore tours", primary: true }, { to: "/book-private-trip", label: "Plan a private trip" }]} />}
    <ul className="account-list">{bookings.data?.content.map(b => <li key={b.reference} className="form-surface">
      <div className="account-item-head"><h3>{b.tourTitle}</h3><span className="admin-badge">{bookingStatusLabels[b.status] ?? b.status}</span></div>
      <dl className="account-facts">
        <div><dt>Reference</dt><dd>{b.reference}</dd></div>
        <div><dt>Travel date</dt><dd>{displayDate(b.travelDate)}</dd></div>
        <div><dt>Guests</dt><dd>{b.guests}</dd></div>
        <div><dt>Total</dt><dd>{formatMoney(b.totalAmountCents)}</dd></div>
        <div><dt>Paid</dt><dd>{formatMoney(b.amountPaidCents)}</dd></div>
      </dl>
    </li>)}</ul>
    {bookings.data && bookings.data.totalPages > 1 && <Pagination page={page} totalPages={bookings.data.totalPages} totalElements={bookings.data.totalElements} onPage={setPage} />}
  </section>;
}
