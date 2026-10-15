import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { request } from "../../lib/api";
import { formatAmount } from "../../lib/format";
import { newIdempotencyKey } from "../../lib/uuid";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { areaLabels, categoryLabels } from "../explore/exploreLabels";
import { dateTime, randToCents } from "./labels";
import { businessTypeLabels, commissionKindLabels, proposalStatusLabels, routeLabels, routeStatusLabels, type BusinessType, type CommissionLine,
  type PayoutView, type ProposalView, type Route, type RouteStatus, type RouteView, type SupplierPaymentView } from "../partners/partnerTypes";

interface Summary { id: string; reference: string; businessName: string; businessType: BusinessType; area: string | null; routes: RouteView[];
  hasReferralAgreement: boolean; payableCents: number; createdAt: string }
interface Agreement { id: string; route: Route; agreementReference: string; effectiveFrom: string; effectiveTo: string | null; commissionType: string | null;
  rateBasisPoints: number | null; fixedAmountCents: number | null; eligibleBasis: string | null; eligibleBookingTypes: string | null; attributionWindowDays: number | null;
  refundTreatment: string | null; minimumEligibleCents: number | null; maximumCommissionCents: number | null; supplierTerms: string | null; notes: string | null;
  recordedBy: string; recordedAt: string; endedBy: string | null; endedAt: string | null }
interface Detail { id: string; version: number; reference: string; businessName: string; businessType: BusinessType; contactName: string; email: string; phone: string | null;
  website: string | null; area: string | null; registrationNumber: string | null; licences: string | null; description: string; servicesOffered: string | null;
  referralCode: string | null; loginEnabled: boolean; createdAt: string; routes: RouteView[]; agreements: Agreement[];
  referrals: Array<{ kind: string; reference: string; customerName: string; status: string; source: string; capturedAt: string | null; createdAt: string; totalAmountCents: number; netPaidCents: number }>;
  commissions: CommissionLine[]; payouts: PayoutView[]; unpaidCents: number; supplierPayments: SupplierPaymentView[]; proposals: ProposalView[] }

const api = {
  list: (status: string) => request<Summary[]>(`/api/v1/admin/partners${status ? `?status=${status}` : ""}`),
  get: (id: string) => request<Detail>(`/api/v1/admin/partners/${id}`),
  post: (path: string, body: unknown) => request<Detail>(`/api/v1/admin/partners/${path}`, { method: "POST", body: JSON.stringify(body) }),
  proposals: () => request<ProposalView[]>("/api/v1/admin/partners/proposals?status=SUBMITTED"),
  decideProposal: (p: ProposalView, decision: string, reason: string) => request<ProposalView>(`/api/v1/admin/partners/proposals/${p.id}/decision`,
    { method: "POST", body: JSON.stringify({ version: p.version, decision, reason: reason || null }) })
};
const statusClass = (s: RouteStatus) => s === "APPROVED" ? "status-active" : s === "PENDING" ? "status-draft" : "status-blocked";
/** "12.5" -> 1250 basis points, without floating-point rounding. */
function percentToBasisPoints(value: string): number {
  const v = value.trim().replace(",", ".");
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(v)) throw new Error("Enter the rate as a percentage with up to two decimals, for example 10 or 12.5");
  const [whole, frac = ""] = v.split(".");
  const bp = Number(whole) * 100 + Number(frac.padEnd(2, "0"));
  if (bp < 1 || bp > 10000) throw new Error("The rate must be between 0.01% and 100%");
  return bp;
}

