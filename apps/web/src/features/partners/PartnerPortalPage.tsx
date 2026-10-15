import { capeTown } from "../../lib/dates";
import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, Navigate, NavLink, Route, Routes, useNavigate } from "react-router-dom";
import { Copy, LogOut } from "lucide-react";
import { useSession } from "../auth/useSession";
import { signOut } from "../auth/authApi";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { formatAmount } from "../../lib/format";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { areaLabels, categoryLabels } from "../explore/exploreLabels";
import { portal } from "./partnerApi";
import { commissionKindLabels, proposalStatusLabels, routeLabels, routeStatusLabels, type ProposalInput, type ProposalView } from "./partnerTypes";
import type { ActivityArea, ActivityCategory } from "../../types/api";

const day = (iso: string) => iso.length === 10 ? new Date(`${iso}T00:00:00`).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
  : capeTown(iso, { day: "numeric", month: "short", year: "numeric" });

/** Partner area. The API returns only this partner's own records: references and status, never guests' personal details. */
export function PartnerPortalPage() {
  useDocumentTitle("Partner area | SL Tours");
  const session = useSession(); const client = useQueryClient(); const navigate = useNavigate();
  const [pending, setPending] = useState(false);
  const me = useQuery({ queryKey: ["partner", "me"], queryFn: portal.me, retry: false, enabled: session.data?.role === "PARTNER" });
  async function logout() { setPending(true); try { await signOut(); client.clear(); navigate("/login", { replace: true }); } finally { setPending(false); } }
  if (session.isLoading) return <LoadingState label="Checking your session" />;
  if (session.isError) return <ErrorState message={session.error.message} />;
  if (!session.data?.authenticated) return <Navigate to="/login?next=%2Fpartner" replace />;
  if (session.data.role !== "PARTNER") return <Navigate to={session.data.role === "CUSTOMER" ? "/account" : session.data.role === "DRIVER" ? "/driver" : "/operations"} replace />;
  const supplier = me.data?.routes.some(r => r.route === "SUPPLIER" && r.status === "APPROVED");
  const referral = me.data?.routes.some(r => r.route === "REFERRAL" && r.status === "APPROVED");
  return <div className="driver-shell partner-shell">
    <header className="driver-header"><Link to="/partner" className="driver-brand"><img src="/assets/logo.png" alt="" width="40" height="40" />
      <span>SL Tours<strong>Partner area</strong></span></Link>
      <button className="icon-button" onClick={logout} disabled={pending} aria-label="Sign out"><LogOut aria-hidden="true" /></button></header>
    <main className="driver-main partner-main">
      {me.isLoading && <LoadingState label="Loading your partner details" />}{me.isError && <ErrorState message={me.error.message} />}
      {me.data && <>
        <nav className="partner-tabs" aria-label="Partner area">
          <NavLink to="/partner" end>Overview</NavLink>
          {referral && <NavLink to="/partner/referrals">Referrals</NavLink>}
          <NavLink to="/partner/statement">Statement</NavLink>
          {supplier && <NavLink to="/partner/experiences">Experiences</NavLink>}
        </nav>
        <Routes>
          <Route index element={<Overview />} />
          <Route path="referrals" element={<Referrals />} />
          <Route path="statement" element={<Statement />} />
          <Route path="experiences" element={<Experiences />} />
          <Route path="*" element={<Navigate to="/partner" replace />} />
        </Routes>
      </>}
    </main>
  </div>;
}

function Overview() {
  const me = useQuery({ queryKey: ["partner", "me"], queryFn: portal.me, retry: false });
  const [copied, setCopied] = useState(false);
  if (!me.data) return null;
  const p = me.data;
  return <>
    <h1>{p.businessName}</h1>
    <p className="form-note">Partner reference {p.reference}</p>
    <ul className="partner-status-list">{p.routes.map(r => <li key={r.route}><strong>{routeLabels[r.route]}</strong>
      <span className={`admin-badge status-${r.status.toLowerCase()}`}>{routeStatusLabels[r.status]}</span>{r.reason && <small>{r.reason}</small>}</li>)}</ul>
    {p.referralLink && <section className="form-surface partner-link">
      <h2>Your referral link</h2>
      <p>Share this link, or ask guests to enter code <strong>{p.referralCode}</strong> when they book or enquire.</p>
      <div className="partner-copy"><input readOnly value={p.referralLink} aria-label="Referral link" onFocus={e => e.target.select()} />
        <button type="button" className="button button-secondary" onClick={() => { navigator.clipboard?.writeText(p.referralLink!).then(() => setCopied(true)).catch(() => undefined); }}>
          <Copy aria-hidden="true" />{copied ? "Copied" : "Copy"}</button></div>
      <p className="form-note">Guests are told that you referred them and may receive a commission, and that their price is the same.</p>
    </section>}
    <section className="form-surface">
      <h2>Your agreements</h2>
      {p.agreements.length === 0 ? <p>No agreement has been recorded yet. Referrals are still credited to you, but commission can only be earned once
        SL Tours records a written agreement with you.</p>
        : <ul className="partner-agreements">{p.agreements.map(a => <li key={a.agreementReference}><strong>{routeLabels[a.route]} · {a.agreementReference}</strong>
          <span>From {day(a.effectiveFrom)}{a.effectiveTo ? ` to ${day(a.effectiveTo)}` : ""}</span><p>{a.terms}</p></li>)}</ul>}
    </section>
  </>;
}

