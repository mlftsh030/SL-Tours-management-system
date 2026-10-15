import { capeTown } from "../../lib/dates";
import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { assignmentsNeedingAttention, cancelAssignment, changeAssignment, compareInspections, listAssignments, pendingInspections, resourceOptions, reviewInspection } from "./fleetApi";
import { assignmentStatusLabels, dateTime, fuelLabels, humanize } from "./labels";
import type { AdminAssignment, InspectionView, ResourceOptions } from "../../types/api";

const stateLabels: Record<string, string> = {
  NOT_SUBMITTED: "Not submitted", PENDING_REVIEW: "Awaiting review", APPROVED: "Approved", SAFETY_DEFECT: "Safety defect", REJECTED: "Rejected"
};

export function AssignmentsPanel({ isAdmin }: { isAdmin: boolean }) {
  const [scope, setScope] = useState<"active" | "recent">("active");
  const [selected, setSelected] = useState<AdminAssignment | null>(null);
  const [params, setParams] = useSearchParams();
  const pending = useQuery({ queryKey: ["admin", "inspections", "pending"], queryFn: pendingInspections, retry: false });
  const attention = useQuery({ queryKey: ["admin", "assignments", "attention"], queryFn: assignmentsNeedingAttention, retry: false });
  const assignments = useQuery({ queryKey: ["admin", "assignments", scope], queryFn: () => listAssignments(scope), retry: false });
  const open = (assignmentId: string) => {
    const match = assignments.data?.find(a => a.id === assignmentId) ?? attention.data?.find(a => a.id === assignmentId);
    if (match) setSelected(match);
  };
  // Links from vehicle and driver screens open a specific assignment.
  const requested = params.get("assignment");
  useEffect(() => {
    if (requested && assignments.data) { open(requested); setParams({}, { replace: true }); }
  }, [requested, assignments.data]);
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">Operations</p><h1>Trips &amp; inspections</h1>
      <p>Vehicle and driver assignments with their collection and return inspections.</p></div></div>
    {attention.data && attention.data.length > 0 && <>
      <h2 className="admin-subheading">Needs attention</h2>
      <div className="admin-review-list">{attention.data.map(a => <button key={a.id} className="admin-review-item is-safety" onClick={() => setSelected(a)}>
        <strong>{dateTime(a.startsAt)} · {a.title} · {a.bookingReference}</strong>
        <span>{a.vehicleName} · {a.driverName}</span>
        {a.problems.map(p => <span key={p} className="admin-warning">{p}</span>)}
      </button>)}</div>
    </>}
    <h2 className="admin-subheading">Awaiting review</h2>
    {pending.isLoading && <LoadingState label="Loading inspections" />}{pending.isError && <ErrorState message={pending.error.message} />}
    {pending.data && (pending.data.length ? <div className="admin-review-list">{pending.data.map(p => <button key={p.inspectionId} className={`admin-review-item${p.hasSafetyDefect ? " is-safety" : ""}`} onClick={() => open(p.assignmentId)}>
      <strong>{p.type === "COLLECTION" ? "Collection" : "Return"} · {p.vehicleName}</strong>
      <span>{p.driverName} · {p.bookingReference} · {dateTime(p.submittedAt)}</span>
      {p.hasSafetyDefect ? <span className="admin-badge restriction-safety">Safety defect</span> : p.hasDefect ? <span className="admin-badge restriction-maintenance">Defects reported</span> : <span className="admin-badge status-active">No defects</span>}
    </button>)}</div> : <p className="admin-ok">Nothing to review.</p>)}
    <div className="admin-heading admin-heading-tight"><h2 className="admin-subheading">Assignments</h2>
      <label>Show<select value={scope} onChange={event => setScope(event.target.value as "active" | "recent")}><option value="active">Open work</option><option value="recent">Last 14 days and upcoming</option></select></label></div>
    {assignments.isLoading && <LoadingState label="Loading assignments" />}{assignments.isError && <ErrorState message={assignments.error.message} />}
    {assignments.data && <div className="admin-table-wrap"><table><thead><tr><th>When</th><th>Trip</th><th>Vehicle</th><th>Driver</th><th>Status</th><th>Collection</th><th>Return</th><th></th></tr></thead><tbody>
      {assignments.data.map(a => <tr key={a.id}>
        <td>{dateTime(a.startsAt)}<small>until {capeTown(a.endsAt, { timeStyle: "short" })}</small></td>
        <td>{a.title}<small>{a.bookingReference} · {a.passengers} pax</small></td>
        <td>{a.vehicleName}<small>{a.registration}</small></td><td>{a.driverName}</td>
        <td><span className="admin-badge">{assignmentStatusLabels[a.status]}</span></td>
        <td><small>{stateLabels[a.collectionState] ?? a.collectionState}</small></td><td><small>{stateLabels[a.returnState] ?? a.returnState}</small></td>
        <td><button className="button button-secondary" onClick={() => setSelected(a)}>Open</button></td>
      </tr>)}
    </tbody></table>{!assignments.data.length && <p className="admin-empty">No assignments.</p>}</div>}
    {selected && <AssignmentDetail key={selected.id} assignment={selected} isAdmin={isAdmin} onClose={() => setSelected(null)} />}
  </section>;
}

