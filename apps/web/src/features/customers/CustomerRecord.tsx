import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { displayDate, documentStatusLabels, formatMoney, kindLabels, paymentStateLabels, paymentStatusLabels } from "../billing/money";
import { dateTime, humanize } from "../operations/labels";
import { getCustomer, mergeCustomers, searchCustomers, updateCustomer, type CustomerDetail } from "./customersApi";

export function CustomerRecord({ isAdmin }: { isAdmin: boolean }) {
  const { id = "" } = useParams();
  const client = useQueryClient();
  const detail = useQuery({ queryKey: ["admin", "customer", id], queryFn: () => getCustomer(id), retry: false });
  const [editing, setEditing] = useState(false);
  if (detail.isLoading) return <LoadingState label="Loading customer" />;
  if (detail.isError) return <ErrorState message={detail.error.message} />;
  const c = detail.data!;
  const s = c.summary;
  const refresh = async (next: CustomerDetail) => { client.setQueryData(["admin", "customer", next.summary.id], next); setEditing(false); await client.invalidateQueries({ queryKey: ["admin", "customers"] }); };
  return <section className="customer-record">
    <div className="admin-heading"><div><p className="eyebrow"><Link to="/operations/customers">Customers</Link></p><h1>{s.name}</h1>
      <p>{[s.email, s.phone, c.country].filter(Boolean).join(" · ") || "No contact details"}</p></div>
      <div className="admin-actions">{!c.mergedIntoId && <button className="button button-secondary" onClick={() => setEditing(e => !e)}>{editing ? "Close" : "Edit details"}</button>}</div></div>
    {c.mergedIntoId && <p className="admin-warning">This record was merged. Its history now belongs to <Link to={`/operations/customers/${c.mergedIntoId}`}>the surviving record</Link>.</p>}
    <dl className="billing-figures">
      <div><dt>Login</dt><dd>{c.accountEmail ? `${c.accountEmail}${c.accountVerified ? " (verified)" : ""}` : "None (guest)"}</dd></div>
      <div><dt>Enquiries</dt><dd>{s.enquiries} ({s.openEnquiries} open)</dd></div>
      <div><dt>Bookings</dt><dd>{s.bookings} ({s.upcomingBookings} upcoming)</dd></div>
      <div className="billing-balance"><dt>Outstanding</dt><dd>{formatMoney(s.outstandingCents)}</dd></div>
    </dl>
    {editing && <EditForm c={c} onSaved={refresh} />}
    {c.notes && !editing && <section className="form-surface"><h2>Staff notes</h2><p className="admin-message">{c.notes}</p></section>}

    <section className="form-surface"><h2>Enquiries</h2>
      {c.enquiries.length ? <div className="admin-table-wrap"><table><thead><tr><th>Reference</th><th>Request</th><th>Status</th><th>Travel date</th><th>Received</th></tr></thead><tbody>
        {c.enquiries.map(e => <tr key={e.reference}><td><Link to={`/operations/enquiries?q=${encodeURIComponent(e.reference)}`}>{e.reference}</Link></td>
          <td>{e.tourTitle ?? humanize(e.type)}<small>{humanize(e.source)}</small></td><td>{humanize(e.status)}</td><td>{displayDate(e.requestedDate)}</td><td>{dateTime(e.createdAt)}</td></tr>)}
      </tbody></table></div> : <p className="admin-empty">No enquiries.</p>}</section>

    <section className="form-surface"><h2>Bookings</h2>
      {c.bookings.length ? <div className="admin-table-wrap"><table><thead><tr><th>Reference</th><th>Trip</th><th>Travel date</th><th>Status</th><th>Total</th><th>Paid</th><th>Vehicle/driver</th></tr></thead><tbody>
        {c.bookings.map(b => <tr key={b.reference}><td>{b.reference}{b.sourceQuotation && <small>from quotation {b.sourceQuotation}</small>}</td><td>{b.title}<small>{b.guests} guests</small></td>
          <td>{displayDate(b.travelDate)}</td><td>{humanize(b.status)}</td><td>{formatMoney(b.totalCents)}</td><td>{formatMoney(b.paidCents)}</td>
          <td>{b.assigned ? "Assigned" : <span className="admin-badge status-draft">Not assigned</span>}</td></tr>)}
      </tbody></table></div> : <p className="admin-empty">No bookings.</p>}</section>

    <section className="form-surface"><h2>Quotations and invoices</h2>
      {c.documents.length ? <div className="admin-table-wrap"><table><thead><tr><th>Document</th><th>Status</th><th>Issued</th><th>Total</th><th>Balance</th></tr></thead><tbody>
        {c.documents.map(d => <tr key={d.id}><td><Link to={`/operations/billing/${d.id}`}>{kindLabels[d.kind]} {d.displayNumber}</Link>{d.bookingReference && <small>booking {d.bookingReference}</small>}</td>
          <td>{documentStatusLabels[d.status]}{d.kind === "INVOICE" && d.status === "ISSUED" && <small>{paymentStatusLabels[d.paymentStatus]}</small>}</td>
          <td>{displayDate(d.issueDate)}</td><td>{formatMoney(d.totalCents, d.currency)}</td>
          <td>{d.kind === "INVOICE" && d.status === "ISSUED" ? formatMoney(d.totalCents - d.paidCents, d.currency) : "—"}</td></tr>)}
      </tbody></table></div> : <p className="admin-empty">No quotations or invoices.</p>}</section>

    <section className="form-surface"><h2>Payments</h2>
      {c.payments.length ? <div className="admin-table-wrap"><table><thead><tr><th>Paid on</th><th>Invoice</th><th>Amount</th><th>Method</th><th>Status</th></tr></thead><tbody>
        {c.payments.map(p => <tr key={p.id}><td>{displayDate(p.paidOn)}</td><td><Link to={`/operations/billing/${p.documentId}`}>{p.invoiceNumber ?? "draft"}</Link></td>
          <td>{formatMoney(p.amountCents)}</td><td>{humanize(p.method)}</td><td>{paymentStateLabels[p.status]}{p.receiptNumber && <small>receipt {p.receiptNumber}</small>}</td></tr>)}
      </tbody></table></div> : <p className="admin-empty">No payments.</p>}</section>

    {c.merges.length > 0 && <section className="form-surface"><h2>Merge history</h2><ul className="admin-notes">{c.merges.map(m => <li key={m.otherId + m.mergedAt}>
      <p>{m.direction === "MERGED_IN" ? "Merged in" : "Merged into"} <Link to={`/operations/customers/${m.otherId}`}>{m.otherName}</Link> ({m.otherEmail ?? "no email"}): {m.reason}</p>
      <small>{m.mergedBy} · {dateTime(m.mergedAt)}</small></li>)}</ul></section>}

    {isAdmin && !c.mergedIntoId && <MergePanel c={c} />}
  </section>;
}