function Referrals() {
  const list = useQuery({ queryKey: ["partner", "referrals"], queryFn: portal.referrals, retry: false });
  return <>
    <h1>Referrals</h1>
    <p className="form-note">Bookings and enquiries credited to you. Guests' names and contact details are not shown.</p>
    {list.isLoading && <LoadingState label="Loading referrals" />}{list.isError && <ErrorState message={list.error.message} />}
    {list.data && list.data.length === 0 && <p className="driver-empty">No referrals yet.</p>}
    {list.data && list.data.length > 0 && <div className="admin-table-wrap"><table className="partner-table"><thead><tr><th>Reference</th><th>Type</th><th>Received</th>
      <th>Status</th><th>Commission</th></tr></thead><tbody>{list.data.map(r => <tr key={r.reference}>
        <td data-label="Reference">{r.reference}</td><td data-label="Type">{r.product}</td><td data-label="Received">{day(r.receivedOn)}</td>
        <td data-label="Status">{r.status}</td><td data-label="Commission">{r.commission}</td></tr>)}</tbody></table></div>}
  </>;
}

function Statement() {
  const s = useQuery({ queryKey: ["partner", "statement"], queryFn: portal.statement, retry: false });
  if (s.isLoading) return <LoadingState label="Loading your statement" />;
  if (s.isError) return <ErrorState message={s.error.message} />;
  const data = s.data!;
  return <>
    <h1>Statement</h1>
    <p className="partner-balance">{data.unpaidCents >= 0 ? "Not yet paid out" : "Owed back to SL Tours"}: <strong>{formatAmount(Math.abs(data.unpaidCents))}</strong></p>
    <h2>Referral commission</h2>
    {data.commissions.length === 0 ? <p className="driver-empty">No commission yet.</p> : <div className="admin-table-wrap"><table className="partner-table">
      <thead><tr><th>Date</th><th>Booking</th><th>Entry</th><th>Amount</th><th>Paid out</th></tr></thead>
      <tbody>{data.commissions.map(c => <tr key={c.id}><td data-label="Date">{day(c.createdAt)}</td><td data-label="Booking">{c.bookingReference}</td>
        <td data-label="Entry">{commissionKindLabels[c.kind]}<small>{c.calculation}</small></td><td data-label="Amount">{formatAmount(c.amountCents)}</td>
        <td data-label="Paid out">{c.payoutReference ?? "Not yet"}</td></tr>)}</tbody></table></div>}
    <h2>Payouts</h2>
    {data.payouts.length === 0 ? <p className="driver-empty">No payouts recorded.</p> : <ul className="partner-agreements">{data.payouts.map(p =>
      <li key={p.id}><strong>{formatAmount(p.amountCents)} · {day(p.paidOn)}</strong><span>{p.method}, reference {p.paymentReference}</span></li>)}</ul>}
    {data.supplierPayments.length > 0 && <><h2>Supplier payments</h2><p className="form-note">Payments for experiences you supplied, recorded separately from commission.</p>
      <ul className="partner-agreements">{data.supplierPayments.map(p => <li key={p.id}><strong>{formatAmount(p.amountCents)} · {day(p.paidOn)}</strong>
        <span>{p.description}{p.bookingReference ? ` (${p.bookingReference})` : ""}, reference {p.paymentReference}</span></li>)}</ul></>}
  </>;
}

const blankProposal = (): ProposalInput => ({ title: "", category: "ADVENTURE", area: "CITY_CENTRE", summary: "", description: "", durationMinutes: 120, address: null,
  website: null, proposedPrices: "", proposedSchedule: "", participationInfo: "", supplierNotes: null });

