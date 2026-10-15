import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import {
  createDriver, createDriverAccount, markDriverGenuine, getDriver, listDrivers, reviewDriverDocument, setDriverAccountEnabled, setDriverVerification,
  updateDriver, uploadDriverDocument
} from "./fleetApi";
import { dateTime, humanize } from "./labels";
import { UpcomingTrips } from "./AssignmentsPanel";
import { SampleNotice } from "./VehiclesPanel";
import type { DriverDetail, DriverDocument } from "../../types/api";

const documentTypes = { DRIVING_LICENCE: "Driving licence", PRDP: "Professional Driving Permit (PrDP)", IDENTITY_DOCUMENT: "Identity document", OTHER: "Other" };

export function DriversPanel() {
  const [selected, setSelected] = useState<string | "new" | null>(null);
  const drivers = useQuery({ queryKey: ["admin", "drivers"], queryFn: listDrivers, retry: false });
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">People</p><h1>Drivers</h1>
      <p>Driver accounts, verification documents and eligibility. Only eligible, verified drivers can be assigned or collect a vehicle.</p></div>
      <button className="button button-primary" onClick={() => setSelected("new")}>Add driver</button></div>
    {drivers.isLoading && <LoadingState label="Loading drivers" />}{drivers.isError && <ErrorState message={drivers.error.message} />}
    {drivers.data && <div className="admin-table-wrap"><table><thead><tr><th>Driver</th><th>Sign-in</th><th>Verification</th><th>Licence / PrDP</th><th>Eligible today</th><th>Details</th></tr></thead><tbody>
      {drivers.data.map(d => <tr key={d.id}>
        <td>{d.name}<small>{d.contact}</small>{d.demoData && <span className="admin-badge sample-badge">Sample data</span>}</td>
        <td>{d.email ?? <span className="admin-badge">No account</span>}{d.email && !d.accountEnabled && <span className="admin-badge status-draft">Disabled</span>}</td>
        <td><span className={`admin-badge verification-${d.verificationStatus.toLowerCase()}`}>{humanize(d.verificationStatus)}</span>
          {d.documentsAwaitingReview > 0 && <small>{d.documentsAwaitingReview} document(s) to review</small>}</td>
        <td>{d.licenceExpiresOn ?? "—"}<small>PrDP {d.prdpExpiresOn ?? "—"}</small></td>
        <td>{d.eligibilityProblems.length ? <small className="admin-warning">{d.eligibilityProblems[0]}{d.eligibilityProblems.length > 1 ? ` (+${d.eligibilityProblems.length - 1})` : ""}</small> : <span className="admin-badge status-active">Eligible</span>}</td>
        <td><button className="button button-secondary" onClick={() => setSelected(d.id)}>Open</button></td>
      </tr>)}
    </tbody></table>{!drivers.data.length && <p className="admin-empty">No drivers yet.</p>}</div>}
    {selected === "new" && <NewDriver onCreated={id => setSelected(id)} onCancel={() => setSelected(null)} />}
    {selected && selected !== "new" && <DriverView key={selected} id={selected} onClose={() => setSelected(null)} />}
  </section>;
}

function NewDriver({ onCreated, onCancel }: { onCreated: (id: string) => void; onCancel: () => void }) {
  const client = useQueryClient();
  const create = useMutation({ mutationFn: createDriver, onSuccess: async d => { await client.invalidateQueries({ queryKey: ["admin", "drivers"] }); onCreated(d.id); } });
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const f = new FormData(event.currentTarget); const t = (n: string) => String(f.get(n) ?? "").trim() || null;
    create.mutate({ name: t("name"), email: t("email"), contact: t("contact"), initialPassword: t("initialPassword"), licenceNumber: t("licenceNumber"),
      licenceExpiresOn: t("licenceExpiresOn"), prdpExpiresOn: t("prdpExpiresOn") });
  }
  return <form className="form-surface admin-editor" onSubmit={submit}>
    <div className="admin-heading"><h2>Add a driver</h2><button type="button" className="button button-secondary" onClick={onCancel}>Cancel</button></div>
    <p className="form-note">This creates a driver sign-in. Give the driver the initial password in person; drivers can never change their own role.</p>
    <div className="form-grid admin-form-grid">
      <label>Full name<input name="name" required maxLength={160} /></label>
      <label>Phone<input name="contact" maxLength={120} type="tel" /></label>
      <label>Sign-in email<input name="email" type="email" required maxLength={180} /></label>
      <label>Initial password<input name="initialPassword" type="password" required minLength={12} maxLength={64} autoComplete="new-password" /></label>
      <label>Licence number<input name="licenceNumber" maxLength={40} /></label>
      <label>Licence expiry<input name="licenceExpiresOn" type="date" /></label>
      <label>PrDP expiry<input name="prdpExpiresOn" type="date" /></label>
    </div>
    {create.isError && <ErrorState message={create.error.message} />}
    <button className="button button-primary" type="submit" disabled={create.isPending}>{create.isPending ? "Creating…" : "Create driver"}</button>
  </form>;
}

