import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { listTours } from "../../lib/api";
import { localDate } from "../../lib/dates";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { Pagination } from "./Pagination";
import { addEnquiryNote, convertEnquiry, getEnquiry, listEnquiries, updateEnquiryStatus, type EnquiryFilters } from "./operationsApi";
import { dateTime, enquirySourceLabels, enquiryStatusLabels, humanize } from "./labels";
import type { EnquiryDetail } from "../../types/api";
import { convertEnquiryToTrip, listVehicles } from "./fleetApi";
import { categoryLabels, randToCents } from "./labels";

const statuses = Object.keys(enquiryStatusLabels);
const sources = Object.keys(enquirySourceLabels);

export function EnquiriesPanel() {
  const [page, setPage] = useState(0);
  // ?q= opens the inbox already searched (links from invoices, quotations and customer records).
  const [initialQuery] = useState(() => new URLSearchParams(window.location.search).get("q") ?? "");
  const [filters, setFilters] = useState<EnquiryFilters>({ q: initialQuery, status: "", source: "" });
  const [search, setSearch] = useState(initialQuery);
  const [selected, setSelected] = useState<string | null>(null);
  const data = useQuery({ queryKey: ["admin", "enquiries", page, filters], queryFn: () => listEnquiries(page, filters), retry: false });
  const applyFilters = (next: Partial<EnquiryFilters>) => { setFilters(current => ({ ...current, ...next })); setPage(0); };
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">Inbox</p><h1>Website enquiries</h1><p>Contact form, trip planner and private-trip quote requests. Internal notes are never shown to customers.</p></div></div>
    <form className="admin-filters" role="search" onSubmit={event => { event.preventDefault(); applyFilters({ q: search }); }}>
      <label>Search<input value={search} onChange={event => setSearch(event.target.value)} maxLength={120} placeholder="Name, email, phone, reference or message" /></label>
      <label>Status<select value={filters.status} onChange={event => applyFilters({ status: event.target.value })}><option value="">All statuses</option>{statuses.map(s => <option key={s} value={s}>{enquiryStatusLabels[s]}</option>)}</select></label>
      <label>Source<select value={filters.source} onChange={event => applyFilters({ source: event.target.value })}><option value="">All sources</option>{sources.map(s => <option key={s} value={s}>{enquirySourceLabels[s]}</option>)}</select></label>
      <button className="button button-secondary" type="submit">Search</button>
    </form>
    {data.isLoading && <LoadingState label="Loading enquiries" />}{data.isError && <ErrorState message={data.error.message} />}
    {data.data && <><div className="admin-table-wrap"><table><thead><tr><th>Received</th><th>Customer</th><th>Request</th><th>Message</th><th>Status</th><th>Details</th></tr></thead><tbody>
      {data.data.content.map(enquiry => <tr key={enquiry.reference}>
        <td>{dateTime(enquiry.createdAt)}<small>{enquiry.reference}</small></td>
        <td>{enquiry.customerName}{enquiry.demoData && <span className="admin-badge sample-badge">Sample data</span>}<small>{enquiry.customerEmail}</small>{enquiry.customerPhone && <small>{enquiry.customerPhone}</small>}</td>
        <td>{enquiry.tourTitle ?? humanize(enquiry.type)}<small>{[enquiry.requestedDate, enquiry.passengers && `${enquiry.passengers} pax`].filter(Boolean).join(" · ") || enquirySourceLabels[enquiry.source]}</small></td>
        <td className="admin-message-cell">{enquiry.messagePreview}</td>
        <td><span className={`admin-badge enquiry-${enquiry.status.toLowerCase()}`}>{enquiryStatusLabels[enquiry.status]}</span></td>
        <td><button className="button button-secondary" onClick={() => setSelected(enquiry.reference)}>Review</button></td>
      </tr>)}
    </tbody></table>{!data.data.content.length && <p className="admin-empty">No enquiries match these filters.</p>}</div>
    <Pagination page={page} totalPages={data.data.totalPages} totalElements={data.data.totalElements} onPage={setPage} /></>}
    {selected && <EnquiryReview key={selected} reference={selected} onClose={() => setSelected(null)} />}
  </section>;
}