export function PartnersPanel() {
  const [status, setStatus] = useState("");
  const [selected, setSelected] = useState<string | null>(null);
  const list = useQuery({ queryKey: ["admin", "partners", status], queryFn: () => api.list(status), retry: false });
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">Partnerships</p><h1>Partners</h1>
      <p>Referral partners and experience suppliers. Commission is earned only under an agreement you record; nothing is defaulted.</p></div>
      <label>Route status<select value={status} onChange={e => setStatus(e.target.value)}><option value="">All</option>
        {(Object.keys(routeStatusLabels) as RouteStatus[]).map(s => <option key={s} value={s}>{routeStatusLabels[s]}</option>)}</select></label></div>
    <ProposalQueue />
    {list.isLoading && <LoadingState label="Loading partners" />}{list.isError && <ErrorState message={list.error.message} />}
    {list.data && <div className="admin-table-wrap"><table><thead><tr><th>Business</th><th>Routes</th><th>Referral agreement</th><th>Not yet paid out</th><th>Applied</th><th></th></tr></thead>
      <tbody>{list.data.map(p => <tr key={p.id}>
        <td>{p.businessName}<small>{businessTypeLabels[p.businessType]}{p.area ? ` · ${p.area}` : ""} · {p.reference}</small></td>
        <td>{p.routes.map(r => <span key={r.route} className={`admin-badge ${statusClass(r.status)}`}>{routeLabels[r.route]}: {routeStatusLabels[r.status]}</span>)}</td>
        <td>{p.hasReferralAgreement ? "In force" : "None"}</td><td>{formatAmount(p.payableCents)}</td><td>{dateTime(p.createdAt)}</td>
        <td><button className="button button-secondary" onClick={() => setSelected(p.id)}>Open</button></td></tr>)}</tbody></table>
      {list.data.length === 0 && <p className="admin-empty">No partners match this filter.</p>}</div>}
    {selected && <PartnerDetailView key={selected} id={selected} onClose={() => setSelected(null)} />}
  </section>;
}

function ProposalQueue() {
  const client = useQueryClient();
  const queue = useQuery({ queryKey: ["admin", "proposals"], queryFn: api.proposals, retry: false });
  if (!queue.data || queue.data.length === 0) return null;
  return <div className="admin-callout"><strong>Experience proposals awaiting review</strong>
    {queue.data.map(p => <ProposalReview key={p.id} proposal={p} onDone={() => client.invalidateQueries({ queryKey: ["admin"] })} />)}</div>;
}

function ProposalReview({ proposal: p, onDone }: { proposal: ProposalView; onDone: () => void }) {
  const [reason, setReason] = useState("");
  const decide = useMutation({ mutationFn: (d: string) => api.decideProposal(p, d, reason), onSuccess: onDone });
  return <details className="admin-inline-details"><summary>{p.title} · {p.partnerName} · {proposalStatusLabels[p.status]}</summary>
    <dl className="admin-detail-grid">{[["Category", categoryLabels[p.category]], ["Area", areaLabels[p.area]], ["Duration", `${p.durationMinutes} minutes`],
      ["Meeting point", p.address], ["Website", p.website], ["Summary", p.summary], ["Description", p.description], ["Supplier's prices (unverified)", p.proposedPrices],
      ["Supplier's schedule (unverified)", p.proposedSchedule], ["Participation limits (unverified)", p.participationInfo], ["Notes", p.supplierNotes]]
      .map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v || "—"}</dd></div>)}</dl>
    {p.status === "SUBMITTED" && <>
      <p className="form-note">Approving is a commercial decision only. It creates a draft Explore entry; prices, hours and participation limits must still be
        checked against an official source in Explore activities before publishing.</p>
      <label className="full-field">Reason for the supplier <em className="optional">(needed to request changes or decline)</em>
        <input maxLength={600} value={reason} onChange={e => setReason(e.target.value)} /></label>
      {decide.isError && <ErrorState message={decide.error.message} />}
      <div className="admin-actions"><button className="button button-primary" disabled={decide.isPending} onClick={() => decide.mutate("APPROVE")}>Approve and create draft</button>
        <button className="button button-secondary" disabled={decide.isPending} onClick={() => decide.mutate("REQUEST_CHANGES")}>Request changes</button>
        <button className="button button-quiet" disabled={decide.isPending} onClick={() => decide.mutate("REJECT")}>Decline</button></div></>}
    {p.activitySlug && <p>Draft activity: /explore/{p.activitySlug} ({p.activityStatus?.toLowerCase()})</p>}
  </details>;
}

