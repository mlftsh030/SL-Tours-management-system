import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { formatAmount } from "../../lib/format";
import { getPricing, saveFee, savePricingSettings, saveRate } from "./fleetApi";
import { categoryLabels, centsToRand, dateTime, randToCents } from "./labels";
import type { PricingView } from "../../types/api";

/** Private-trip pricing. Sample rates are development data: saving a rate makes it the live price. */
export function PricingPanel({ isAdmin }: { isAdmin: boolean }) {
  const client = useQueryClient();
  const pricing = useQuery({ queryKey: ["admin", "pricing"], queryFn: getPricing, retry: false });
  const update = (next: PricingView) => { client.setQueryData(["admin", "pricing"], next); };
  if (pricing.isLoading) return <LoadingState label="Loading pricing" />;
  if (pricing.isError) return <ErrorState message={pricing.error.message} />;
  const p = pricing.data!;
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">Private trips</p><h1>Rates &amp; pricing rules</h1>
      <p>Base price = hourly rate × billable hours. Customers see the rate, duration, minimum charge and every fee before they submit.</p></div></div>
    {p.rates.some(r => r.rateStatus === "SAMPLE") && <div className="admin-callout"><strong>Sample rates are not real prices</strong>
      <p>Rates marked “Sample” were seeded for development. {p.sampleRatesShownPublicly ? "This environment shows them on the website with a “sample rate” label." : "The website treats those vehicles as “priced on request” until you save a real rate."}</p></div>}
    <h2 className="admin-subheading">Hourly rates by vehicle category</h2>
    <div className="admin-table-wrap"><table><thead><tr><th>Category</th><th>Hourly rate</th><th>Minimum</th><th>Status</th><th>{isAdmin ? "Edit" : "Updated"}</th></tr></thead><tbody>
      {p.rates.map(rate => <RateRow key={rate.category + rate.version} rate={rate} isAdmin={isAdmin} onSaved={update} />)}
    </tbody></table></div>
    <p className="form-note">A vehicle-specific rate (set on the vehicle) overrides its category. Leave a rate blank to quote that category on request.</p>
    <Settings pricing={p} isAdmin={isAdmin} onSaved={update} />
    <Fees pricing={p} isAdmin={isAdmin} onSaved={update} />
  </section>;
}

function RateRow({ rate, isAdmin, onSaved }: { rate: PricingView["rates"][number]; isAdmin: boolean; onSaved: (p: PricingView) => void }) {
  const [amount, setAmount] = useState(centsToRand(rate.hourlyRateCents)); const [minimum, setMinimum] = useState(String(rate.minimumMinutes / 60));
  const [error, setError] = useState("");
  const save = useMutation({ mutationFn: (body: unknown) => saveRate(rate.category, body), onSuccess: onSaved });
  function submit(event: FormEvent) {
    event.preventDefault(); setError("");
    try { save.mutate({ version: rate.version, hourlyRateCents: randToCents(amount), minimumMinutes: Math.round(Number(minimum) * 60) }); }
    catch (err) { setError((err as Error).message); }
  }
  return <tr>
    <td>{categoryLabels[rate.category]}</td>
    <td>{isAdmin ? <input aria-label={`${categoryLabels[rate.category]} hourly rate in rand`} inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} placeholder="On request" />
      : rate.hourlyRateCents ? formatAmount(rate.hourlyRateCents) : "On request"}</td>
    <td>{isAdmin ? <input aria-label={`${categoryLabels[rate.category]} minimum hours`} type="number" min={1} max={24} step={0.5} value={minimum} onChange={e => setMinimum(e.target.value)} /> : `${rate.minimumMinutes / 60} h`}</td>
    <td><span className={`admin-badge ${rate.rateStatus === "LIVE" ? "status-active" : "status-draft"}`}>{rate.rateStatus === "LIVE" ? "Live" : "Sample"}</span></td>
    <td>{isAdmin ? <form onSubmit={submit} className="admin-row-actions"><button className="button button-secondary" type="submit" disabled={save.isPending}>Save as live rate</button>
      {(error || save.isError) && <small className="admin-warning">{error || save.error!.message}</small>}</form> : <small>{rate.updatedBy} · {dateTime(rate.updatedAt)}</small>}</td>
  </tr>;
}

