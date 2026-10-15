import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, Navigate, Route, Routes, useNavigate, useParams } from "react-router-dom";
import { CalendarClock, Car, ChevronRight, LogOut, MapPin, Phone, TriangleAlert, Users } from "lucide-react";
import { useSession } from "../auth/useSession";
import { signOut } from "../auth/authApi";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { capeTown, dayAndTime } from "../../lib/dates";
import { getDriverAssignment, getDriverProfile, listDriverAssignments } from "./driverApi";
import { InspectionForm, SubmissionOutcome } from "./InspectionForm";
import type { DriverStep, SubmissionResult } from "../../types/api";

const when = (iso: string) => dayAndTime(iso);
const time = (iso: string) => capeTown(iso, { hour: "2-digit", minute: "2-digit" });
const statusLabels: Record<string, string> = { SCHEDULED: "Scheduled", AWAITING_RELEASE: "Waiting for release", RELEASED: "Vehicle with you", RETURNED: "Returned", CLOSED: "Completed" };
const stepLabels: Record<string, string> = {
  NOT_SUBMITTED: "Not done yet", AWAITING_APPROVAL: "Waiting for office approval", RELEASED: "Released to you", APPROVED: "Approved",
  BLOCKED: "Blocked: safety defect", RESUBMIT: "Please redo this inspection", SUBMITTED: "Submitted"
};

/** Mobile-first portal for drivers. The API returns only the signed-in driver's own assignments. */
export function DriverPortalPage() {
  useDocumentTitle("Driver portal | SL Tours");
  const session = useSession(); const client = useQueryClient(); const navigate = useNavigate();
  const [pending, setPending] = useState(false);
  async function logout() { setPending(true); try { await signOut(); client.clear(); navigate("/login", { replace: true }); } finally { setPending(false); } }
  if (session.isLoading) return <LoadingState label="Checking your session" />;
  if (session.isError) return <ErrorState message={session.error.message} />;
  if (!session.data?.authenticated) return <Navigate to="/login?next=%2Fdriver" replace />;
  if (session.data.role !== "DRIVER") return <Navigate to={session.data.role === "CUSTOMER" ? "/account" : session.data.role === "PARTNER" ? "/partner" : "/operations"} replace />;
  return <div className="driver-shell">
    <header className="driver-header"><Link to="/driver" className="driver-brand"><img src="/assets/logo.png" alt="" width="40" height="40" /><span>SL Tours<strong>Driver portal</strong></span></Link>
      <button className="icon-button" onClick={logout} disabled={pending} aria-label="Sign out"><LogOut aria-hidden="true" /></button></header>
    <main className="driver-main">
      <Routes>
        <Route index element={<AssignmentList name={session.data.name ?? ""} />} />
        <Route path="assignments/:id" element={<AssignmentView />} />
        <Route path="*" element={<Navigate to="/driver" replace />} />
      </Routes>
    </main>
  </div>;
}

function AssignmentList({ name }: { name: string }) {
  const profile = useQuery({ queryKey: ["driver", "me"], queryFn: getDriverProfile, retry: false });
  const assignments = useQuery({ queryKey: ["driver", "assignments"], queryFn: listDriverAssignments, retry: false, refetchInterval: 120_000 });
  return <>
    <h1>Hello, {name.split(" ")[0]}</h1>
    {profile.data && profile.data.eligibilityProblemsToday.length > 0 && <div className="driver-warning-box" role="alert"><TriangleAlert aria-hidden="true" />
      <div><strong>You cannot collect vehicles yet</strong><ul>{profile.data.eligibilityProblemsToday.map(p => <li key={p}>{p}</li>)}</ul><p>Contact the office to update your documents.</p></div></div>}
    <h2 className="driver-subheading">Your assignments</h2>
    {assignments.isLoading && <LoadingState label="Loading your assignments" />}{assignments.isError && <ErrorState message={assignments.error.message} />}
    {assignments.data && !assignments.data.length && <p className="driver-empty">No upcoming assignments.</p>}
    <ul className="driver-cards">{assignments.data?.map(a => <li key={a.id}><Link to={`/driver/assignments/${a.id}`} className="driver-card">
      <div className="driver-card-top"><span className={`driver-status status-${a.status.toLowerCase()}`}>{statusLabels[a.status] ?? a.status}</span><span>{a.bookingReference}</span></div>
      <strong>{a.title}</strong>
      <span><CalendarClock aria-hidden="true" />{when(a.startsAt)} – {time(a.endsAt)}</span>
      {a.pickupLocation && <span><MapPin aria-hidden="true" />{a.pickupLocation}</span>}
      <span><Car aria-hidden="true" />{a.vehicleName} · {a.registration}</span>
      <span className="driver-next">{a.nextAction}<ChevronRight aria-hidden="true" /></span>
    </Link></li>)}</ul>
  </>;
}

