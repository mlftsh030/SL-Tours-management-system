import { useState } from "react";
import { localDate } from "../../lib/dates";
import { exportUrl, type ExportDataset } from "./reportsApi";

const datasets: Array<{ id: ExportDataset; label: string; by: string; contents: string }> = [
  { id: "customers", label: "Customers", by: "date the record was created", contents: "Name, email, phone, country, whether they have a login." },
  { id: "enquiries", label: "Enquiries", by: "date received", contents: "Reference, status, type, source, customer contact, tour, travel date, passengers, booking. Messages and staff notes are not included." },
  { id: "bookings", label: "Bookings", by: "travel date", contents: "Reference, type, trip, status, travel date, guests, customer contact, total, paid, source quotation." },
  { id: "invoices", label: "Quotations and invoices", by: "issue date", contents: "Number, status, dates, customer, booking, amounts, balance, payment status, void reason. Drafts are not included." },
  { id: "payments", label: "Invoice payments", by: "date paid", contents: "Amount, method, reference, status, receipt number, who recorded, confirmed, rejected or reversed it." }
];

/** Administrators only. Each download is recorded in the audit log with the dataset, dates and row count. */
export function ExportsPanel() {
  const today = localDate();
  const [from, setFrom] = useState(`${today.slice(0, 4)}-01-01`);
  const [to, setTo] = useState(today);
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">Data</p><h1>Data exports</h1>
      <p>CSV files for accounting, reporting or a customer's request for their data. Exports contain personal information: store them securely and delete them when no longer needed.</p></div></div>
    <div className="form-surface">
      <div className="form-grid admin-form-grid">
        <label>From<input type="date" value={from} max={to} onChange={e => setFrom(e.target.value)} /></label>
        <label>To<input type="date" value={to} min={from} onChange={e => setTo(e.target.value)} /></label>
      </div>
      <p className="form-note">Every export is recorded with your name, the dataset, the dates and the number of rows. Passwords, sign-in tokens, staff notes and uploaded files are never exported.</p>
    </div>
    <ul className="export-list">{datasets.map(d => <li key={d.id} className="form-surface">
      <div><h2>{d.label}</h2><p>{d.contents}</p><small>Filtered by {d.by}.</small></div>
      <a className="button button-secondary" href={exportUrl(d.id, from, to)} download>Download CSV</a></li>)}</ul>
  </section>;
}
