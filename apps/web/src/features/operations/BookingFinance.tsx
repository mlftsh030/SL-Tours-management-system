import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { request } from "../../lib/api";
import { formatAmount } from "../../lib/format";
import { newIdempotencyKey } from "../../lib/uuid";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { dateTime, randToCents } from "./labels";
import { commissionKindLabels, type CommissionLine } from "../partners/partnerTypes";

interface PaymentRow { id: string; kind: "PAYMENT" | "REFUND"; amountCents: number; method: string; externalReference: string; receivedOn: string; recordedBy: string; recordedAt: string }
interface Finance {
  reference: string; status: string; totalAmountCents: number; netPaidCents: number; payments: PaymentRow[];
  attribution: { partnerId: string; partnerReference: string; partnerName: string; code: string | null; source: string | null; capturedAt: string | null; commissionStatus: string | null } | null;
  commissions: CommissionLine[]; attributionHistory: Array<{ fromPartner: string | null; toPartner: string | null; reason: string; changedBy: string; changedAt: string }>;
}
interface PartnerOption { id: string; reference: string; businessName: string; routes: Array<{ route: string; status: string }> }
const sourceLabels: Record<string, string> = { LINK: "referral link", CODE: "code entered by the guest", ENQUIRY: "from the enquiry", ADMIN: "set by an administrator" };

/** Payments, refunds, referral and commission for one booking. Recording money and changing referrals is for administrators. */
export function BookingFinance({ reference, isAdmin }: { reference: string; isAdmin: boolean }) {
  const client = useQueryClient();
  const key = ["admin", "finance", reference];
  const finance = useQuery({ queryKey: key, queryFn: () => request<Finance>(`/api/v1/admin/bookings/${reference}/finance`), retry: false });
  const refresh = async (data: Finance) => { client.setQueryData(key, data); await client.invalidateQueries({ queryKey: ["admin", "bookings"] }); };
  const evaluate = useMutation({ mutationFn: () => request<Finance>(`/api/v1/admin/bookings/${reference}/commission/evaluate`, { method: "POST" }), onSuccess: refresh });
  if (finance.isLoading) return <LoadingState label="Loading payments" />;
  if (finance.isError) return <ErrorState message={finance.error.message} />;
  const f = finance.data!;
  return <section className="booking-finance" aria-label="Payments and referral">
    <h3>Payments</h3>
    <p>Total {formatAmount(f.totalAmountCents)} · paid {formatAmount(f.netPaidCents)}{f.netPaidCents < f.totalAmountCents ? ` · outstanding ${formatAmount(f.totalAmountCents - f.netPaidCents)}` : ""}</p>
    {f.payments.length > 0 && <ul className="admin-upcoming-list">{f.payments.map(p => <li key={p.id}>
      <span><strong>{p.kind === "REFUND" ? "Refund" : "Payment"} {formatAmount(p.amountCents)}</strong> · {p.method}, ref {p.externalReference}</span>
      <small>Received {p.receivedOn}; recorded by {p.recordedBy}, {dateTime(p.recordedAt)}</small></li>)}</ul>}
    {isAdmin && <PaymentForm reference={reference} onSaved={refresh} />}

    <h3>Referral</h3>
    {f.attribution ? <p>Referred by <strong>{f.attribution.partnerName}</strong> ({f.attribution.partnerReference}) via {sourceLabels[f.attribution.source ?? ""] ?? "unknown"}
      {f.attribution.capturedAt ? `, ${dateTime(f.attribution.capturedAt)}` : ""}.<br /><small>{f.attribution.commissionStatus}</small></p> : <p>No referral partner.</p>}
    {f.commissions.length > 0 && <ul className="admin-upcoming-list">{f.commissions.map(c => <li key={c.id}>
      <span><strong>{commissionKindLabels[c.kind]} {formatAmount(c.amountCents)}</strong>{c.payoutReference ? ` · paid out (${c.payoutReference})` : " · not paid out"}</span>
      <small>{c.calculation}</small></li>)}</ul>}
    {f.attributionHistory.length > 0 && <details><summary>Referral changes</summary><ul>{f.attributionHistory.map((h, i) =>
      <li key={i}>{h.fromPartner ?? "None"} → {h.toPartner ?? "None"}: {h.reason} ({h.changedBy}, {dateTime(h.changedAt)})</li>)}</ul></details>}
    {isAdmin && f.attribution && f.commissions.length === 0 && <button type="button" className="button button-quiet" disabled={evaluate.isPending}
      onClick={() => evaluate.mutate()}>Recalculate commission</button>}
    {evaluate.isError && <ErrorState message={evaluate.error.message} />}
    {isAdmin && f.commissions.length === 0 && <ReferralForm reference={reference} current={f.attribution?.partnerId ?? null} onSaved={refresh} />}
  </section>;
}