function AssignmentDetail({ assignment, isAdmin, onClose }: { assignment: AdminAssignment; isAdmin: boolean; onClose: () => void }) {
  const comparison = useQuery({ queryKey: ["admin", "comparison", assignment.id], queryFn: () => compareInspections(assignment.id), retry: false });
  const c = comparison.data;
  return <section className="form-surface admin-detail" aria-label="Assignment details">
    <div className="admin-heading"><div><p className="eyebrow">{assignment.bookingReference}</p><h2>{assignment.title}</h2>
      <p>{dateTime(assignment.startsAt)} – {dateTime(assignment.endsAt)} · {assignment.vehicleName} ({assignment.registration}) · {assignment.driverName}</p></div>
      <button className="button button-secondary" onClick={onClose}>Close</button></div>
    {assignment.problems.length > 0 && <div className="admin-callout" role="alert"><strong>This trip cannot go ahead as planned</strong>
      <ul>{assignment.problems.map(p => <li key={p}>{p}</li>)}</ul>{isAdmin && <p>Choose another vehicle or driver below.</p>}</div>}
    {comparison.isLoading && <LoadingState label="Loading inspections" />}{comparison.isError && <ErrorState message={comparison.error.message} />}
    {c && <>
      {c.collection && c.returned && <div className="admin-metrics admin-compare-metrics">
        <article className="admin-metric"><span>Distance travelled</span><strong>{c.distanceKm} km</strong></article>
        <article className="admin-metric"><span>Fuel change</span><strong>{c.fuelChangePercent! > 0 ? "+" : ""}{c.fuelChangePercent}%</strong></article>
        <article className="admin-metric"><span>Newly reported defects</span><strong>{c.newlyReportedDefects.length}</strong></article>
      </div>}
      {c.newlyReportedDefects.length > 0 && <div className="admin-callout"><strong>New since collection</strong><ul>{c.newlyReportedDefects.map(d => <li key={d.item}>{d.label}: {d.note}</li>)}</ul></div>}
      <div className="admin-compare">
        <InspectionCard title="Collection" inspection={c.collection} isAdmin={isAdmin} assignmentId={assignment.id} />
        <InspectionCard title="Return" inspection={c.returned} isAdmin={isAdmin} assignmentId={assignment.id} />
      </div>
    </>}
    {isAdmin && (assignment.status === "SCHEDULED" || assignment.status === "AWAITING_RELEASE") && <ChangeAssignment assignment={assignment} />}
  </section>;
}