function EnquiryReview({ reference, onClose }: { reference: string; onClose: () => void }) {
  const client = useQueryClient();
  const detail = useQuery({ queryKey: ["admin", "enquiry", reference], queryFn: () => getEnquiry(reference), retry: false });
  const refresh = async (next: EnquiryDetail) => {
    client.setQueryData(["admin", "enquiry", reference], next);
    await client.invalidateQueries({ queryKey: ["admin", "enquiries"] });
    await client.invalidateQueries({ queryKey: ["admin", "dashboard"] });
  };
  const [status, setStatus] = useState("");
  const [note, setNote] = useState("");
  const updateStatus = useMutation({ mutationFn: (enquiry: EnquiryDetail) => updateEnquiryStatus(enquiry, status), onSuccess: refresh });
  const saveNote = useMutation({ mutationFn: () => addEnquiryNote(reference, note), onSuccess: async next => { setNote(""); await refresh(next); } });
  if (detail.isLoading) return <LoadingState label="Loading enquiry" />;
  if (detail.isError) return <ErrorState message={detail.error.message} />;
  const enquiry = detail.data!;
  const rows: Array<[string, string | number | null]> = [
    ["Customer", enquiry.customerName], ["Email", enquiry.customerEmail], ["Phone", enquiry.customerPhone],
    ["Source", enquirySourceLabels[enquiry.source]], ["Request", enquiry.tourTitle ?? humanize(enquiry.type)],
    ["Requested date", enquiry.requestedDate], ["Start time", enquiry.requestedTime?.slice(0, 5) ?? null],
    ["Duration", enquiry.durationMinutes ? `${enquiry.durationMinutes / 60} hours` : null], ["Passengers", enquiry.passengers],
    ["Pickup location", enquiry.pickupLocation], ["Destination / itinerary", enquiry.destination], ["Received", dateTime(enquiry.createdAt)]
  ];
  return <section className="form-surface admin-detail" aria-label="Enquiry details">
    <div className="admin-heading"><div><p className="eyebrow">{enquiry.reference}</p><h2>{enquiry.customerName}</h2></div>
      <div className="admin-actions"><span className={`admin-badge enquiry-${enquiry.status.toLowerCase()}`}>{enquiryStatusLabels[enquiry.status]}</span><button className="button button-secondary" onClick={onClose}>Close</button></div></div>
    <dl className="admin-detail-grid">{rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value ?? "—"}</dd></div>)}</dl>
    <h3>Message</h3><p className="admin-message">{enquiry.message}</p>
    <div className="admin-actions">
      <a className="button button-secondary" href={`mailto:${enquiry.customerEmail}?subject=${encodeURIComponent(`SL Tours enquiry ${enquiry.reference}`)}`}>Email customer</a>
      {enquiry.customerPhone && <a className="button button-secondary" href={`tel:${enquiry.customerPhone.replace(/[^+\d]/g, "")}`}>Call customer</a>}
      {enquiry.convertedBookingReference && <Link className="button button-secondary" to="/operations/bookings">Booking {enquiry.convertedBookingReference}</Link>}
      {enquiry.status !== "CONVERTED" && enquiry.status !== "CLOSED" &&
        <Link className="button button-primary" to={`/operations/billing/new?kind=QUOTATION&enquiry=${encodeURIComponent(enquiry.reference)}`}>Prepare quotation</Link>}
    </div>
    {enquiry.allowedStatuses.length > 0 && <form className="admin-inline-form" onSubmit={event => { event.preventDefault(); updateStatus.mutate(enquiry); }}>
      <label>Handling status<select value={status} onChange={event => setStatus(event.target.value)} required><option value="">Choose a status</option>{enquiry.allowedStatuses.map(s => <option key={s} value={s}>{enquiryStatusLabels[s]}</option>)}</select></label>
      <button className="button button-primary" type="submit" disabled={!status || updateStatus.isPending}>{updateStatus.isPending ? "Saving…" : "Update status"}</button>
      {updateStatus.isError && <ErrorState message={updateStatus.error.message} />}
    </form>}
    <h3>Internal notes</h3>
    {enquiry.notes.length ? <ol className="admin-notes">{enquiry.notes.map((n, index) => <li key={index}><p>{n.body}</p><small>{n.author} · {dateTime(n.createdAt)}</small></li>)}</ol> : <p className="form-note">No notes yet.</p>}
    <form onSubmit={(event: FormEvent) => { event.preventDefault(); if (note.trim()) saveNote.mutate(); }}>
      <label>Add a note<textarea rows={3} maxLength={2000} value={note} onChange={event => setNote(event.target.value)} placeholder="Visible to SL Tours staff only" /></label>
      {saveNote.isError && <ErrorState message={saveNote.error.message} />}
      <div className="admin-actions"><button className="button button-secondary" type="submit" disabled={!note.trim() || saveNote.isPending}>{saveNote.isPending ? "Saving…" : "Save note"}</button></div>
    </form>
    {enquiry.status !== "CONVERTED" && enquiry.status !== "CLOSED" && <ConvertForm enquiry={enquiry} onConverted={refresh} />}
  </section>;
}