function PaymentForm({ reference, onSaved }: { reference: string; onSaved: (f: Finance) => void }) {
  const [form, setForm] = useState({ kind: "PAYMENT", amount: "", method: "EFT", externalReference: "", receivedOn: new Date().toISOString().slice(0, 10) });
  const [idempotencyKey, setKey] = useState(newIdempotencyKey);
  const [error, setError] = useState("");
  const save = useMutation({ mutationFn: (amountCents: number) => request<Finance>(`/api/v1/admin/bookings/${reference}/payments`, { method: "POST",
    body: JSON.stringify({ idempotencyKey, kind: form.kind, amountCents, method: form.method, externalReference: form.externalReference, receivedOn: form.receivedOn }) }),
    onSuccess: f => { onSaved(f); setForm(v => ({ ...v, amount: "", externalReference: "" })); setKey(newIdempotencyKey()); } });
  function submit(e: FormEvent) {
    e.preventDefault(); setError("");
    try { const cents = randToCents(form.amount); if (!cents) throw new Error("Enter an amount"); save.mutate(cents); } catch (err) { setError((err as Error).message); }
  }
  return <form className="admin-inline-form" onSubmit={submit} aria-label="Record a payment or refund">
    <label>Type<select value={form.kind} onChange={e => setForm({ ...form, kind: e.target.value })}><option value="PAYMENT">Payment received</option><option value="REFUND">Refund paid</option></select></label>
    <label>Amount (R)<input inputMode="decimal" required value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} /></label>
    <label>Method<input required maxLength={60} value={form.method} onChange={e => setForm({ ...form, method: e.target.value })} /></label>
    <label>Payment reference<input required maxLength={120} value={form.externalReference} onChange={e => setForm({ ...form, externalReference: e.target.value })} /></label>
    <label>Date<input type="date" required value={form.receivedOn} onChange={e => setForm({ ...form, receivedOn: e.target.value })} /></label>
    {(error || save.isError) && <ErrorState message={error || save.error!.message} />}
    <button className="button button-secondary" type="submit" disabled={save.isPending}>{save.isPending ? "Saving…" : "Record"}</button>
  </form>;
}

function ReferralForm({ reference, current, onSaved }: { reference: string; current: string | null; onSaved: (f: Finance) => void }) {
  const partners = useQuery({ queryKey: ["admin", "partners", "list"], queryFn: () => request<PartnerOption[]>("/api/v1/admin/partners"), retry: false });
  const [partnerId, setPartnerId] = useState(current ?? "");
  const [reason, setReason] = useState("");
  const save = useMutation({ mutationFn: () => request<Finance>(`/api/v1/admin/bookings/${reference}/referral`, { method: "PUT",
    body: JSON.stringify({ partnerId: partnerId || null, reason }) }), onSuccess: f => { onSaved(f); setReason(""); } });
  const options = (partners.data ?? []).filter(p => p.routes.some(r => r.route === "REFERRAL" && r.status === "APPROVED"));
  return <details className="admin-inline-details"><summary>Change referral</summary>
    <form className="admin-inline-form" onSubmit={e => { e.preventDefault(); save.mutate(); }}>
      <label>Referral partner<select value={partnerId} onChange={e => setPartnerId(e.target.value)}><option value="">No referral partner</option>
        {options.map(p => <option key={p.id} value={p.id}>{p.businessName} ({p.reference})</option>)}</select></label>
      <label className="full-field">Reason (kept in the audit log)<input required maxLength={600} value={reason} onChange={e => setReason(e.target.value)} /></label>
      {save.isError && <ErrorState message={save.error.message} />}
      <button className="button button-secondary" type="submit" disabled={save.isPending || (partnerId || null) === current}>Save referral</button>
    </form></details>;
}
