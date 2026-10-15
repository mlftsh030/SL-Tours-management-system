import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { formatAmount } from "../../lib/format";
import { UpcomingTrips } from "./AssignmentsPanel";
import { listVehicleOptions } from "../private-trips/privateTripApi";
import { clearRestriction, getVehicle, markVehicleGenuine, listVehicles, openRestriction, removeVehicleImage, saveVehicle, uploadVehicleImage } from "./fleetApi";
import { categoryLabels, centsToRand, dateTime, humanize, randToCents } from "./labels";
import type { SaveVehicle, VehicleDetail, VehicleRestriction } from "../../types/api";

const categories = Object.keys(categoryLabels);

export function VehiclesPanel({ isAdmin }: { isAdmin: boolean }) {
  const [selected, setSelected] = useState<string | "new" | null>(null);
  const vehicles = useQuery({ queryKey: ["admin", "vehicles"], queryFn: listVehicles, retry: false });
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">Fleet</p><h1>Vehicles</h1>
      <p>Operational status, public listing and bookings are separate: a listed vehicle is offered only when it is operational, unrestricted and free.</p></div>
      {isAdmin && <button className="button button-primary" onClick={() => setSelected("new")}>Add vehicle</button>}</div>
    {vehicles.isLoading && <LoadingState label="Loading vehicles" />}{vehicles.isError && <ErrorState message={vehicles.error.message} />}
    {vehicles.data && <div className="admin-table-wrap"><table><thead><tr><th>Vehicle</th><th>Category</th><th>Seats</th><th>Operational</th><th>Website</th><th>Restrictions</th><th>Rate</th><th>Details</th></tr></thead><tbody>
      {vehicles.data.map(v => <tr key={v.id}>
        <td><div className="admin-tour-cell">{v.imageUrl ? <img src={v.imageUrl} alt="" width="64" height="48" /> : <span className="admin-thumb-placeholder" aria-hidden="true" />}<span>{v.displayName}<small>{v.registration}</small>{v.demoData && <span className="admin-badge sample-badge">Sample data</span>}</span></div></td>
        <td>{categoryLabels[v.category]}</td><td>{v.capacity}</td>
        <td><span className={`admin-badge ${v.operationalStatus === "ACTIVE" ? "status-active" : "status-draft"}`}>{humanize(v.operationalStatus)}</span></td>
        <td><span className="admin-badge">{v.listingStatus === "LISTED" ? "Listed" : "Hidden"}</span></td>
        <td>{v.openRestrictions.length ? v.openRestrictions.map(r => <span key={r.id} className={`admin-badge restriction-${r.type.toLowerCase()}`}>{humanize(r.type)}</span>) : "None"}</td>
        <td>{v.hourlyRateCents ? `${formatAmount(v.hourlyRateCents)}/h` : <small>Category rate</small>}</td>
        <td><button className="button button-secondary" onClick={() => setSelected(v.id)}>Open</button></td>
      </tr>)}
    </tbody></table>{!vehicles.data.length && <p className="admin-empty">No vehicles yet.</p>}</div>}
    {selected === "new" && <VehicleEditor isAdmin={isAdmin} onDone={id => setSelected(id)} />}
    {selected && selected !== "new" && <VehicleDetailView key={selected} id={selected} isAdmin={isAdmin} onClose={() => setSelected(null)} />}
  </section>;
}

