import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { displayDate, formatMoney } from "../billing/money";
import { dateTime, humanize } from "../operations/labels";
import { getWorkQueues } from "./reportsApi";

const waited = (hours: number) => hours >= 48 ? `${Math.floor(hours / 24)} days` : `${hours} hours`;

/** What needs someone to act, oldest first. Each list links to where the work is done. */
export function FollowUpPanel() {
  const data = useQuery({ queryKey: ["admin", "work-queues"], queryFn: getWorkQueues, retry: false, refetchInterval: 60_000 });
  if (data.isLoading) return <LoadingState label="Loading work queues" />;
  if (data.isError) return <ErrorState message={data.error.message} />;
  const q = data.data!;
  return <section className="work-queues">
    <div className="admin-heading"><div><p className="eyebrow">Today</p><h1>Follow-up</h1>
      <p>Enquiries waiting for a reply, trips without a vehicle or driver, unpaid invoices and payment evidence to check. Sample data is left out.</p></div></div>
    <nav className="work-queue-summary" aria-label="Queues">
      <a href="#follow-ups"><strong>{q.followUps.length}</strong>Enquiries to follow up</a>
      <a href="#unassigned"><strong>{q.unassignedBookings.length}</strong>Trips not assigned (21 days)</a>
      <a href="#open-invoices"><strong>{q.openInvoices.length}</strong>Invoices with a balance</a>
      <a href="#evidence"><strong>{q.paymentsToReview.length + q.customerProofs.length}</strong>Payment evidence to check</a>
      <Link to="/operations/customers/duplicates"><strong>{q.duplicateGroups}</strong>Possible duplicate customers</Link>
    </nav>

    <section id="follow-ups" className="form-surface"><h2>Enquiries awaiting follow-up</h2>
      <p className="form-note">New for more than 4 hours, in progress for more than 2 days, or waiting for the customer for more than 5 days.</p>
      {q.followUps.length ? <div className="admin-table-wrap"><table><thead><tr><th>Enquiry</th><th>Customer</th><th>Status</th><th>Waiting</th><th>Received</th></tr></thead><tbody>
        {q.followUps.map(e => <tr key={e.reference}><td><Link to={`/operations/enquiries?q=${encodeURIComponent(e.reference)}`}>{e.reference}</Link><small>{humanize(e.type)}</small></td>
          <td>{e.customerId ? <Link to={`/operations/customers/${e.customerId}`}>{e.customerName}</Link> : e.customerName}</td><td>{humanize(e.status)}</td>
          <td>{waited(e.waitingHours)}</td><td>{dateTime(e.createdAt)}</td></tr>)}</tbody></table></div> : <p className="admin-empty">Nothing waiting.</p>}</section>

    <section id="unassigned" className="form-surface"><h2>Upcoming trips without a vehicle or driver</h2>
      {q.unassignedBookings.length ? <div className="admin-table-wrap"><table><thead><tr><th>Travel date</th><th>Booking</th><th>Customer</th><th>Guests</th><th>Status</th></tr></thead><tbody>
        {q.unassignedBookings.map(b => <tr key={b.reference}><td>{displayDate(b.travelDate)}</td><td>{b.reference}<small>{b.title}</small></td>
          <td>{b.customerId ? <Link to={`/operations/customers/${b.customerId}`}>{b.customerName}</Link> : b.customerName}</td><td>{b.guests}</td><td>{humanize(b.status)}</td></tr>)}
      </tbody></table></div> : <p className="admin-empty">Every trip in the next 21 days is assigned.</p>}
      <Link className="button button-secondary" to="/operations/trips">Open trips &amp; inspections</Link></section>

    <section id="open-invoices" className="form-surface"><h2>Invoices with a balance</h2>
      {q.openInvoices.length ? <div className="admin-table-wrap"><table><thead><tr><th>Invoice</th><th>Customer</th><th>Due</th><th>Balance</th><th>Waiting for confirmation</th></tr></thead><tbody>
        {q.openInvoices.map(i => <tr key={i.id}><td><Link to={`/operations/billing/${i.id}`}>{i.number}</Link>{i.overdue && <span className="admin-badge billing-overdue">Overdue</span>}</td>
          <td>{i.customerId ? <Link to={`/operations/customers/${i.customerId}`}>{i.customerName}</Link> : i.customerName}</td><td>{displayDate(i.dueDate)}</td>
          <td><strong>{formatMoney(i.totalCents - i.paidCents, i.currency)}</strong></td><td>{i.pendingCents ? formatMoney(i.pendingCents, i.currency) : "—"}</td></tr>)}
      </tbody></table></div> : <p className="admin-empty">No unpaid invoices.</p>}</section>

    <section id="evidence" className="form-surface"><h2>Payment evidence to check</h2>
      <h3>Recorded payments waiting for an administrator</h3>
      {q.paymentsToReview.length ? <div className="admin-table-wrap"><table><thead><tr><th>Invoice</th><th>Customer</th><th>Amount</th><th>Paid on</th><th>Proof</th></tr></thead><tbody>
        {q.paymentsToReview.map(p => <tr key={p.paymentId}><td><Link to={`/operations/billing/${p.documentId}`}>{p.invoiceNumber}</Link></td><td>{p.customerName}</td>
          <td>{formatMoney(p.amountCents)}<small>{humanize(p.method)}{p.reference ? ` · ${p.reference}` : ""}</small></td><td>{displayDate(p.paidOn)}</td>
          <td>{p.proofFiles ? `${p.proofFiles} file${p.proofFiles > 1 ? "s" : ""}` : <span className="admin-badge status-draft">None</span>}</td></tr>)}
      </tbody></table></div> : <p className="admin-empty">No payments waiting.</p>}
      <h3>Proof uploaded by customers, not yet recorded as a payment</h3>
      {q.customerProofs.length ? <div className="admin-table-wrap"><table><thead><tr><th>Invoice</th><th>Customer</th><th>Uploaded</th></tr></thead><tbody>
        {q.customerProofs.map(p => <tr key={p.attachmentId}><td><Link to={`/operations/billing/${p.documentId}`}>{p.invoiceNumber}</Link></td><td>{p.customerName}<small>{p.uploadedBy}</small></td>
          <td>{dateTime(p.uploadedAt)}</td></tr>)}</tbody></table></div> : <p className="admin-empty">No customer uploads waiting.</p>}</section>
  </section>;
}