function PartnerDetailView({ id, onClose }: { id: string; onClose: () => void }) {
  const client = useQueryClient();
  const detail = useQuery({ queryKey: ["admin", "partner", id], queryFn: () => api.get(id), retry: false });
  const done = async (d: Detail) => { client.setQueryData(["admin", "partner", id], d); await client.invalidateQueries({ queryKey: ["admin", "partners"] }); };
  if (detail.isLoading) return <LoadingState label="Loading partner" />;
  if (detail.isError) return <ErrorState message={detail.error.message} />;
  const d = detail.data!;
  return <section className="form-surface admin-detail" aria-label="Partner details">
    <div className="admin-heading"><div><p className="eyebrow">{d.reference}</p><h2>{d.businessName}</h2>
      <p>{businessTypeLabels[d.businessType]} · applied {dateTime(d.createdAt)} · sign-in {d.loginEnabled ? "enabled" : "disabled"}{d.referralCode ? ` · referral code ${d.referralCode}` : ""}</p></div>
      <button className="button button-secondary" onClick={onClose}>Close</button></div>
    <dl className="admin-detail-grid">{[["Contact", d.contactName], ["Email", d.email], ["Phone", d.phone], ["Website", d.website], ["Area", d.area],
      ["Registration", d.registrationNumber], ["Licences", d.licences], ["About", d.description], ["Services offered", d.servicesOffered]]
      .map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v || "—"}</dd></div>)}</dl>

    <h3>Routes</h3>
    {d.routes.map(r => <RouteDecision key={r.route} partnerId={d.id} route={r} onDone={done} />)}

    <h3>Agreements</h3>
    {d.agreements.length === 0 && <p>No agreement recorded. Referrals are credited, but no commission is earned until one is in force.</p>}
    <ul className="admin-upcoming-list">{d.agreements.map(a => <AgreementRow key={a.id} partnerId={d.id} agreement={a} onDone={done} />)}</ul>
    <AgreementForm partnerId={d.id} routes={d.routes.map(r => r.route)} onDone={done} />

    {d.referrals.length > 0 && <><h3>Referrals</h3><div className="admin-table-wrap"><table><thead><tr><th>Reference</th><th>Customer</th><th>Status</th><th>Source</th><th>Paid</th></tr></thead>
      <tbody>{d.referrals.map(r => <tr key={r.reference}><td>{r.reference}<small>{r.kind.toLowerCase()} · {dateTime(r.createdAt)}</small></td><td>{r.customerName}</td>
        <td>{r.status.replace(/_/g, " ")}</td><td>{r.source.toLowerCase()}</td><td>{r.kind === "BOOKING" ? `${formatAmount(r.netPaidCents)} of ${formatAmount(r.totalAmountCents)}` : "—"}</td></tr>)}</tbody></table></div></>}

    <h3>Commission ledger</h3>
    <p>Not yet paid out: <strong>{formatAmount(d.unpaidCents)}</strong>{d.unpaidCents < 0 ? " (owed back by the partner)" : ""}</p>
    <PayoutForm partnerId={d.id} lines={d.commissions} onDone={done} />
    {d.payouts.length > 0 && <><h4>Payouts</h4><ul className="admin-upcoming-list">{d.payouts.map(p => <li key={p.id}><span><strong>{formatAmount(p.amountCents)}</strong> paid {p.paidOn}
      · {p.method}, ref {p.paymentReference}</span><small>Recorded by {p.recordedBy}, {dateTime(p.recordedAt)}</small></li>)}</ul></>}

    {d.routes.some(r => r.route === "SUPPLIER") && <>
      <h3>Supplier payments</h3>
      <p className="form-note">Payments to this business for experiences it supplied. Kept separate from referral commission.</p>
      <ul className="admin-upcoming-list">{d.supplierPayments.map(p => <li key={p.id}><span><strong>{formatAmount(p.amountCents)}</strong> paid {p.paidOn} · {p.method}, ref {p.paymentReference}</span>
        <small>{p.description}{p.bookingReference ? ` · booking ${p.bookingReference}` : ""}</small></li>)}</ul>
      <SupplierPaymentForm partnerId={d.id} onDone={done} />
      {d.proposals.length > 0 && <><h4>Proposals</h4>{d.proposals.map(p => <ProposalReview key={p.id} proposal={p} onDone={() => client.invalidateQueries({ queryKey: ["admin"] })} />)}</>}
    </>}
  </section>;
}