function DriverView({ id, onClose }: { id: string; onClose: () => void }) {
  const client = useQueryClient();
  const driver = useQuery({ queryKey: ["admin", "driver", id], queryFn: () => getDriver(id), retry: false });
  const refresh = async (next: DriverDetail) => { client.setQueryData(["admin", "driver", id], next); await client.invalidateQueries({ queryKey: ["admin", "drivers"] }); };
  const update = useMutation({ mutationFn: (body: unknown) => updateDriver(id, body), onSuccess: refresh });
  const account = useMutation({ mutationFn: (enabled: boolean) => setDriverAccountEnabled(id, enabled), onSuccess: refresh });
  const [decision, setDecision] = useState("VERIFIED"); const [decisionNote, setDecisionNote] = useState("");
  const verify = useMutation({ mutationFn: (d: DriverDetail) => setDriverVerification(id, d.version, decision, decisionNote), onSuccess: async next => { setDecisionNote(""); await refresh(next); } });
  if (driver.isLoading) return <LoadingState label="Loading driver" />;
  if (driver.isError) return <ErrorState message={driver.error.message} />;
  const d = driver.data!;
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const f = new FormData(event.currentTarget); const t = (n: string) => String(f.get(n) ?? "").trim() || null;
    update.mutate({ version: d.version, name: t("name"), contact: t("contact"), status: t("status"), notes: t("notes"), licenceNumber: t("licenceNumber"),
      licenceExpiresOn: t("licenceExpiresOn"), prdpExpiresOn: t("prdpExpiresOn") });
  }
  return <section className="form-surface admin-detail" aria-label="Driver details">
    <div className="admin-heading"><div><p className="eyebrow">Driver</p><h2>{d.name}</h2></div><button className="button button-secondary" onClick={onClose}>Close</button></div>
    {d.demoData && <SampleNotice isAdmin onConfirm={() => markDriverGenuine(id)} queryKey={["admin", "driver", id]} subject="driver" />}
    {d.eligibilityProblems.length > 0 ? <div className="admin-callout"><strong>Not eligible today</strong><ul>{d.eligibilityProblems.map(p => <li key={p}>{p}</li>)}</ul></div>
      : <p className="admin-ok">Eligible to drive today.</p>}
    <UpcomingTrips trips={d.upcomingAssignments} subject="driver" />
    <h3>Sign-in</h3>
    {d.email ? <div className="admin-inline-form"><span>{d.email} · {d.accountEnabled ? "enabled" : "disabled"}</span>
      <button className="button button-secondary" onClick={() => account.mutate(!d.accountEnabled)} disabled={account.isPending}>{d.accountEnabled ? "Disable sign-in" : "Enable sign-in"}</button></div>
      : <CreateAccount id={id} onCreated={refresh} />}
    {account.isError && <ErrorState message={account.error.message} />}
    <form key={d.version} onSubmit={save}><h3>Profile and licence</h3><div className="form-grid admin-form-grid">
      <label>Full name<input name="name" required maxLength={160} defaultValue={d.name} /></label>
      <label>Phone<input name="contact" maxLength={120} defaultValue={d.contact ?? ""} /></label>
      <label>Status<select name="status" defaultValue={d.status}><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></select></label>
      <label>Licence number<input name="licenceNumber" maxLength={40} defaultValue={d.licenceNumber ?? ""} /></label>
      <label>Licence expiry<input name="licenceExpiresOn" type="date" defaultValue={d.licenceExpiresOn ?? ""} /></label>
      <label>PrDP expiry<input name="prdpExpiresOn" type="date" defaultValue={d.prdpExpiresOn ?? ""} /></label>
      <label className="full-field">Internal notes<textarea name="notes" rows={2} maxLength={2000} defaultValue={d.notes ?? ""} /></label>
    </div><p className="form-note">Changing licence details returns a verified driver to “pending” until they are re-verified.</p>
      {update.isError && <ErrorState message={update.error.message} />}
      <button className="button button-secondary" type="submit" disabled={update.isPending}>Save profile</button></form>
    <h3>Verification documents</h3>
    <p className="form-note">Private: only administrators can open these files.</p>
    <div className="admin-table-wrap"><table><thead><tr><th>Document</th><th>Expires</th><th>Status</th><th>Review</th></tr></thead><tbody>
      {d.documents.map(doc => <DocumentRow key={doc.id} driverId={id} doc={doc} onReviewed={refresh} />)}
    </tbody></table>{!d.documents.length && <p className="admin-empty">No documents uploaded.</p>}</div>
    <UploadDocument id={id} onUploaded={refresh} />
    <h3>Verification decision</h3>
    <p>Current: <span className={`admin-badge verification-${d.verificationStatus.toLowerCase()}`}>{humanize(d.verificationStatus)}</span>
      {d.verifiedBy && <small> by {d.verifiedBy} on {dateTime(d.verifiedAt)}{d.verificationNotes ? ` — ${d.verificationNotes}` : ""}</small>}</p>
    <form className="admin-inline-form" onSubmit={event => { event.preventDefault(); verify.mutate(d); }}>
      <label>Decision<select value={decision} onChange={event => setDecision(event.target.value)}>
        <option value="VERIFIED">Verified</option><option value="PENDING">Pending</option><option value="REJECTED">Rejected</option><option value="SUSPENDED">Suspended</option></select></label>
      <label className="admin-grow">Note {decision === "VERIFIED" && <em className="optional">(optional)</em>}<input value={decisionNote} onChange={event => setDecisionNote(event.target.value)} maxLength={1000} required={decision !== "VERIFIED"} /></label>
      <button className="button button-primary" type="submit" disabled={verify.isPending}>Record decision</button>
    </form>
    <p className="form-note">Verification requires a licence number, current licence and PrDP expiry dates, and accepted licence and PrDP documents.</p>
    {verify.isError && <ErrorState message={verify.error.message} />}
  </section>;
}