function ConvertForm({ enquiry, onConverted }: { enquiry: EnquiryDetail; onConverted: (next: EnquiryDetail) => Promise<void> }) {
  const tours = useQuery({ queryKey: ["tours"], queryFn: () => listTours() });
  const [result, setResult] = useState<string | null>(null);
  const convert = useMutation({
    mutationFn: (payload: Parameters<typeof convertEnquiry>[1]) => convertEnquiry(enquiry, payload),
    onSuccess: async response => { setResult(response.bookingReference); await onConverted(await getEnquiry(enquiry.reference)); }
  });
  if (enquiry.type === "PRIVATE_TRIP") return <PrivateTripConvertForm enquiry={enquiry} onConverted={onConverted} />;
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    convert.mutate({ tourSlug: String(form.get("tourSlug")), travelDate: String(form.get("travelDate")), guests: Number(form.get("guests")),
      pickupLocation: String(form.get("pickupLocation") ?? "") || undefined });
  }
  return <form className="admin-convert" onSubmit={submit}>
    <h3>Convert to booking</h3>
    <p className="form-note">The customer's name, email and phone are copied from this enquiry. The tour price comes from the current catalogue.</p>
    <div className="form-grid admin-form-grid">
      <label>Tour<select name="tourSlug" required defaultValue={enquiry.tourSlug ?? ""}><option value="" disabled>Choose a tour</option>{tours.data?.map(t => <option key={t.slug} value={t.slug}>{t.title}</option>)}</select></label>
      <label>Travel date<input name="travelDate" type="date" required defaultValue={enquiry.requestedDate ?? ""} min={localDate()} /></label>
      <label>Guests<input name="guests" type="number" required min={1} max={60} defaultValue={enquiry.passengers ?? 2} /></label>
      <label>Pickup location<input name="pickupLocation" maxLength={240} defaultValue={enquiry.pickupLocation ?? ""} /></label>
    </div>
    {convert.isError && <ErrorState message={convert.error.message} />}
    {result && <p role="status">Booking {result} created.</p>}
    <button className="button button-primary" type="submit" disabled={convert.isPending}>{convert.isPending ? "Converting…" : "Create booking"}</button>
  </form>;
}

function PrivateTripConvertForm({ enquiry, onConverted }: { enquiry: EnquiryDetail; onConverted: (next: EnquiryDetail) => Promise<void> }) {
  const vehicles = useQuery({ queryKey: ["admin", "vehicles"], queryFn: listVehicles });
  const [error, setError] = useState("");
  const [result, setResult] = useState<string | null>(null);
  const convert = useMutation({
    mutationFn: (body: unknown) => convertEnquiryToTrip(enquiry.reference, body),
    onSuccess: async trip => { setResult(trip.reference); await onConverted(await getEnquiry(enquiry.reference)); }
  });
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError("");
    const f = new FormData(event.currentTarget); const t = (n: string) => String(f.get(n) ?? "").trim();
    try {
      convert.mutate({ version: enquiry.version, vehicleId: t("vehicleId"), date: t("date"), startTime: t("startTime"),
        durationMinutes: Math.round(Number(t("hours")) * 60), pickupLocation: t("pickupLocation"), destination: t("destination"),
        passengers: Number(t("passengers")), manualTotalCents: randToCents(t("manualTotal")) });
    } catch (err) { setError((err as Error).message); }
  }
  return <form className="admin-convert" onSubmit={submit}>
    <h3>Create a private-trip booking request</h3>
    <p className="form-note">Customer details are copied from this enquiry. Leave the manual total blank to price from the vehicle's live hourly rate. The request is then confirmed from Bookings, where availability is checked.</p>
    <div className="form-grid admin-form-grid">
      <label>Vehicle<select name="vehicleId" required defaultValue=""><option value="" disabled>Choose a vehicle</option>
        {vehicles.data?.filter(v => v.operationalStatus === "ACTIVE").map(v => <option key={v.id} value={v.id}>{v.displayName} · {categoryLabels[v.category]} · {v.capacity} seats</option>)}</select></label>
      <label>Date<input name="date" type="date" required defaultValue={enquiry.requestedDate ?? ""} /></label>
      <label>Start time<input name="startTime" type="time" required defaultValue={enquiry.requestedTime?.slice(0, 5) ?? "09:00"} /></label>
      <label>Hours<input name="hours" type="number" min={1} max={24} step={0.5} required defaultValue={enquiry.durationMinutes ? enquiry.durationMinutes / 60 : 4} /></label>
      <label>Passengers<input name="passengers" type="number" min={1} max={80} required defaultValue={enquiry.passengers ?? 2} /></label>
      <label>Pickup location<input name="pickupLocation" required maxLength={240} defaultValue={enquiry.pickupLocation ?? ""} /></label>
      <label className="full-field">Destination or itinerary<textarea name="destination" required maxLength={1200} rows={3} defaultValue={enquiry.destination ?? ""} /></label>
      <label>Manual total (ZAR) <em className="optional">(optional)</em><input name="manualTotal" inputMode="decimal" /></label>
    </div>
    {(error || convert.isError) && <ErrorState message={error || convert.error!.message} />}
    {result && <p role="status">Booking request {result} created.</p>}
    <button className="button button-primary" type="submit" disabled={convert.isPending}>{convert.isPending ? "Creating…" : "Create booking request"}</button>
  </form>;
}