function InspectionCard({ title, inspection, isAdmin, assignmentId }: { title: string; inspection: InspectionView | null; isAdmin: boolean; assignmentId: string }) {
  const client = useQueryClient();
  const [notes, setNotes] = useState("");
  const review = useMutation({
    mutationFn: (decision: "approve" | "reject") => reviewInspection(inspection!.id, decision, inspection!.version, notes),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ["admin"] }); await client.invalidateQueries({ queryKey: ["admin", "comparison", assignmentId] }); }
  });
  if (!inspection) return <article className="admin-inspection is-empty"><h3>{title}</h3><p>Not submitted yet.</p></article>;
  return <article className={`admin-inspection${inspection.hasSafetyDefect ? " is-safety" : ""}`}>
    <h3>{title} <span className="admin-badge">{humanize(inspection.reviewStatus)}</span></h3>
    <p>{inspection.driverName} · {dateTime(inspection.submittedAt)}</p>
    <dl className="admin-detail-grid"><div><dt>Odometer</dt><dd>{inspection.odometerKm.toLocaleString("en-ZA")} km</dd></div><div><dt>Fuel</dt><dd>{fuelLabels[inspection.fuelLevel]}</dd></div></dl>
    {inspection.notes && <p className="admin-message">{inspection.notes}</p>}
    <ul className="admin-checklist">{inspection.checklist.map(line => <li key={line.item} className={line.result === "DEFECT" ? `is-defect is-${line.severity?.toLowerCase()}` : ""}>
      <span>{line.label}</span><strong>{line.result === "OK" ? "OK" : line.severity === "SAFETY" ? "Safety defect" : "Defect"}</strong>{line.note && <small>{line.note}</small>}</li>)}</ul>
    <div className="admin-photo-grid">
      {inspection.selfieUrl && <figure><a href={inspection.selfieUrl} target="_blank" rel="noopener noreferrer"><img src={inspection.selfieUrl} alt={`${title} selfie of ${inspection.driverName}`} loading="lazy" /></a><figcaption>Driver selfie</figcaption></figure>}
      {inspection.photos.map(p => <figure key={p.mediaId}><a href={p.url} target="_blank" rel="noopener noreferrer"><img src={p.url} alt={p.kind === "DEFECT" ? `Defect: ${p.checklistItem}` : "Vehicle photo"} loading="lazy" /></a>
        <figcaption>{p.kind === "DEFECT" ? humanize(p.checklistItem) : "Vehicle"}</figcaption></figure>)}
    </div>
    {inspection.reviewedBy && <p className="form-note">Reviewed by {inspection.reviewedBy} {dateTime(inspection.reviewedAt)}{inspection.reviewNotes ? `: ${inspection.reviewNotes}` : ""}</p>}
    {isAdmin && inspection.reviewStatus === "PENDING_REVIEW" && <div className="admin-review-actions">
      <label>Review note<input value={notes} onChange={event => setNotes(event.target.value)} maxLength={1000} placeholder="Required when rejecting" /></label>
      <div className="admin-actions">
        {!inspection.hasSafetyDefect && <button className="button button-primary" onClick={() => review.mutate("approve")} disabled={review.isPending}>
          {inspection.type === "COLLECTION" ? "Approve & release vehicle" : "Approve return"}</button>}
        <button className="button button-secondary" onClick={() => review.mutate("reject")} disabled={review.isPending || !notes.trim()}>Reject</button>
      </div>
      {inspection.hasSafetyDefect && <p className="form-note">Safety defects cannot be approved. Reject this inspection or assign another vehicle; clear the vehicle's restriction after repair.</p>}
      {review.isError && <ErrorState message={review.error.message} />}
    </div>}
  </article>;
}

export function ResourcePicker({ options, vehicleId, driverId, onVehicle, onDriver }: {
  options: ResourceOptions; vehicleId: string; driverId: string; onVehicle: (id: string) => void; onDriver: (id: string) => void;
}) {
  return <div className="form-grid admin-form-grid">
    <label>Vehicle<select value={vehicleId} onChange={event => onVehicle(event.target.value)} required><option value="">Choose a vehicle</option>
      {options.vehicles.map(v => <option key={v.id} value={v.id} disabled={!v.available}>{v.name} — {v.detail}{v.available ? "" : ` (${v.reasons.join("; ")})`}</option>)}</select></label>
    <label>Driver<select value={driverId} onChange={event => onDriver(event.target.value)} required><option value="">Choose a driver</option>
      {options.drivers.map(d => <option key={d.id} value={d.id} disabled={!d.available}>{d.name}{d.available ? "" : ` (${d.reasons.join("; ")})`}</option>)}</select></label>
    <p className="form-note full-field">Unavailable choices are disabled. Availability includes a {options.turnaroundMinutes}-minute turnaround and is checked again when you save.</p>
  </div>;
}