function VehicleDetailView({ id, isAdmin, onClose }: { id: string; isAdmin: boolean; onClose: () => void }) {
  const vehicle = useQuery({ queryKey: ["admin", "vehicle", id], queryFn: () => getVehicle(id), retry: false });
  if (vehicle.isLoading) return <LoadingState label="Loading vehicle" />;
  if (vehicle.isError) return <ErrorState message={vehicle.error.message} />;
  const v = vehicle.data!;
  return <section className="form-surface admin-detail" aria-label="Vehicle details">
    <div className="admin-heading"><div><p className="eyebrow">{v.registration}</p><h2>{v.displayName}</h2></div><button className="button button-secondary" onClick={onClose}>Close</button></div>
    {v.demoData && <SampleNotice isAdmin={isAdmin} onConfirm={() => markVehicleGenuine(v.id)} queryKey={["admin", "vehicle", v.id]} subject="vehicle" />}
    <Restrictions vehicle={v} isAdmin={isAdmin} />
    <UpcomingTrips trips={v.upcomingAssignments} subject="vehicle" />
    {isAdmin ? <VehicleEditor key={v.version} vehicle={v} isAdmin onDone={() => undefined} /> : <dl className="admin-detail-grid">
      {[["Category", categoryLabels[v.category]], ["Seats", v.capacity], ["Luggage", v.luggageCapacity ?? "—"], ["Operational", humanize(v.operationalStatus)],
        ["Website", v.listingStatus === "LISTED" ? "Listed" : "Hidden"], ["Operating notes", v.operatingNotes ?? "—"]]
        .map(([label, value]) => <div key={String(label)}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>}
    <VehicleImages vehicle={v} isAdmin={isAdmin} />
  </section>;
}

function VehicleEditor({ vehicle, isAdmin, onDone }: { vehicle?: VehicleDetail; isAdmin: boolean; onDone: (id: string) => void }) {
  const client = useQueryClient();
  const [error, setError] = useState("");
  const models = useQuery({ queryKey: ["private-trips", "vehicle-options"], queryFn: listVehicleOptions });
  const save = useMutation({
    mutationFn: (payload: SaveVehicle) => saveVehicle(payload, vehicle?.id),
    onSuccess: async saved => {
      client.setQueryData(["admin", "vehicle", saved.id], saved);
      await client.invalidateQueries({ queryKey: ["admin", "vehicles"] });
      onDone(saved.id);
    }
  });
  if (!isAdmin) return null;
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError("");
    const form = new FormData(event.currentTarget);
    const text = (name: string) => String(form.get(name) ?? "").trim();
    try {
      const hourly = randToCents(text("hourlyRate"));
      const minimumHours = text("minimumHours");
      save.mutate({
        version: vehicle?.version ?? 0, registration: text("registration"), displayName: text("displayName"),
        category: text("category") as SaveVehicle["category"], capacity: Number(text("capacity")),
        luggageCapacity: text("luggageCapacity") ? Number(text("luggageCapacity")) : null,
        operationalStatus: text("operationalStatus") as SaveVehicle["operationalStatus"], listingStatus: text("listingStatus") as SaveVehicle["listingStatus"],
        publicDescription: text("publicDescription") || null, operatingNotes: text("operatingNotes") || null, notes: text("notes") || null,
        hourlyRateCents: hourly, minimumMinutes: minimumHours ? Math.round(Number(minimumHours) * 60) : null,
        amenities: text("amenities").split("\n").map(a => a.trim()).filter(Boolean),
        vehicleOptionSlug: text("vehicleOptionSlug") || null
      });
    } catch (err) { setError((err as Error).message); }
  }
  return <form className={vehicle ? "admin-subform" : "form-surface admin-editor"} onSubmit={submit}>
    <h3>{vehicle ? "Vehicle details" : "Add a vehicle"}</h3>
    <div className="form-grid admin-form-grid">
      <label>Display name<input name="displayName" required maxLength={120} defaultValue={vehicle?.displayName} placeholder="Mercedes-Benz V-Class" /></label>
      <label>Registration<input name="registration" required maxLength={40} defaultValue={vehicle?.registration} placeholder="CA 123 456" /></label>
      <label>Category<select name="category" defaultValue={vehicle?.category ?? "VAN"}>{categories.map(c => <option key={c} value={c}>{categoryLabels[c]}</option>)}</select></label>
      {/* Mounted only once the models have loaded, so the saved link is the selected default and is never cleared. */}
      {models.data ? <label>Model shown to customers <small>Customers who choose this model are offered this vehicle</small>
        <select name="vehicleOptionSlug" defaultValue={vehicle?.vehicleOptionSlug ?? ""}>
          <option value="">Not one of the published models</option>
          {models.data.map(m => <option key={m.slug} value={m.slug}>{m.name} ({m.typeLabel})</option>)}</select></label>
        : <input type="hidden" name="vehicleOptionSlug" value={vehicle?.vehicleOptionSlug ?? ""} />}
      <label>Passenger seats<input name="capacity" type="number" required min={1} max={80} defaultValue={vehicle?.capacity ?? 4} /></label>
      <label>Large bags <em className="optional">(optional)</em><input name="luggageCapacity" type="number" min={0} max={80} defaultValue={vehicle?.luggageCapacity ?? ""} /></label>
      <label>Operational status<select name="operationalStatus" defaultValue={vehicle?.operationalStatus ?? "ACTIVE"}>
        <option value="ACTIVE">Active — can be driven</option><option value="OUT_OF_SERVICE">Out of service</option><option value="RETIRED">Retired</option></select></label>
      <label>Website listing<select name="listingStatus" defaultValue={vehicle?.listingStatus ?? "HIDDEN"}>
        <option value="HIDDEN">Hidden from the website</option><option value="LISTED">Listed for private trips</option></select></label>
      <label>Hourly rate override (ZAR) <em className="optional">(optional)</em><input name="hourlyRate" inputMode="decimal" defaultValue={centsToRand(vehicle?.hourlyRateCents)} placeholder="Uses the category rate" /></label>
      <label>Minimum hours override <em className="optional">(optional)</em><input name="minimumHours" type="number" min={1} max={24} step={0.5} defaultValue={vehicle?.minimumMinutes ? vehicle.minimumMinutes / 60 : ""} /></label>
      <label className="full-field">Public description<textarea name="publicDescription" rows={2} maxLength={500} defaultValue={vehicle?.publicDescription ?? ""} /></label>
      <label className="full-field">Amenities <small>One per line, shown on the website card</small><textarea name="amenities" rows={3} defaultValue={vehicle?.amenities.join("\n") ?? ""} /></label>
      <label className="full-field">Notes for the assigned driver<textarea name="operatingNotes" rows={2} maxLength={1000} defaultValue={vehicle?.operatingNotes ?? ""} placeholder="Fuel type, quirks, where the keys are kept" /></label>
      <label className="full-field">Internal fleet notes <small>Never shown to drivers or the public</small><textarea name="notes" rows={2} maxLength={2000} defaultValue={vehicle?.notes ?? ""} /></label>
    </div>
    {(error || save.isError) && <ErrorState message={error || save.error!.message} />}
    <div className="admin-actions"><button className="button button-primary" type="submit" disabled={save.isPending}>{save.isPending ? "Saving…" : "Save vehicle"}</button></div>
  </form>;
}

function Restrictions({ vehicle, isAdmin }: { vehicle: VehicleDetail; isAdmin: boolean }) {
  const client = useQueryClient();
  const refresh = (next: VehicleDetail) => { client.setQueryData(["admin", "vehicle", vehicle.id], next); return client.invalidateQueries({ queryKey: ["admin", "vehicles"] }); };
  const [type, setType] = useState("MAINTENANCE"); const [reason, setReason] = useState("");
  const open = useMutation({ mutationFn: () => openRestriction(vehicle.id, type, reason), onSuccess: async next => { setReason(""); await refresh(next); } });
  const openOnes = vehicle.restrictions.filter(r => !r.clearedAt);
  return <div className="admin-restrictions">
    <h3>Safety and maintenance restrictions</h3>
    <p className="form-note">An open restriction blocks collection, website availability and new assignments. Approving an inspection never clears it; an administrator must record the repair here.</p>
    {openOnes.length === 0 && <p className="admin-ok">No open restrictions.</p>}
    {openOnes.map(r => <RestrictionCard key={r.id} vehicleId={vehicle.id} restriction={r} isAdmin={isAdmin} onCleared={refresh} />)}
    <form className="admin-inline-form" onSubmit={event => { event.preventDefault(); open.mutate(); }}>
      <label>Report a problem<select value={type} onChange={event => setType(event.target.value)}><option value="MAINTENANCE">Maintenance</option><option value="SAFETY">Safety</option></select></label>
      <label className="admin-grow">Reason<input value={reason} onChange={event => setReason(event.target.value)} minLength={5} maxLength={1000} required placeholder="What is wrong?" /></label>
      <button className="button button-secondary" type="submit" disabled={open.isPending}>Open restriction</button>
    </form>
    {open.isError && <ErrorState message={open.error.message} />}
    {vehicle.restrictions.some(r => r.clearedAt) && <details className="admin-history"><summary>Restriction history</summary><ul>
      {vehicle.restrictions.filter(r => r.clearedAt).map(r => <li key={r.id}><strong>{humanize(r.type)}</strong>: {r.reason}<small>Opened {dateTime(r.openedAt)} by {r.openedBy} · cleared {dateTime(r.clearedAt)} by {r.clearedBy}: {r.clearanceNote}</small></li>)}
    </ul></details>}
  </div>;
}

function RestrictionCard({ vehicleId, restriction, isAdmin, onCleared }: { vehicleId: string; restriction: VehicleRestriction; isAdmin: boolean; onCleared: (v: VehicleDetail) => Promise<void> }) {
  const [note, setNote] = useState("");
  const clear = useMutation({ mutationFn: () => clearRestriction(vehicleId, restriction.id, restriction.version, note), onSuccess: onCleared });
  return <article className={`admin-restriction restriction-${restriction.type.toLowerCase()}`}>
    <strong>{restriction.type === "SAFETY" ? "Safety restriction" : "Maintenance restriction"}</strong>
    <p>{restriction.reason}</p><small>Opened {dateTime(restriction.openedAt)} by {restriction.openedBy}{restriction.sourceInspectionId ? " from an inspection" : ""}</small>
    {isAdmin && <form className="admin-inline-form" onSubmit={event => { event.preventDefault(); clear.mutate(); }}>
      <label className="admin-grow">Repair or check carried out<input value={note} onChange={event => setNote(event.target.value)} minLength={10} maxLength={1000} required placeholder="e.g. Brake pads replaced by Cape Auto, road tested" /></label>
      <button className="button button-primary" type="submit" disabled={clear.isPending || note.trim().length < 10}>Clear restriction</button>
    </form>}
    {clear.isError && <ErrorState message={clear.error.message} />}
  </article>;
}

function VehicleImages({ vehicle, isAdmin }: { vehicle: VehicleDetail; isAdmin: boolean }) {
  const client = useQueryClient();
  const refresh = (next: VehicleDetail) => { client.setQueryData(["admin", "vehicle", vehicle.id], next); return client.invalidateQueries({ queryKey: ["admin", "vehicles"] }); };
  const [file, setFile] = useState<File | null>(null); const [alt, setAlt] = useState("");
  const upload = useMutation({ mutationFn: () => uploadVehicleImage(vehicle.id, file!, alt), onSuccess: async next => { setFile(null); setAlt(""); await refresh(next); } });
  const remove = useMutation({ mutationFn: (imageId: string) => removeVehicleImage(vehicle.id, imageId), onSuccess: refresh });
  return <div className="admin-images"><h3>Website photos</h3>
    <p className="form-note">The first photo appears on the public vehicle card. Photos are public; never upload documents or number-plate close-ups.</p>
    <div className="admin-image-grid">{vehicle.images.map(image => <figure key={image.id}><img src={image.url} alt={image.altText} loading="lazy" /><figcaption>{image.altText}</figcaption>
      {isAdmin && <button className="button button-quiet" onClick={() => remove.mutate(image.id)} disabled={remove.isPending}>Remove</button>}</figure>)}
      {!vehicle.images.length && <p className="admin-empty">No photos yet. The website shows a neutral placeholder.</p>}</div>
    {isAdmin && <form className="admin-inline-form" onSubmit={event => { event.preventDefault(); if (file) upload.mutate(); }}>
      <label>Photo<input type="file" accept="image/jpeg,image/png,image/webp" onChange={event => setFile(event.target.files?.[0] ?? null)} required /></label>
      <label className="admin-grow">Alternative text<input value={alt} onChange={event => setAlt(event.target.value)} maxLength={200} required placeholder="Silver Mercedes V-Class parked at Camps Bay" /></label>
      <button className="button button-secondary" type="submit" disabled={!file || !alt.trim() || upload.isPending}>{upload.isPending ? "Uploading…" : "Upload photo"}</button>
    </form>}
    {(upload.isError || remove.isError) && <ErrorState message={(upload.error ?? remove.error)!.message} />}
  </div>;
}

/** Explains a V2 sample record and lets an administrator confirm it is real. */
export function SampleNotice({ isAdmin, onConfirm, queryKey, subject }: { isAdmin: boolean; onConfirm: () => Promise<unknown>; queryKey: unknown[]; subject: string }) {
  const client = useQueryClient();
  const confirm = useMutation({ mutationFn: onConfirm, onSuccess: async next => { client.setQueryData(queryKey, next); await client.invalidateQueries({ queryKey: ["admin"] }); } });
  return <div className="admin-callout"><strong>Development sample data</strong>
    <p>This {subject} was inserted as sample data by an early migration. Outside development it is never offered to customers or assigned to trips.
      If it is a real {subject}, check every field first, then confirm it.</p>
    {isAdmin && <button className="button button-secondary" onClick={() => { if (window.confirm(`Confirm this ${subject} is real?`)) confirm.mutate(); }} disabled={confirm.isPending}>Mark as genuine</button>}
    {confirm.isError && <ErrorState message={confirm.error.message} />}
  </div>;
}