function Experiences() {
  const client = useQueryClient();
  const list = useQuery({ queryKey: ["partner", "proposals"], queryFn: portal.proposals, retry: false });
  const [editing, setEditing] = useState<ProposalView | "new" | null>(null);
  const withdraw = useMutation({ mutationFn: portal.withdraw, onSuccess: () => client.invalidateQueries({ queryKey: ["partner", "proposals"] }) });
  return <>
    <div className="admin-heading"><div><h1>Experiences</h1><p className="form-note">Propose experiences for SL Tours to offer. After commercial approval, SL Tours
      checks prices, schedules and participation rules against an official source before anything is published.</p></div>
      {!editing && <button className="button button-primary" onClick={() => setEditing("new")}>Propose an experience</button>}</div>
    {editing && <ProposalForm key={editing === "new" ? "new" : editing.id} existing={editing === "new" ? null : editing} onDone={() => setEditing(null)} />}
    {list.isLoading && <LoadingState label="Loading proposals" />}{list.isError && <ErrorState message={list.error.message} />}
    <ul className="partner-agreements">{list.data?.map(p => <li key={p.id}><strong>{p.title}</strong>
      <span className="admin-badge">{proposalStatusLabels[p.status]}</span><span>{p.reference} · {day(p.createdAt)}</span>
      {p.decisionReason && <p>SL Tours: {p.decisionReason}</p>}
      {p.status === "APPROVED" && <p>{p.activityStatus === "PUBLISHED" ? <Link to={`/explore/${p.activitySlug}`}>View the published page</Link>
        : "Approved. SL Tours is verifying the details before publishing."}</p>}
      {(p.status === "SUBMITTED" || p.status === "CHANGES_REQUESTED") && <div className="assistant-send-actions">
        <button className="button button-secondary" onClick={() => setEditing(p)}>Edit</button>
        <button className="button button-quiet" disabled={withdraw.isPending} onClick={() => withdraw.mutate(p)}>Withdraw</button></div>}
    </li>)}</ul>
    {list.data && list.data.length === 0 && !editing && <p className="driver-empty">No proposals yet.</p>}
  </>;
}

function ProposalForm({ existing, onDone }: { existing: ProposalView | null; onDone: () => void }) {
  const client = useQueryClient();
  const [p, setP] = useState<ProposalInput>(existing ?? blankProposal());
  const save = useMutation({ mutationFn: () => existing ? portal.update(existing, p) : portal.submit(p),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ["partner", "proposals"] }); onDone(); } });
  const set = (k: keyof ProposalInput) => (e: { target: { value: string } }) => setP(v => ({ ...v, [k]: e.target.value === "" && (k === "address" || k === "website" || k === "supplierNotes") ? null : e.target.value }));
  function submit(e: FormEvent) { e.preventDefault(); save.mutate(); }
  return <form className="form-surface wide-form" onSubmit={submit} aria-label="Experience proposal">
    <div className="form-grid">
      <label className="full-field"><span>Experience name</span><input required maxLength={180} value={p.title} onChange={set("title")} /></label>
      <label><span>Category</span><select value={p.category} onChange={set("category")}>{(Object.keys(categoryLabels) as ActivityCategory[]).map(c =>
        <option key={c} value={c}>{categoryLabels[c]}</option>)}</select></label>
      <label><span>Area</span><select value={p.area} onChange={set("area")}>{(Object.keys(areaLabels) as ActivityArea[]).map(a =>
        <option key={a} value={a}>{areaLabels[a]}</option>)}</select></label>
      <label className="full-field"><span>Short summary</span><input required maxLength={400} value={p.summary} onChange={set("summary")} /></label>
      <label className="full-field"><span>Description</span><textarea required maxLength={4000} rows={4} value={p.description} onChange={set("description")} /></label>
      <label><span>Typical duration (minutes)</span><input type="number" min={15} max={720} required value={p.durationMinutes}
        onChange={e => setP(v => ({ ...v, durationMinutes: Number(e.target.value) }))} /></label>
      <label><span>Meeting point <em className="optional">(optional)</em></span><input maxLength={300} value={p.address ?? ""} onChange={set("address")} /></label>
      <label className="full-field"><span>Official page for this experience <em className="optional">(optional)</em></span>
        <input maxLength={500} placeholder="https://" value={p.website ?? ""} onChange={set("website")} /></label>
      <label className="full-field"><span>Prices you charge</span><textarea required maxLength={2000} rows={2} value={p.proposedPrices} onChange={set("proposedPrices")} /></label>
      <label className="full-field"><span>Days and times</span><textarea required maxLength={2000} rows={2} value={p.proposedSchedule} onChange={set("proposedSchedule")} /></label>
      <label className="full-field"><span>Age, height, fitness or other limits</span><textarea required maxLength={1000} rows={2} value={p.participationInfo} onChange={set("participationInfo")} /></label>
      <label className="full-field"><span>Notes for SL Tours <em className="optional">(optional)</em></span><textarea maxLength={2000} rows={2} value={p.supplierNotes ?? ""} onChange={set("supplierNotes")} /></label>
    </div>
    {save.isError && <ErrorState message={save.error.message} />}
    <div className="assistant-send-actions">
      <button className="button button-primary" type="submit" disabled={save.isPending}>{save.isPending ? "Sending…" : existing ? "Save and resubmit" : "Submit proposal"}</button>
      <button className="button button-quiet" type="button" onClick={onDone}>Cancel</button>
    </div>
  </form>;
}