function ChangeAssignment({ assignment }: { assignment: AdminAssignment }) {
  const client = useQueryClient();
  const [vehicleId, setVehicleId] = useState(assignment.vehicleId ?? ""); const [driverId, setDriverId] = useState(assignment.driverId ?? "");
  const [reason, setReason] = useState("");
  const options = useQuery({ queryKey: ["admin", "options", assignment.id], queryFn: () => resourceOptions(assignment.startsAt, assignment.endsAt, assignment.passengers) });
  const done = async () => { await client.invalidateQueries({ queryKey: ["admin"] }); };
  const change = useMutation({ mutationFn: () => changeAssignment(assignment.id, assignment.version, vehicleId, driverId), onSuccess: done });
  const cancel = useMutation({ mutationFn: () => cancelAssignment(assignment.id, assignment.version, reason), onSuccess: done });
  if (!options.data) return null;
  // The current vehicle and driver are "already assigned" to this very trip; ignore only that reason.
  const ownBooking = (o: ResourceOptions["vehicles"][number], current: string | null) => {
    if (o.id !== current) return o;
    const reasons = o.reasons.filter(r => !r.startsWith("Already assigned"));
    return { ...o, reasons, available: reasons.length === 0 };
  };
  const withCurrent: ResourceOptions = { ...options.data,
    vehicles: options.data.vehicles.map(v => ownBooking(v, assignment.vehicleId)),
    drivers: options.data.drivers.map(d => ownBooking(d, assignment.driverId)) };
  return <div className="admin-convert"><h3>Change vehicle or driver</h3>
    <p className="form-note">Changing either rejects any collection inspection made for the previous vehicle or driver.</p>
    <ResourcePicker options={withCurrent} vehicleId={vehicleId} driverId={driverId} onVehicle={setVehicleId} onDriver={setDriverId} />
    <button className="button button-secondary" onClick={() => change.mutate()} disabled={change.isPending || (vehicleId === assignment.vehicleId && driverId === assignment.driverId)}>Save change</button>
    {change.isError && <ErrorState message={change.error.message} />}
    <form className="admin-inline-form" onSubmit={event => { event.preventDefault(); cancel.mutate(); }}>
      <label className="admin-grow">Cancel this assignment<input value={reason} onChange={event => setReason(event.target.value)} required maxLength={500} placeholder="Reason" /></label>
      <button className="button button-quiet" type="submit" disabled={cancel.isPending}>Cancel assignment</button>
    </form>
    {cancel.isError && <ErrorState message={cancel.error.message} />}
  </div>;
}

/** Upcoming trips for a vehicle or driver; trips the latest change has put at risk are highlighted. */
export function UpcomingTrips({ trips, subject }: { trips: AdminAssignment[]; subject: string }) {
  const affected = trips.filter(t => t.problems.length > 0);
  return <div className="admin-upcoming">
    <h3>Upcoming trips</h3>
    {affected.length > 0 && <div className="admin-callout" role="alert"><strong>{affected.length} upcoming {affected.length === 1 ? "trip is" : "trips are"} affected</strong>
      <p>Reassign {affected.length === 1 ? "it" : "them"} before the trip date. The usual availability, restriction and eligibility checks apply.</p></div>}
    {trips.length === 0 ? <p className="form-note">No upcoming trips use this {subject}.</p> : <ul className="admin-upcoming-list">{trips.map(t =>
      <li key={t.id} className={t.problems.length ? "is-affected" : ""}>
        <div><strong>{dateTime(t.startsAt)}</strong> · {t.title} <small>{t.bookingReference} · {t.vehicleName} · {t.driverName}</small>
          {t.problems.map(p => <small key={p} className="admin-warning">{p}</small>)}</div>
        <Link className="button button-secondary" to={`/operations/trips?assignment=${t.id}`}>{t.problems.length ? "Reassign" : "Open"}</Link>
      </li>)}</ul>}
  </div>;
}