function EditForm({ c, onSaved }: { c: CustomerDetail; onSaved: (next: CustomerDetail) => Promise<void> }) {
  const [form, setForm] = useState({ name: c.summary.name, phone: c.summary.phone ?? "", country: c.country ?? "", notes: c.notes ?? "" });
  const save = useMutation({ mutationFn: () => updateCustomer(c.summary.id, { version: c.version, name: form.name, phone: form.phone || null, country: form.country || null, notes: form.notes || null }), onSuccess: onSaved });
  return <form className="form-surface" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate(); }} aria-label="Edit customer">
    <h2>Edit details</h2>
    <p className="form-note">Corrections apply to this record and to new documents. Issued quotations and invoices keep the details they were issued with. The email address identifies the customer and is not edited here.</p>
    <div className="form-grid admin-form-grid">
      <label>Name<input required maxLength={160} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
      <label>Phone<input maxLength={80} value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} /></label>
      <label>Country<input maxLength={80} value={form.country} onChange={e => setForm({ ...form, country: e.target.value })} /></label>
      <label className="full-field">Staff notes <small>Never shown to the customer</small><textarea rows={3} maxLength={4000} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></label>
    </div>
    {save.isError && <ErrorState message={save.error.message} />}
    <button className="button button-primary" type="submit" disabled={save.isPending || !form.name.trim()}>{save.isPending ? "Saving…" : "Save"}</button>
  </form>;
}