function Settings({ pricing, isAdmin, onSaved }: { pricing: PricingView; isAdmin: boolean; onSaved: (p: PricingView) => void }) {
  const s = pricing.settings;
  const [error, setError] = useState("");
  const save = useMutation({ mutationFn: savePricingSettings, onSuccess: onSaved });
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError("");
    const f = new FormData(event.currentTarget); const t = (n: string) => String(f.get(n) ?? "").trim();
    try {
      save.mutate({ version: s.version, turnaroundMinutes: Number(t("turnaround")), billingIncrementMinutes: Number(t("increment")),
        includedKmPerHour: t("includedKm") ? Number(t("includedKm")) : null, excessKmRateCents: randToCents(t("excessKm")),
        overtimeHourlyRateCents: randToCents(t("overtime")) });
    } catch (err) { setError((err as Error).message); }
  }
  return <form key={s.version} className="form-surface admin-editor" onSubmit={submit}>
    <h2>Rules</h2>
    <fieldset disabled={!isAdmin} className="admin-plain-fieldset"><div className="form-grid admin-form-grid">
      <label>Turnaround between trips (minutes)<input name="turnaround" type="number" min={0} max={1440} defaultValue={s.turnaroundMinutes} required /></label>
      <label>Round durations up to<select name="increment" defaultValue={s.billingIncrementMinutes}><option value={15}>15 minutes</option><option value={30}>30 minutes</option><option value={60}>Whole hours</option></select></label>
      <label>Included distance per hour (km) <em className="optional">(optional)</em><input name="includedKm" type="number" min={1} max={1000} defaultValue={s.includedKmPerHour ?? ""} /></label>
      <label>Extra distance per km (ZAR) <em className="optional">(optional)</em><input name="excessKm" inputMode="decimal" defaultValue={centsToRand(s.excessKmRateCents)} /></label>
      <label>Overtime per hour (ZAR) <em className="optional">(optional)</em><input name="overtime" inputMode="decimal" defaultValue={centsToRand(s.overtimeHourlyRateCents)} placeholder="Shown as “not published yet” if blank" /></label>
    </div></fieldset>
    <p className="form-note">Last changed by {s.updatedBy} {dateTime(s.updatedAt)}. Changes apply to new requests; submitted requests keep the price they were quoted.</p>
    {(error || save.isError) && <ErrorState message={error || save.error!.message} />}
    {isAdmin && <button className="button button-primary" type="submit" disabled={save.isPending}>Save rules</button>}
  </form>;
}

function Fees({ pricing, isAdmin, onSaved }: { pricing: PricingView; isAdmin: boolean; onSaved: (p: PricingView) => void }) {
  const [error, setError] = useState("");
  const save = useMutation({ mutationFn: ({ body, id }: { body: unknown; id?: string }) => saveFee(body, id), onSuccess: onSaved });
  function submit(event: FormEvent<HTMLFormElement>, fee?: PricingView["fees"][number]) {
    event.preventDefault(); setError("");
    const f = new FormData(event.currentTarget); const t = (n: string) => String(f.get(n) ?? "").trim();
    try {
      save.mutate({ id: fee?.id, body: { version: fee?.version ?? 0, name: t("name"), type: t("type"), amountCents: randToCents(t("amount")),
        active: f.has("active"), sortOrder: Number(t("sortOrder") || 0) } });
      if (!fee) event.currentTarget.reset();
    } catch (err) { setError((err as Error).message); }
  }
  const row = (fee?: PricingView["fees"][number]) => <form key={fee ? fee.id + fee.version : "new"} className="admin-inline-form admin-fee-form" onSubmit={event => submit(event, fee)}>
    <label>Fee name<input name="name" required maxLength={120} defaultValue={fee?.name} placeholder="Airport access fee" /></label>
    <label>Charged<select name="type" defaultValue={fee?.type ?? "FIXED"}><option value="FIXED">Once per trip</option><option value="PER_BILLABLE_HOUR">Per billable hour</option></select></label>
    <label>Amount (ZAR)<input name="amount" required inputMode="decimal" defaultValue={centsToRand(fee?.amountCents)} /></label>
    <label>Order<input name="sortOrder" type="number" min={0} max={1000} defaultValue={fee?.sortOrder ?? 0} /></label>
    <label className="admin-check"><input type="checkbox" name="active" defaultChecked={fee?.active ?? true} />Active</label>
    <button className="button button-secondary" type="submit" disabled={save.isPending}>{fee ? "Save" : "Add fee"}</button>
  </form>;
  return <section className="form-surface admin-editor"><h2>Additional fees</h2>
    <p className="form-note">Active fees are listed for customers before they submit and included in the backend total.</p>
    {!isAdmin ? <ul>{pricing.fees.map(f => <li key={f.id}>{f.name}: {formatAmount(f.amountCents)} {f.type === "FIXED" ? "per trip" : "per hour"}{f.active ? "" : " (inactive)"}</li>)}</ul>
      : <>{pricing.fees.map(fee => row(fee))}{row()}</>}
    {!pricing.fees.length && <p className="admin-empty">No additional fees configured.</p>}
    {(error || save.isError) && <ErrorState message={error || save.error!.message} />}
  </section>;
}
