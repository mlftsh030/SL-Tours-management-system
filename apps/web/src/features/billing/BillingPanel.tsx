import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, Route, Routes, useParams } from "react-router-dom";
import { FilePlus2 } from "lucide-react";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { Pagination } from "../operations/Pagination";
import { listDocuments, type DocumentFilters } from "./billingApi";
import { DocumentEditor } from "./DocumentEditor";
import { DocumentPage } from "./DocumentPage";
import { displayDate, documentStatusLabels, formatMoney, kindLabels, paymentStatusLabels } from "./money";

/** Invoices and quotations: list, editor and document pages under /operations/billing. */
export function BillingPanel({ isAdmin }: { isAdmin: boolean }) {
  return <Routes>
    <Route index element={<DocumentList />} />
    <Route path="new" element={<DocumentEditor />} />
    <Route path=":id/edit" element={<EditRoute />} />
    <Route path=":id" element={<DocumentRoute isAdmin={isAdmin} />} />
  </Routes>;
}
function EditRoute() { const { id } = useParams(); return <DocumentEditor id={id} />; }
function DocumentRoute({ isAdmin }: { isAdmin: boolean }) { const { id } = useParams(); return <DocumentPage key={id} id={id!} isAdmin={isAdmin} />; }

const emptyFilters: DocumentFilters = { kind: "", status: "", paymentStatus: "", q: "", from: "", to: "", overdue: false };

function DocumentList() {
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<DocumentFilters>(emptyFilters);
  const [search, setSearch] = useState("");
  const data = useQuery({ queryKey: ["billing", "documents", page, filters], queryFn: () => listDocuments(page, filters), retry: false });
  const apply = (next: Partial<DocumentFilters>) => { setFilters(f => ({ ...f, ...next })); setPage(0); };
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">Billing</p><h1>Invoices and quotations</h1>
      <p>Create quotations and invoices in the SL Tours layout, share them, and record payments. Customer details and prices are frozen when a document is issued.</p></div>
      <div className="admin-actions">
        <Link className="button button-primary" to="/operations/billing/new?kind=INVOICE"><FilePlus2 aria-hidden="true" />New invoice</Link>
        <Link className="button button-secondary" to="/operations/billing/new?kind=QUOTATION">New quotation</Link></div></div>
    <form className="admin-filters billing-filters" role="search" onSubmit={e => { e.preventDefault(); apply({ q: search }); }}>
      <label>Search<input value={search} maxLength={120} onChange={e => setSearch(e.target.value)} placeholder="Customer, email, number or booking" /></label>
      <label>Type<select value={filters.kind} onChange={e => apply({ kind: e.target.value as DocumentFilters["kind"] })}><option value="">Invoices and quotations</option><option value="INVOICE">Invoices</option><option value="QUOTATION">Quotations</option></select></label>
      <label>Status<select value={filters.status} onChange={e => apply({ status: e.target.value as DocumentFilters["status"] })}><option value="">All except void</option>
        {Object.entries(documentStatusLabels).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
      <label>Payment<select value={filters.paymentStatus} onChange={e => apply({ paymentStatus: e.target.value as DocumentFilters["paymentStatus"] })}><option value="">Any</option>
        <option value="UNPAID">Unpaid</option><option value="PARTIALLY_PAID">Part paid</option><option value="PAID">Paid</option></select></label>
      <label>From<input type="date" value={filters.from} onChange={e => apply({ from: e.target.value })} /></label>
      <label>To<input type="date" value={filters.to} min={filters.from || undefined} onChange={e => apply({ to: e.target.value })} /></label>
      <label className="admin-check"><input type="checkbox" checked={filters.overdue} onChange={e => apply({ overdue: e.target.checked })} />Overdue only</label>
      <div className="admin-actions"><button className="button button-secondary" type="submit">Search</button>
        <button className="button button-quiet" type="button" onClick={() => { setSearch(""); setFilters(emptyFilters); setPage(0); }}>Clear</button></div>
    </form>
    {data.isLoading && <LoadingState label="Loading documents" />}{data.isError && <ErrorState message={data.error.message} />}
    {data.data && <><div className="admin-table-wrap"><table className="billing-table"><thead><tr><th>Number</th><th>Customer</th><th>Date</th><th>Total</th><th>Balance</th><th>Status</th><th></th></tr></thead><tbody>
      {data.data.content.map(d => <tr key={d.id}>
        <td><strong>{d.displayNumber}</strong><small>{kindLabels[d.kind]}</small></td>
        <td>{d.customerName}{d.bookingReference && <small>{d.bookingReference}</small>}</td>
        <td>{displayDate(d.issueDate)}{d.dueDate && <small>{d.kind === "INVOICE" ? "Due" : "Valid until"} {displayDate(d.dueDate)}</small>}</td>
        <td>{formatMoney(d.totalCents, d.currency)}</td>
        <td>{d.kind === "INVOICE" && d.status !== "DRAFT" && d.status !== "VOID" ? formatMoney(d.balanceCents, d.currency) : "—"}</td>
        <td><span className={`admin-badge billing-status-${d.status.toLowerCase()}`}>{documentStatusLabels[d.status]}</span>
          {d.kind === "INVOICE" && d.status === "ISSUED" && <span className={`admin-badge billing-pay-${d.paymentStatus.toLowerCase()}`}>{paymentStatusLabels[d.paymentStatus]}</span>}
          {d.overdue && <span className="admin-badge billing-overdue">Overdue</span>}</td>
        <td><Link className="button button-secondary" to={`/operations/billing/${d.id}`} aria-label={`Open ${kindLabels[d.kind].toLowerCase()} ${d.displayNumber} for ${d.customerName}`}>Open</Link></td>
      </tr>)}
    </tbody></table>{!data.data.content.length && <p className="admin-empty">No documents match these filters.</p>}</div>
    <Pagination page={page} totalPages={data.data.totalPages} totalElements={data.data.totalElements} onPage={setPage} /></>}
  </section>;
}
