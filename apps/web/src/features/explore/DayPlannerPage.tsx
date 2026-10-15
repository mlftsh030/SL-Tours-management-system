import { useState, type FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { AlertTriangle, CheckCircle2, Info, MapPin } from "lucide-react";
import { PageBanner } from "../../components/PageBanner";
import { ErrorState } from "../../components/StateBlocks";
import { localDate } from "../../lib/dates";
import { formatAmount } from "../../lib/format";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { listActivities, planDay } from "./exploreApi";
import { areaLabels, categoryLabels, costKindLabels, minutesLabel, residencyLabels } from "./exploreLabels";
import { AssistantLauncher } from "../assistant/AssistantLauncher";
import type { ActivityArea, ActivityCategory, Itinerary, ItineraryCostLine } from "../../types/api";

const verdictStyle = { WITHIN_BUDGET: "is-ok", ESTIMATED_WITHIN_BUDGET: "is-estimate", OVER_BUDGET: "is-over", CANNOT_CONFIRM: "is-unknown", NO_BUDGET: "" } as const;

/** Rand amount typed by the visitor, to cents. Empty means "not set". */
function toCents(value: string): number | null {
  const v = value.trim().replace(/\s/g, "").replace(",", ".");
  if (!v) return null;
  if (!/^\d+(\.\d{1,2})?$/.test(v)) throw new Error("Enter amounts in rand, for example 1500 or 1500.50");
  const [r, c = ""] = v.split(".");
  return Number(r) * 100 + Number(c.padEnd(2, "0"));
}

export function DayPlannerPage() {
  useDocumentTitle("Plan a Day in Cape Town | SL Tours",
    "Build a practical day plan with opening times, travel between areas and transparent admission, transport and food costs.");
  const [params] = useSearchParams();
  const include = params.get("include");
  const activities = useQuery({ queryKey: ["activities"], queryFn: () => listActivities({}) });
  // Details can arrive pre-filled from the planning assistant's fallback link.
  const pick = (key: string, allowed: readonly string[] | RegExp, fallback: string) => {
    const v = params.get(key);
    return v && (allowed instanceof RegExp ? allowed.test(v) : allowed.includes(v)) ? v : fallback;
  };
  const [form, setForm] = useState({ date: pick("date", /^\d{4}-\d{2}-\d{2}$/, localDate(1)), start: pick("start", /^\d{2}:\d{2}$/, "09:00"),
    end: pick("end", /^\d{2}:\d{2}$/, "17:00"), adults: pick("adults", /^\d{1,2}$/, "2"), children: pick("children", /^\d{1,2}(,\d{1,2})*$/, "").replace(/,/g, ", "),
    residency: pick("residency", Object.keys(residencyLabels), "INTERNATIONAL"), startArea: pick("area", Object.keys(areaLabels), "CITY_CENTRE"),
    transport: pick("transport", ["OWN_VEHICLE", "SL_TOURS_PRIVATE_VEHICLE"], "OWN_VEHICLE"), food: pick("food", /^\d{1,6}$/, ""),
    budget: pick("budget", /^\d{1,7}$/, ""), maxStops: pick("stops", ["1", "2", "3", "4", "5"], "3"), onlyPublished: false });
  const [interests, setInterests] = useState<ActivityCategory[]>(() => (params.get("interests") ?? "").split(",")
    .filter((c): c is ActivityCategory => c in categoryLabels));
  const [error, setError] = useState("");
  const plan = useMutation({ mutationFn: planDay });
  const set = (field: keyof typeof form) => (event: { target: { value: string } }) => setForm(f => ({ ...f, [field]: event.target.value }));
  const categories = [...new Set((activities.data ?? []).map(a => a.category))];

  function submit(event: FormEvent) {
    event.preventDefault(); setError("");
    try {
      const childAges = form.children.trim() ? form.children.split(/[,\s]+/).filter(Boolean).map(age => {
        const n = Number(age); if (!Number.isInteger(n) || n < 0 || n > 17) throw new Error("Children's ages must be whole numbers from 0 to 17"); return n;
      }) : [];
      plan.mutate({ date: form.date, startTime: form.start, endTime: form.end, adults: Number(form.adults), childAges, residency: form.residency,
        interests, startArea: form.startArea, transport: form.transport, foodPerPersonCents: toCents(form.food), budgetCents: toCents(form.budget),
        maxStops: Number(form.maxStops), onlyPublishedPrices: form.onlyPublished, mustInclude: include ? [include] : [] });
    } catch (err) { setError((err as Error).message); }
  }
  const included = include ? activities.data?.find(a => a.slug === include) : null;

  return (
    <>
      <PageBanner title="Plan a day in Cape Town" subtitle="A practical route with opening times and every cost spelled out" image="/assets/gallery/41.webp" />
      <section className="section planner-page">
        <form className="form-surface wide-form" onSubmit={submit}>
          <AssistantLauncher />
          {included && <p className="private-trip-note"><Info aria-hidden="true" />Your plan will include {included.title}.</p>}
          <div className="form-grid">
            <label><span>Date</span><input type="date" required min={localDate()} value={form.date} onChange={set("date")} /></label>
            <label><span>Travellers are</span><select value={form.residency} onChange={set("residency")}>
              {Object.entries(residencyLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            <label><span>Start</span><input type="time" required value={form.start} onChange={set("start")} /></label>
            <label><span>Finish by</span><input type="time" required value={form.end} onChange={set("end")} /></label>
            <label><span>Adults</span><input type="number" min={1} max={20} required value={form.adults} onChange={set("adults")} /></label>
            <label><span>Children's ages <em className="optional">(optional)</em></span><input value={form.children} onChange={set("children")} placeholder="e.g. 4, 9" inputMode="numeric" /></label>
            <label><span>Starting from</span><select value={form.startArea} onChange={set("startArea")}>
              {(Object.keys(areaLabels) as ActivityArea[]).map(a => <option key={a} value={a}>{areaLabels[a]}</option>)}</select></label>
            <label><span>Getting around</span><select value={form.transport} onChange={set("transport")}>
              <option value="OWN_VEHICLE">Own or rented car</option><option value="SL_TOURS_PRIVATE_VEHICLE">SL Tours vehicle with driver</option></select></label>
            <label><span>Food per person <em className="optional">(optional, rand)</em></span><input inputMode="decimal" value={form.food} onChange={set("food")} placeholder="Leave blank to exclude" /></label>
            <label><span>Total budget <em className="optional">(optional, rand)</em></span><input inputMode="decimal" value={form.budget} onChange={set("budget")} /></label>
            <label><span>Number of stops</span><select value={form.maxStops} onChange={set("maxStops")}>{[1, 2, 3, 4, 5].map(n => <option key={n}>{n}</option>)}</select></label>
          </div>
          <fieldset><legend>Interests <em className="optional">(optional)</em></legend>
            <div className="choice-grid">{(Object.keys(categoryLabels) as ActivityCategory[]).filter(c => categories.includes(c)).map(c =>
              <label key={c} className="choice"><input type="checkbox" checked={interests.includes(c)}
                onChange={event => setInterests(list => event.target.checked ? [...list, c] : list.filter(x => x !== c))} /><span>{categoryLabels[c]}</span></label>)}</div>
          </fieldset>
          <label className="choice planner-only-priced"><input type="checkbox" checked={form.onlyPublished} onChange={event => setForm(f => ({ ...f, onlyPublished: event.target.checked }))} />
            <span>Only use activities with published prices</span></label>
          {(error || plan.isError) && <ErrorState message={error || plan.error!.message} />}
          <button className="button button-primary" type="submit" disabled={plan.isPending}>{plan.isPending ? "Planning…" : "Plan my day"}</button>
        </form>
        {plan.data && <PlanResult plan={plan.data} />}
      </section>
    </>
  );
}

export function CostRow({ line }: { line: ItineraryCostLine }) {
  return <li className={`cost-${line.kind.toLowerCase()}`}>
    <span>{line.label}{line.quantity > 1 && line.unitAmountCents != null ? ` × ${line.quantity}` : ""}
      {line.detail && <small>{line.detail}</small>}
      {line.sourceUrl && <small><a href={line.sourceUrl} target="_blank" rel="noopener noreferrer">Source</a>{line.verifiedOn ? `, checked ${line.verifiedOn}` : ""}</small>}</span>
    <span className="cost-amount">{line.amountCents != null ? formatAmount(line.amountCents) : "—"}<small className="cost-kind">{costKindLabels[line.kind]}</small></span>
  </li>;
}

export function PlanResult({ plan }: { plan: Itinerary }) {
  return <div className="planner-result" aria-live="polite">
    <div className={`planner-verdict ${verdictStyle[plan.verdict]}`}>
      {plan.verdict === "WITHIN_BUDGET" ? <CheckCircle2 aria-hidden="true" /> : <AlertTriangle aria-hidden="true" />}
      <div><strong>{plan.travellers} · {new Date(`${plan.date}T00:00:00`).toLocaleDateString("en-ZA", { dateStyle: "full" })}</strong><p>{plan.verdictMessage}</p></div>
    </div>
    <ol className="planner-timeline">
      {plan.stops.map(stop => <li key={stop.order} className={`planner-stop is-${stop.type.toLowerCase()}`}>
        {stop.travelMinutes > 0 && <p className="planner-travel">About {minutesLabel(stop.travelMinutes)} travel (estimate)</p>}
        <div className="planner-stop-head">
          <span className="planner-time">{stop.start.slice(0, 5)}-{stop.end.slice(0, 5)}</span>
          <div><h3>{stop.slug ? <Link to={`/explore/${stop.slug}`}>{stop.title}</Link> : stop.title}</h3>
            <p><MapPin aria-hidden="true" />{stop.areaLabel}</p></div>
        </div>
        {stop.notes.length > 0 && <ul className="planner-notes">{stop.notes.map(n => <li key={n}>{n}</li>)}</ul>}
        {stop.cautions.length > 0 && <ul className="planner-notes planner-cautions" aria-label="Not confirmed">
          {stop.cautions.map(c => <li key={c}><AlertTriangle aria-hidden="true" />{c}</li>)}</ul>}
        {stop.costs.length > 0 && <ul className="planner-costs">{stop.costs.map((c, i) => <CostRow key={i} line={c} />)}</ul>}
      </li>)}
    </ol>
    <div className="form-surface planner-summary">
      <h2>Costs</h2>
      {(plan.transport.length > 0 || plan.food.length > 0) && <ul className="planner-costs">{[...plan.transport, ...plan.food].map((c, i) => <CostRow key={i} line={c} />)}</ul>}
      <dl className="price-breakdown">
        <div><dt>Published prices</dt><dd>{formatAmount(plan.totals.verifiedCents)}</dd></div>
        {plan.totals.assumptionCents > 0 && <div><dt>Based on assumptions</dt><dd>{formatAmount(plan.totals.assumptionCents)}</dd></div>}
        {plan.totals.estimatedCents > 0 && <div><dt>Estimates</dt><dd>{formatAmount(plan.totals.estimatedCents)}</dd></div>}
        <div className="price-total"><dt>Known total</dt><dd>{formatAmount(plan.totals.knownTotalCents)}</dd></div>
        {plan.totals.unknownItems > 0 && <div><dt>Costs without a verified price</dt><dd>{plan.totals.unknownItems}</dd></div>}
      </dl>
      {plan.exclusions.length > 0 && <p><strong>Not included:</strong> {plan.exclusions.join(", ")}.</p>}
      <ConfidenceList plan={plan} />
      {plan.warnings.length > 0 && <><h3>Please note</h3><ul>{plan.warnings.map(w => <li key={w}>{w}</li>)}</ul></>}
      {plan.skipped.length > 0 && <><h3>Left out</h3><ul>{plan.skipped.map(s => <li key={s}>{s}</li>)}</ul></>}
      <h3>Assumptions</h3><ul>{plan.assumptions.map(a => <li key={a}>{a}</li>)}</ul>
      <p className="form-note">This plan is a guide, not a booking. Opening times and prices come from official sources on the dates shown and can change.
        <Link to="/contact"> Ask SL Tours</Link> to turn it into a guided day.</p>
    </div>
  </div>;
}

/** What this plan can and cannot claim, in plain words. */
function ConfidenceList({ plan }: { plan: Itinerary }) {
  const c = plan.confidence;
  return <ul className="planner-confidence" aria-label="What is confirmed">
    <li className={c.pricesConfirmed ? "is-ok" : "is-unknown"}>{c.pricesConfirmed ? "Prices: all from published prices for this date"
      : "Prices: some are estimates or not verified"}</li>
    <li className={c.scheduleConfirmed ? "is-ok" : "is-unknown"}>{c.scheduleConfirmed ? "Times: opening hours and sessions recently checked"
      : "Times: some opening hours or sessions are not confirmed"}</li>
    <li className={c.suitabilityConfirmed ? "is-ok" : "is-unknown"}>{c.suitabilityConfirmed ? "Suitability: published age and participation rules met"
      : "Suitability: some participation limits are not published"}</li>
  </ul>;
}