function RouteDecision({ partnerId, route, onDone }: { partnerId: string; route: RouteView; onDone: (d: Detail) => void }) {
  const [reason, setReason] = useState("");
  const decide = useMutation({ mutationFn: (decision: string) => api.post(`${partnerId}/decisions`, { route: route.route, decision, reason: reason || null }),
    onSuccess: d => { setReason(""); onDone(d); } });
  const actions: Array<[string, string]> = route.status === "PENDING" ? [["APPROVE", "Approve"], ["REJECT", "Decline"]]
    : route.status === "APPROVED" ? [["SUSPEND", "Suspend"]] : route.status === "SUSPENDED" ? [["REINSTATE", "Reinstate"]] : [["APPROVE", "Approve after all"]];
  return <div className="admin-route-row">
    <p><strong>{routeLabels[route.route]}</strong> <span className={`admin-badge ${statusClass(route.status)}`}>{routeStatusLabels[route.status]}</span>
      {route.decidedBy && <small>{route.decidedBy}, {dateTime(route.decidedAt)}{route.reason ? `: ${route.reason}` : ""}</small>}</p>
    <div className="admin-inline-form">
      <label>Reason <em className="optional">(needed to decline or suspend)</em><input maxLength={600} value={reason} onChange={e => setReason(e.target.value)} /></label>
      {actions.map(([value, label]) => <button key={value} type="button" className={value === "APPROVE" || value === "REINSTATE" ? "button button-primary" : "button button-secondary"}
        disabled={decide.isPending} onClick={() => decide.mutate(value)}>{label}</button>)}
    </div>
    {decide.isError && <ErrorState message={decide.error.message} />}
  </div>;
}

function terms(a: Agreement) {
  if (a.route === "SUPPLIER") return a.supplierTerms;
  const value = a.commissionType === "PERCENTAGE" ? `${(a.rateBasisPoints ?? 0) / 100}% of ${a.eligibleBasis === "NET_AMOUNT_PAID" ? "net amount paid" : "booking total"}`
    : `${formatAmount(a.fixedAmountCents ?? 0)} per booking`;
  return `${value}; ${a.eligibleBookingTypes?.toLowerCase().replace("_", " ")}; ${a.attributionWindowDays}-day window; refunds ${a.refundTreatment === "PROPORTIONAL" ? "proportional" : "reverse all"}`
    + (a.minimumEligibleCents != null ? `; minimum ${formatAmount(a.minimumEligibleCents)}` : "") + (a.maximumCommissionCents != null ? `; cap ${formatAmount(a.maximumCommissionCents)}` : "");
}

function AgreementRow({ partnerId, agreement: a, onDone }: { partnerId: string; agreement: Agreement; onDone: (d: Detail) => void }) {
  const [to, setTo] = useState("");
  const end = useMutation({ mutationFn: () => api.post(`${partnerId}/agreements/${a.id}/end`, { effectiveTo: to }), onSuccess: onDone });
  return <li><span><strong>{routeLabels[a.route]} · {a.agreementReference}</strong> from {a.effectiveFrom}{a.effectiveTo ? ` to ${a.effectiveTo}` : " (open-ended)"}</span>
    <small>{terms(a)}{a.notes ? ` · ${a.notes}` : ""} · recorded by {a.recordedBy}{a.endedBy ? ` · ended by ${a.endedBy}` : ""}</small>
    <form className="admin-inline-form" onSubmit={e => { e.preventDefault(); end.mutate(); }}>
      <label>End on<input type="date" required value={to} onChange={e => setTo(e.target.value)} /></label>
      <button className="button button-quiet" type="submit" disabled={end.isPending || !to}>End agreement</button></form>
    {end.isError && <ErrorState message={end.error.message} />}</li>;
}