function AssignmentView() {
  const { id = "" } = useParams();
  const client = useQueryClient();
  const detail = useQuery({ queryKey: ["driver", "assignment", id], queryFn: () => getDriverAssignment(id), retry: false });
  const [mode, setMode] = useState<"view" | "collection" | "return">("view");
  const [result, setResult] = useState<SubmissionResult | null>(null);
  if (detail.isLoading) return <LoadingState label="Loading assignment" />;
  if (detail.isError) return <><ErrorState message={detail.error.message} /><Link to="/driver">Back to assignments</Link></>;
  const d = detail.data!;
  async function done(next: SubmissionResult) {
    setResult(next); setMode("view");
    await client.invalidateQueries({ queryKey: ["driver"] });
  }
  if (result) return <SubmissionOutcome result={result} onBack={() => setResult(null)} />;
  if (mode !== "view") return <InspectionForm assignmentId={d.id} kind={mode} minimumPhotos={d.minimumVehiclePhotos}
    collectionOdometer={mode === "return" ? d.collection.odometerKm : null} onDone={done} onCancel={() => setMode("view")} />;
  return <article className="driver-detail">
    <Link to="/driver" className="driver-back">← All assignments</Link>
    <p className="eyebrow">{d.trip.bookingReference}</p>
    <h1>{d.trip.title}</h1>
    <p className={`driver-next-box${d.blockers.length ? " is-blocked" : ""}`}>{d.nextAction}</p>
    {d.blockers.length > 0 && <ul className="driver-blockers">{d.blockers.map(b => <li key={b}>{b}</li>)}</ul>}
    <div className="driver-actions">
      {d.canCollect && <button className="button button-primary" onClick={() => setMode("collection")}>Start collection inspection</button>}
      {d.canReturn && <button className="button button-primary" onClick={() => setMode("return")}>Start return inspection</button>}
    </div>
    <section className="driver-section"><h2>Trip</h2><dl className="driver-facts">
      <div><dt><CalendarClock aria-hidden="true" />When</dt><dd>{when(d.trip.startsAt)} – {time(d.trip.endsAt)}</dd></div>
      <div><dt><MapPin aria-hidden="true" />Pickup</dt><dd>{d.trip.pickupLocation ?? "—"}</dd></div>
      <div><dt><MapPin aria-hidden="true" />Drop-off</dt><dd>{d.trip.dropOff ?? "—"}</dd></div>
      <div><dt><Users aria-hidden="true" />Passengers</dt><dd>{d.trip.passengers}</dd></div>
      <div><dt>Customer</dt><dd>{d.trip.customerName}{d.trip.customerPhone && <a className="driver-call" href={`tel:${d.trip.customerPhone.replace(/[^+\d]/g, "")}`}><Phone aria-hidden="true" />{d.trip.customerPhone}</a>}</dd></div>
      {d.trip.specialRequests && <div><dt>Special requests</dt><dd>{d.trip.specialRequests}</dd></div>}
    </dl>
    {d.trip.itinerary.length > 0 && <><h3>Itinerary</h3><ol className="driver-itinerary">{d.trip.itinerary.map((step, i) => <li key={i}>{step}</li>)}</ol></>}
    </section>
    <section className="driver-section"><h2>Vehicle</h2>
      {d.vehicle.imageUrl && <img className="driver-vehicle-image" src={d.vehicle.imageUrl} alt={d.vehicle.displayName} />}
      <dl className="driver-facts"><div><dt>Vehicle</dt><dd>{d.vehicle.displayName}</dd></div><div><dt>Registration</dt><dd className="driver-plate">{d.vehicle.registration}</dd></div>
        <div><dt>Seats</dt><dd>{d.vehicle.capacity}{d.vehicle.luggageCapacity != null ? ` · ${d.vehicle.luggageCapacity} large bags` : ""}</dd></div>
        {d.vehicle.operatingNotes && <div><dt>Notes</dt><dd>{d.vehicle.operatingNotes}</dd></div>}</dl>
    </section>
    <section className="driver-section"><h2>Inspections</h2>
      <Step title="Collection" step={d.collection} /><Step title="Return" step={d.returned} />
    </section>
  </article>;
}

function Step({ title, step }: { title: string; step: DriverStep }) {
  return <div className="driver-step"><strong>{title}</strong><span className={`driver-step-state state-${step.state.toLowerCase()}`}>{stepLabels[step.state] ?? step.state}</span>
    {step.submittedAt && <small>{when(step.submittedAt)}{step.odometerKm != null ? ` · ${step.odometerKm.toLocaleString("en-ZA")} km` : ""}</small>}
    {step.photoUrls.length > 0 && <div className="driver-step-photos">{step.photoUrls.slice(0, 6).map(url => <img key={url} src={url} alt="" loading="lazy" />)}</div>}
  </div>;
}