function CreateAccount({ id, onCreated }: { id: string; onCreated: (d: DriverDetail) => Promise<void> }) {
  const [email, setEmail] = useState(""); const [password, setPassword] = useState("");
  const create = useMutation({ mutationFn: () => createDriverAccount(id, email, password), onSuccess: onCreated });
  return <form className="admin-inline-form" onSubmit={event => { event.preventDefault(); create.mutate(); }}>
    <label>Sign-in email<input type="email" value={email} onChange={event => setEmail(event.target.value)} required maxLength={180} /></label>
    <label>Initial password<input type="password" value={password} onChange={event => setPassword(event.target.value)} required minLength={12} maxLength={64} autoComplete="new-password" /></label>
    <button className="button button-secondary" type="submit" disabled={create.isPending}>Create sign-in</button>
    {create.isError && <ErrorState message={create.error.message} />}
  </form>;
}

function DocumentRow({ driverId, doc, onReviewed }: { driverId: string; doc: DriverDocument; onReviewed: (d: DriverDetail) => Promise<void> }) {
  const [note, setNote] = useState("");
  const review = useMutation({ mutationFn: (status: string) => reviewDriverDocument(driverId, doc.id, doc.version, status, note), onSuccess: onReviewed });
  return <tr>
    <td><a href={`/api/v1/admin/media/${doc.mediaId}`} target="_blank" rel="noopener noreferrer">{documentTypes[doc.type]}</a><small>Uploaded {dateTime(doc.createdAt)}</small></td>
    <td>{doc.expiresOn ?? "—"}</td>
    <td><span className="admin-badge">{humanize(doc.status)}</span>{doc.reviewNotes && <small>{doc.reviewNotes}</small>}</td>
    <td>{doc.status === "PENDING_REVIEW" ? <div className="admin-row-actions">
      <input aria-label="Review note" value={note} onChange={event => setNote(event.target.value)} placeholder="Note (required to reject)" maxLength={1000} />
      <button className="button button-secondary" onClick={() => review.mutate("ACCEPTED")} disabled={review.isPending}>Accept</button>
      <button className="button button-quiet" onClick={() => review.mutate("REJECTED")} disabled={review.isPending || !note.trim()}>Reject</button>
      {review.isError && <small className="admin-warning">{review.error.message}</small>}</div> : <small>{doc.reviewedBy} · {dateTime(doc.reviewedAt)}</small>}</td>
  </tr>;
}

function UploadDocument({ id, onUploaded }: { id: string; onUploaded: (d: DriverDetail) => Promise<void> }) {
  const [type, setType] = useState("DRIVING_LICENCE"); const [expires, setExpires] = useState(""); const [file, setFile] = useState<File | null>(null);
  const upload = useMutation({ mutationFn: () => uploadDriverDocument(id, type, expires, file!), onSuccess: async next => { setFile(null); setExpires(""); await onUploaded(next); } });
  return <form className="admin-inline-form" onSubmit={event => { event.preventDefault(); if (file) upload.mutate(); }}>
    <label>Type<select value={type} onChange={event => setType(event.target.value)}>{Object.entries(documentTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
    <label>Expires<input type="date" value={expires} onChange={event => setExpires(event.target.value)} /></label>
    <label>File (PDF or photo)<input type="file" accept="application/pdf,image/jpeg,image/png,image/webp" onChange={event => setFile(event.target.files?.[0] ?? null)} required /></label>
    <button className="button button-secondary" type="submit" disabled={!file || upload.isPending}>{upload.isPending ? "Uploading…" : "Upload document"}</button>
    {upload.isError && <ErrorState message={upload.error.message} />}
  </form>;
}