function AgreementForm({ partnerId, routes, onDone }: { partnerId: string; routes: Route[]; onDone: (d: Detail) => void }) {
  const [f, setF] = useState({ route: routes[0], reference: "", from: "", to: "", type: "PERCENTAGE", rate: "", fixed: "", basis: "NET_AMOUNT_PAID", tour: true, privateTrip: false,
    window: "", refunds: "PROPORTIONAL", minimum: "", maximum: "", supplierTerms: "", notes: "" });
  const [error, setError] = useState("");
  const save = useMutation({ mutationFn: (body: unknown) => api.post(`${partnerId}/agreements`, body), onSuccess: d => { onDone(d); setError(""); } });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF(v => ({ ...v, [k]: e.target.value }));
  function submit(e: FormEvent) {
    e.preventDefault(); setError("");
    try {
      const referral = f.route === "REFERRAL";
      save.mutate({ route: f.route, agreementReference: f.reference, effectiveFrom: f.from, effectiveTo: f.to || null, notes: f.notes || null,
        commissionType: referral ? f.type : null, rateBasisPoints: referral && f.type === "PERCENTAGE" ? percentToBasisPoints(f.rate) : null,
        fixedAmountCents: referral && f.type === "FIXED_PER_BOOKING" ? randToCents(f.fixed) : null, eligibleBasis: referral ? f.basis : null,
        eligibleBookingTypes: referral ? [...(f.tour ? ["TOUR"] : []), ...(f.privateTrip ? ["PRIVATE_TRIP"] : [])] : null,
        attributionWindowDays: referral ? Number(f.window) : null, refundTreatment: referral ? f.refunds : null,
        minimumEligibleCents: referral && f.minimum ? randToCents(f.minimum) : null, maximumCommissionCents: referral && f.maximum ? randToCents(f.maximum) : null,
        supplierTerms: referral ? null : f.supplierTerms });
    } catch (err) { setError((err as Error).message); }
  }
  return <details className="admin-inline-details"><summary>Record an agreement</summary>
    <form className="form-grid admin-form-grid" onSubmit={submit} aria-label="New agreement">
      <p className="form-note full-field">Enter the terms exactly as agreed in writing. They cannot be edited later, only ended, so every commission keeps the terms it was calculated under.</p>
      <label>Route<select value={f.route} onChange={set("route")}>{routes.map(r => <option key={r} value={r}>{routeLabels[r]}</option>)}</select></label>
      <label>Agreement reference<input required maxLength={80} value={f.reference} onChange={set("reference")} /></label>
      <label>Effective from<input type="date" required value={f.from} onChange={set("from")} /></label>
      <label>Effective to <em className="optional">(optional)</em><input type="date" value={f.to} onChange={set("to")} /></label>
      {f.route === "REFERRAL" ? <>
        <label>Commission<select value={f.type} onChange={set("type")}><option value="PERCENTAGE">Percentage</option><option value="FIXED_PER_BOOKING">Fixed amount per booking</option></select></label>
        {f.type === "PERCENTAGE" ? <label>Rate (%)<input required inputMode="decimal" value={f.rate} onChange={set("rate")} /></label>
          : <label>Amount per booking (R)<input required inputMode="decimal" value={f.fixed} onChange={set("fixed")} /></label>}
        <label>Eligible amount<select value={f.basis} onChange={set("basis")}><option value="NET_AMOUNT_PAID">Net amount paid (after refunds)</option><option value="BOOKING_TOTAL">Booking total</option></select></label>
        <label>Attribution window (days)<input type="number" min={1} max={3650} required value={f.window} onChange={set("window")} /></label>
        <fieldset className="full-field"><legend>Booking types</legend>
          <label className="admin-check"><input type="checkbox" checked={f.tour} onChange={e => setF(v => ({ ...v, tour: e.target.checked }))} />Tours</label>
          <label className="admin-check"><input type="checkbox" checked={f.privateTrip} onChange={e => setF(v => ({ ...v, privateTrip: e.target.checked }))} />Private trips</label></fieldset>
        <label>Refunds<select value={f.refunds} onChange={set("refunds")}><option value="PROPORTIONAL">Reduce commission proportionally</option><option value="FULL_REVERSAL">Any refund reverses the commission</option></select></label>
        <label>Minimum eligible amount (R) <em className="optional">(optional)</em><input inputMode="decimal" value={f.minimum} onChange={set("minimum")} /></label>
        <label>Maximum per booking (R) <em className="optional">(optional)</em><input inputMode="decimal" value={f.maximum} onChange={set("maximum")} /></label>
      </> : <label className="full-field">Supplier terms as agreed<textarea required maxLength={2000} rows={3} value={f.supplierTerms} onChange={set("supplierTerms")} /></label>}
      <label className="full-field">Notes <em className="optional">(optional)</em><input maxLength={2000} value={f.notes} onChange={set("notes")} /></label>
      {(error || save.isError) && <ErrorState message={error || save.error!.message} />}
      <button className="button button-primary" type="submit" disabled={save.isPending}>Record agreement</button>
    </form></details>;
}