/** Merge another record into this one. Administrators only; refused for two login holders; always needs a reason. */
function MergePanel({ c }: { c: CustomerDetail }) {
  const client = useQueryClient(); const navigate = useNavigate();
  const [q, setQ] = useState(""); const [query, setQuery] = useState("");
  const [chosen, setChosen] = useState<CustomerDetail | null>(null);
  const [reason, setReason] = useState("");
  const results = useQuery({ queryKey: ["admin", "customers", "merge", query], queryFn: () => searchCustomers(query, 0), enabled: query.length > 1, retry: false });
  const pick = useMutation({ mutationFn: (id: string) => getCustomer(id), onSuccess: setChosen });
  const merge = useMutation({
    mutationFn: () => mergeCustomers({ survivorId: c.summary.id, mergedId: chosen!.summary.id, survivorVersion: c.version, mergedVersion: chosen!.version, reason }),
    onSuccess: async next => { client.setQueryData(["admin", "customer", next.summary.id], next); setChosen(null); setReason(""); await client.invalidateQueries({ queryKey: ["admin", "customers"] }); navigate(`/operations/customers/${next.summary.id}`); }
  });
  return <section className="form-surface customer-merge"><h2>Merge a duplicate into this record</h2>
    <p className="form-note">Only merge when you have confirmed it is the same person. The other record's enquiries, bookings, quotations and invoices move here; both records and the moved items are kept in the merge history. Who can see what in their customer account does not change. Two customers who each have their own login cannot be merged.</p>
    <form className="admin-inline-form" role="search" onSubmit={e => { e.preventDefault(); setQuery(q.trim()); }}>
      <label>Find the duplicate<input value={q} onChange={e => setQ(e.target.value)} maxLength={120} placeholder="Name, email or phone" /></label>
      <button className="button button-secondary" type="submit">Find</button></form>
    {results.data && <ul className="customer-merge-results">{results.data.content.filter(x => x.id !== c.summary.id).map(x => <li key={x.id}>
      <button type="button" className="button button-quiet" onClick={() => pick.mutate(x.id)}>{x.name} · {x.email ?? "no email"} · {x.bookings} bookings{x.hasAccount ? " · has login" : ""}</button></li>)}</ul>}
    {chosen && <form className="admin-callout billing-confirm" onSubmit={e => { e.preventDefault(); merge.mutate(); }} aria-labelledby="merge-title">
      <h3 id="merge-title">Merge {chosen.summary.name} ({chosen.summary.email ?? "no email"}) into {c.summary.name}?</h3>
      <p>Moves {chosen.summary.enquiries} enquiries, {chosen.summary.bookings} bookings and {chosen.summary.invoices} invoices. {chosen.summary.email} will resolve to this record from now on.</p>
      <label>Reason <small>Kept in the history, for example how you confirmed it is the same person</small><input required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} /></label>
      <div className="admin-actions"><button className="button button-primary billing-danger" type="submit" disabled={!reason.trim() || merge.isPending}>{merge.isPending ? "Merging…" : "Merge"}</button>
        <button className="button button-secondary" type="button" onClick={() => setChosen(null)}>Cancel</button></div></form>}
    {[pick, merge].map((m, i) => m.isError ? <ErrorState key={i} message={(m.error as Error).message} /> : null)}
  </section>;
}