function PayoutForm({ partnerId, lines, onDone }: { partnerId: string; lines: CommissionLine[]; onDone: (d: Detail) => void }) {
  const unpaid = lines.filter(l => !l.payoutId);
  const [chosen, setChosen] = useState<string[]>(unpaid.map(l => l.id));
  const [f, setF] = useState({ paidOn: new Date().toISOString().slice(0, 10), method: "EFT", reference: "", notes: "" });
  const [key, setKey] = useState(newIdempotencyKey);
  const total = unpaid.filter(l => chosen.includes(l.id)).reduce((s, l) => s + l.amountCents, 0);
  const save = useMutation({ mutationFn: () => api.post(`${partnerId}/payouts`, { idempotencyKey: key, commissionIds: chosen, amountCents: total, paidOn: f.paidOn,
    method: f.method, paymentReference: f.reference, notes: f.notes || null }), onSuccess: d => { onDone(d); setKey(newIdempotencyKey()); setChosen([]); } });
  return <>
    {lines.length > 0 && <div className="admin-table-wrap"><table><thead><tr><th></th><th>Booking</th><th>Entry</th><th>Amount</th><th>Paid out</th></tr></thead>
      <tbody>{lines.map(l => <tr key={l.id}><td>{!l.payoutId && <input type="checkbox" aria-label={`Include ${l.bookingReference} ${commissionKindLabels[l.kind]}`}
        checked={chosen.includes(l.id)} onChange={e => setChosen(c => e.target.checked ? [...c, l.id] : c.filter(x => x !== l.id))} />}</td>
        <td>{l.bookingReference}<small>{dateTime(l.createdAt)}</small></td><td>{commissionKindLabels[l.kind]}<small>{l.calculation}</small></td>
        <td>{formatAmount(l.amountCents)}</td><td>{l.payoutReference ?? "—"}</td></tr>)}</tbody></table></div>}
    {unpaid.length > 0 && <form className="admin-inline-form" aria-label="Record a payout" onSubmit={e => { e.preventDefault(); save.mutate(); }}>
      <p className="full-field">Selected: <strong>{formatAmount(total)}</strong>. Record a payout only after the payment has been made.</p>
      <label>Paid on<input type="date" required value={f.paidOn} onChange={e => setF({ ...f, paidOn: e.target.value })} /></label>
      <label>Method<input required maxLength={60} value={f.method} onChange={e => setF({ ...f, method: e.target.value })} /></label>
      <label>Payment reference<input required maxLength={120} value={f.reference} onChange={e => setF({ ...f, reference: e.target.value })} /></label>
      {save.isError && <ErrorState message={save.error.message} />}
      <button className="button button-primary" type="submit" disabled={save.isPending || total <= 0}>Record payout of {formatAmount(total)}</button>
    </form>}
  </>;
}

function SupplierPaymentForm({ partnerId, onDone }: { partnerId: string; onDone: (d: Detail) => void }) {
  const [f, setF] = useState({ amount: "", paidOn: new Date().toISOString().slice(0, 10), method: "EFT", reference: "", booking: "", description: "" });
  const [key, setKey] = useState(newIdempotencyKey);
  const [error, setError] = useState("");
  const save = useMutation({ mutationFn: (amountCents: number) => api.post(`${partnerId}/supplier-payments`, { idempotencyKey: key, amountCents, paidOn: f.paidOn,
    method: f.method, paymentReference: f.reference, bookingReference: f.booking || null, description: f.description }),
    onSuccess: d => { onDone(d); setKey(newIdempotencyKey()); setF(v => ({ ...v, amount: "", reference: "", booking: "", description: "" })); } });
  return <details className="admin-inline-details"><summary>Record a supplier payment</summary>
    <form className="admin-inline-form" onSubmit={e => { e.preventDefault(); setError(""); try { const c = randToCents(f.amount); if (!c) throw new Error("Enter an amount"); save.mutate(c); } catch (err) { setError((err as Error).message); } }}>
      <label>Amount (R)<input inputMode="decimal" required value={f.amount} onChange={e => setF({ ...f, amount: e.target.value })} /></label>
      <label>Paid on<input type="date" required value={f.paidOn} onChange={e => setF({ ...f, paidOn: e.target.value })} /></label>
      <label>Method<input required maxLength={60} value={f.method} onChange={e => setF({ ...f, method: e.target.value })} /></label>
      <label>Payment reference<input required maxLength={120} value={f.reference} onChange={e => setF({ ...f, reference: e.target.value })} /></label>
      <label>Booking <em className="optional">(optional)</em><input maxLength={32} value={f.booking} onChange={e => setF({ ...f, booking: e.target.value })} /></label>
      <label className="full-field">What it was for<input required maxLength={600} value={f.description} onChange={e => setF({ ...f, description: e.target.value })} /></label>
      {(error || save.isError) && <ErrorState message={error || save.error!.message} />}
      <button className="button button-secondary" type="submit" disabled={save.isPending}>Record supplier payment</button>
    </form></details>;
}
