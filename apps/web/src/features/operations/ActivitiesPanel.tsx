import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { request } from "../../lib/api";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { newIdempotencyKey } from "../../lib/uuid";
import { areaLabels, categoryLabels, residencyLabels } from "../explore/exploreLabels";
import { centsToRand, dateTime, humanize, randToCents } from "./labels";
import type { ActivityArea, ActivityCategory, ParticipationRules, PriceAudience, PriceStatus, PriceUnit, Residency, ScheduleType, SourceType } from "../../types/api";

interface Source { id: string; url: string; publisher: string; sourceType: SourceType; covers: string; accessedOn: string }
interface PriceRule { label: string; audience: PriceAudience; minAge: number | null; maxAge: number | null; residency: Residency; unit: PriceUnit; amountCents: number;
  daysOfWeek: string | null; timeFrom: string | null; timeTo: string | null; conditions: string | null; estimateDefault: boolean; effectiveFrom: string | null;
  effectiveTo: string | null; sourceId: string | null; verifiedOn: string; uncertaintyNote: string | null; promotional: boolean }
interface Extra { label: string; amountCents: number; unit: PriceUnit; mandatory: boolean; conditions: string | null; effectiveFrom: string | null; effectiveTo: string | null; sourceId: string | null; verifiedOn: string;
  uncertaintyNote: string | null }
interface HoursRow { seasonLabel: string | null; seasonStart: string | null; seasonEnd: string | null; daysOfWeek: string | null; opens: string; closes: string; lastEntry: string | null; notes: string | null; sourceId: string | null;
  verifiedOn: string; uncertaintyNote: string | null; validFrom: string | null; validTo: string | null }
interface AdminActivity {
  id: string; version: number; status: "DRAFT" | "PUBLISHED" | "ARCHIVED"; slug: string; title: string; category: ActivityCategory; summary: string; description: string;
  /** null outside Cape Town's planning areas. */
  area: ActivityArea | null; address: string | null; officialUrl: string | null; durationMinutes: number; scheduleType: ScheduleType; weatherDependent: boolean; indoor: boolean;
  minAge: number | null; accompanimentRule: string | null; bookingNotes: string | null; priceStatus: PriceStatus; verificationNotes: string | null;
  lastVerifiedOn: string | null; verifiedBy: string | null; verificationStale: boolean; heroImage: string | null; heroImageAlt: string | null; seoTitle: string | null;
  seoDescription: string | null; publishedAt: string | null; updatedAt: string; sources: Source[]; priceRules: PriceRule[]; extras: Extra[]; hours: HoursRow[];
  faqs: Array<{ question: string; answer: string }>; publishProblems: string[]; participationRules: ParticipationRules; participationNote: string | null;
  recheckIssues: string[];
}
interface Summary { id: string; status: AdminActivity["status"]; slug: string; title: string; category: ActivityCategory; area: ActivityArea | null; priceStatus: PriceStatus;
  lastVerifiedOn: string | null; verificationStale: boolean; priceRules: number; sources: number; publishProblems: string[]; recheckIssues: string[] }
interface Planning { version: number; roadFactor: number; averageSpeedKmh: number; bufferMinutes: number; lunchMinutes: number; updatedBy: string }

const today = () => new Date().toISOString().slice(0, 10);
const blank = (): AdminActivity => ({ id: "", version: 0, status: "DRAFT", slug: "", title: "", category: "NATURE", summary: "", description: "", area: "CITY_CENTRE",
  address: null, officialUrl: null, durationMinutes: 90, scheduleType: "OPENING_HOURS", weatherDependent: false, indoor: false, minAge: null, accompanimentRule: null,
  bookingNotes: null, priceStatus: "NOT_PUBLISHED", verificationNotes: null, lastVerifiedOn: null, verifiedBy: null, verificationStale: true, heroImage: null,
  heroImageAlt: null, seoTitle: null, seoDescription: null, publishedAt: null, updatedAt: "", sources: [], priceRules: [], extras: [], hours: [], faqs: [], publishProblems: [],
  participationRules: "KNOWN", participationNote: null, recheckIssues: [] });

const api = {
  list: () => request<Summary[]>("/api/v1/admin/activities"),
  get: (id: string) => request<AdminActivity>(`/api/v1/admin/activities/${id}`),
  save: (a: AdminActivity) => { const { id, recheckIssues: _recheck, ...body } = a; return request<AdminActivity>(`/api/v1/admin/activities${id ? `/${id}` : ""}`, { method: id ? "PUT" : "POST", body: JSON.stringify(body) }); },
  verify: (a: AdminActivity) => request<AdminActivity>(`/api/v1/admin/activities/${a.id}/verify`, { method: "POST", body: JSON.stringify({ version: a.version }) }),
  status: (a: AdminActivity, status: string) => request<AdminActivity>(`/api/v1/admin/activities/${a.id}/status`, { method: "POST", body: JSON.stringify({ version: a.version, status }) }),
  planning: () => request<Planning>("/api/v1/admin/planning"),
  savePlanning: (p: Planning) => request<Planning>("/api/v1/admin/planning", { method: "PUT", body: JSON.stringify(p) })
};

export function ActivitiesPanel() {
  const [selected, setSelected] = useState<string | "new" | null>(null);
  const list = useQuery({ queryKey: ["admin", "activities"], queryFn: api.list, retry: false });
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">Explore Cape Town</p><h1>Activities</h1>
      <p>Every published price, opening time and extra must cite an official source. Session times, promotions and prices also have their own
        re-check periods; anything overdue or unresolved is shown to visitors as unconfirmed.</p></div>
      <button className="button button-primary" onClick={() => setSelected("new")}>Add activity</button></div>
    {list.isLoading && <LoadingState label="Loading activities" />}{list.isError && <ErrorState message={list.error.message} />}
    {list.data && <div className="admin-table-wrap"><table><thead><tr><th>Activity</th><th>Category</th><th>Status</th><th>Prices</th><th>Last checked</th><th>Ready to publish</th><th></th></tr></thead><tbody>
      {list.data.map(a => <tr key={a.id}>
        <td>{a.title}<small>/explore/{a.slug}</small></td><td>{categoryLabels[a.category]}<small>{a.area ? areaLabels[a.area] : "Outside planning areas"}</small></td>
        <td><span className={`admin-badge ${a.status === "PUBLISHED" ? "status-active" : "status-draft"}`}>{humanize(a.status)}</span></td>
        <td>{humanize(a.priceStatus)}<small>{a.priceRules} rule(s), {a.sources} source(s)</small></td>
        <td>{a.lastVerifiedOn ?? "Never"}{a.verificationStale && <small className="admin-warning">Needs re-checking</small>}
          {a.recheckIssues.length > 0 && <small className="admin-warning">{a.recheckIssues.length} item(s) unconfirmed</small>}</td>
        <td>{a.publishProblems.length === 0 ? "Yes" : <small className="admin-warning">{a.publishProblems.length} issue(s)</small>}</td>
        <td><button className="button button-secondary" onClick={() => setSelected(a.id)}>Open</button></td>
      </tr>)}
    </tbody></table></div>}
    {selected === "new" && <ActivityEditor key="new" initial={blank()} onSaved={id => setSelected(id)} onClose={() => setSelected(null)} />}
    {selected && selected !== "new" && <LoadedEditor key={selected} id={selected} onClose={() => setSelected(null)} />}
    <PlanningAssumptions />
  </section>;
}

function LoadedEditor({ id, onClose }: { id: string; onClose: () => void }) {
  const activity = useQuery({ queryKey: ["admin", "activity", id], queryFn: () => api.get(id), retry: false });
  if (activity.isLoading) return <LoadingState label="Loading activity" />;
  if (activity.isError) return <ErrorState message={activity.error.message} />;
  return <ActivityEditor key={activity.data!.version} initial={activity.data!} onSaved={() => undefined} onClose={onClose} />;
}

function ActivityEditor({ initial, onSaved, onClose }: { initial: AdminActivity; onSaved: (id: string) => void; onClose: () => void }) {
  const client = useQueryClient();
  const [a, setA] = useState<AdminActivity>(initial);
  const [error, setError] = useState("");
  const done = async (next: AdminActivity) => {
    client.setQueryData(["admin", "activity", next.id], next); setA(next);
    await client.invalidateQueries({ queryKey: ["admin", "activities"] }); await client.invalidateQueries({ queryKey: ["activities"] });
    onSaved(next.id);
  };
  const save = useMutation({ mutationFn: () => api.save(a), onSuccess: done });
  const verify = useMutation({ mutationFn: () => api.verify(a), onSuccess: done });
  const status = useMutation({ mutationFn: (s: string) => api.status(a, s), onSuccess: done });
  const field = <K extends keyof AdminActivity>(key: K) => (event: { target: { value: string } }) =>
    setA(current => ({ ...current, [key]: event.target.value === "" && key !== "summary" && key !== "description" && key !== "title" && key !== "slug" ? null : event.target.value }));
  const list = <K extends "sources" | "priceRules" | "extras" | "hours" | "faqs">(key: K, index: number, patch: Partial<AdminActivity[K][number]>) =>
    setA(current => ({ ...current, [key]: current[key].map((row, i) => i === index ? { ...row, ...patch } : row) }));
  const remove = (key: "sources" | "priceRules" | "extras" | "hours" | "faqs", index: number) => setA(current => ({ ...current, [key]: current[key].filter((_, i) => i !== index) }));
  const sourceOptions = <>{<option value="">No source</option>}{a.sources.map(s => <option key={s.id} value={s.id}>{s.publisher || s.url}</option>)}</>;
  const busy = save.isPending || verify.isPending || status.isPending;
  const dirty = JSON.stringify(a) !== JSON.stringify(initial);
  function submit(event: FormEvent) { event.preventDefault(); setError(""); save.mutate(); }
  const money = (cents: number, onChange: (c: number) => void, label: string) => <input aria-label={label} inputMode="decimal" defaultValue={centsToRand(cents)}
    onBlur={event => { try { onChange(randToCents(event.target.value) ?? 0); setError(""); } catch (err) { setError((err as Error).message); } }} />;

  return <form className="form-surface admin-detail activity-editor" onSubmit={submit} aria-label="Activity editor">
    <div className="admin-heading"><div><p className="eyebrow">{a.id ? humanize(a.status) : "New activity"}</p><h2>{a.title || "Untitled activity"}</h2>
      {a.lastVerifiedOn && <p>Last checked {a.lastVerifiedOn} by {a.verifiedBy}{a.verificationStale ? " (needs re-checking)" : ""}</p>}</div>
      <button type="button" className="button button-secondary" onClick={onClose}>Close</button></div>
    {a.id && a.publishProblems.length > 0 && <div className="admin-callout"><strong>Before publishing</strong><ul>{a.publishProblems.map(p => <li key={p}>{p}</li>)}</ul></div>}
    {a.id && a.recheckIssues.length > 0 && <div className="admin-callout"><strong>Shown to visitors as unconfirmed</strong>
      <ul>{a.recheckIssues.map(p => <li key={p}>{p}</li>)}</ul></div>}

    <h3>Basics</h3>
    <div className="form-grid admin-form-grid">
      <label>Title<input required maxLength={180} value={a.title} onChange={field("title")} /></label>
      <label>URL slug<input required maxLength={160} pattern="[a-z0-9]+(-[a-z0-9]+)*" value={a.slug} onChange={field("slug")} /></label>
      <label>Category<select value={a.category} onChange={field("category")}>{Object.entries(categoryLabels).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
      <label>Planning area<select value={a.area ?? ""} onChange={e => setA(current => ({ ...current, area: (e.target.value || null) as ActivityArea | null }))}>
        <option value="">None (outside Cape Town's planning areas)</option>{Object.entries(areaLabels).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
      <label className="full-field">Summary<textarea required maxLength={400} rows={2} value={a.summary} onChange={field("summary")} /></label>
      <label className="full-field">Description<textarea required maxLength={4000} rows={4} value={a.description} onChange={field("description")} /></label>
      <label>Address or meeting point<input maxLength={300} value={a.address ?? ""} onChange={field("address")} /></label>
      <label>Official website<input maxLength={500} value={a.officialUrl ?? ""} onChange={field("officialUrl")} placeholder="https://" /></label>
      <label>Typical visit (minutes)<input type="number" min={15} max={720} value={a.durationMinutes} onChange={e => setA({ ...a, durationMinutes: Number(e.target.value) })} /></label>
      <label>Schedule<select value={a.scheduleType} onChange={field("scheduleType")}>
        <option value="OPENING_HOURS">Opening hours</option><option value="SESSIONS">Fixed sessions or departures</option>
        <option value="SCHEDULE_VARIES">Times change (e.g. films)</option><option value="ALWAYS_OPEN">No fixed hours</option></select></label>
      <label>Minimum age <em className="optional">(if stated)</em><input type="number" min={0} max={99} value={a.minAge ?? ""} onChange={e => setA({ ...a, minAge: e.target.value ? Number(e.target.value) : null })} /></label>
      <label>Price status<select value={a.priceStatus} onChange={field("priceStatus")}>
        <option value="VERIFIED">Verified prices below</option><option value="FREE">Free (stated by the source)</option>
        <option value="VARIES_AT_BOOKING">Set when booking</option><option value="NOT_PUBLISHED">Not published</option></select></label>
      <label className="admin-check"><input type="checkbox" checked={a.weatherDependent} onChange={e => setA({ ...a, weatherDependent: e.target.checked })} />Weather dependent</label>
      <label className="admin-check"><input type="checkbox" checked={a.indoor} onChange={e => setA({ ...a, indoor: e.target.checked })} />Indoors</label>
      <label>Participation limits<select value={a.participationRules} onChange={field("participationRules")}>
        <option value="KNOWN">Published (or none apply)</option><option value="PARTLY_KNOWN">Partly published</option><option value="UNKNOWN">Not published</option></select></label>
      <label>What is not published<input maxLength={400} value={a.participationNote ?? ""} onChange={field("participationNote")}
        required={a.participationRules !== "KNOWN"} placeholder="e.g. no minimum age or height published" /></label>
      <label className="full-field">Accompaniment or eligibility rule<input maxLength={300} value={a.accompanimentRule ?? ""} onChange={field("accompanimentRule")} /></label>
      <label className="full-field">Booking notes<input maxLength={600} value={a.bookingNotes ?? ""} onChange={field("bookingNotes")} /></label>
      <label>Image path <em className="optional">(existing /assets/ photo)</em><input maxLength={220} value={a.heroImage ?? ""} onChange={field("heroImage")} /></label>
      <label>Image alternative text<input maxLength={200} value={a.heroImageAlt ?? ""} onChange={field("heroImageAlt")} /></label>
      <label>SEO title <em className="optional">(optional)</em><input maxLength={180} value={a.seoTitle ?? ""} onChange={field("seoTitle")} /></label>
      <label>SEO description <em className="optional">(optional)</em><input maxLength={300} value={a.seoDescription ?? ""} onChange={field("seoDescription")} /></label>
      <label className="full-field">Internal verification notes <small>Never shown publicly</small><textarea maxLength={2000} rows={3} value={a.verificationNotes ?? ""} onChange={field("verificationNotes")} /></label>
    </div>

    <h3>Sources</h3>
    <p className="form-note">Use the operator's own site or a public authority. Third-party listings are not sufficient for publishing.</p>
    {a.sources.map((s, i) => <div key={s.id} className="activity-row">
      <label>Link<input required value={s.url} onChange={e => list("sources", i, { url: e.target.value })} /></label>
      <label>Publisher<input required value={s.publisher} onChange={e => list("sources", i, { publisher: e.target.value })} /></label>
      <label>Type<select value={s.sourceType} onChange={e => list("sources", i, { sourceType: e.target.value as SourceType })}>
        <option value="OFFICIAL_OPERATOR">Official operator</option><option value="PUBLIC_AUTHORITY">Public authority</option>
        <option value="TOURISM_AUTHORITY">Tourism authority</option><option value="OTHER">Other</option></select></label>
      <label>What it confirms<input required value={s.covers} onChange={e => list("sources", i, { covers: e.target.value })} /></label>
      <label>Checked on<input type="date" required value={s.accessedOn} onChange={e => list("sources", i, { accessedOn: e.target.value })} /></label>
      <button type="button" className="button button-quiet" onClick={() => remove("sources", i)}>Remove</button>
    </div>)}
    <button type="button" className="button button-secondary" onClick={() => setA({ ...a, sources: [...a.sources, { id: newIdempotencyKey(), url: "", publisher: "", sourceType: "OFFICIAL_OPERATOR", covers: "", accessedOn: today() }] })}>Add source</button>

    <h3>Price rules</h3>
    <p className="form-note">One row per published price. Use effective dates when the source gives them; the planner ignores prices outside their dates.
      Tick "default" for the price used in estimates when several fit (for example online versus ticket office).</p>
    {a.priceRules.map((p, i) => <div key={i} className="activity-row activity-price-row">
      <label>Label<input required value={p.label} onChange={e => list("priceRules", i, { label: e.target.value })} /></label>
      <label>Amount (R)<span>{money(p.amountCents, c => list("priceRules", i, { amountCents: c }), "Amount in rand")}</span></label>
      <label>Who<select value={p.audience} onChange={e => list("priceRules", i, { audience: e.target.value as PriceAudience })}>
        {["EVERYONE", "ADULT", "CHILD", "INFANT", "SENIOR", "STUDENT", "FAMILY", "GROUP"].map(x => <option key={x} value={x}>{humanize(x)}</option>)}</select></label>
      <label>Ages<span className="activity-ages"><input type="number" min={0} max={120} aria-label="Minimum age" value={p.minAge ?? ""} onChange={e => list("priceRules", i, { minAge: e.target.value ? Number(e.target.value) : null })} />
        <input type="number" min={0} max={120} aria-label="Maximum age" value={p.maxAge ?? ""} onChange={e => list("priceRules", i, { maxAge: e.target.value ? Number(e.target.value) : null })} /></span></label>
      <label>Residency<select value={p.residency} onChange={e => list("priceRules", i, { residency: e.target.value as Residency })}>
        <option value="ANY">Anyone</option>{Object.entries(residencyLabels).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
      <label>Charged<select value={p.unit} onChange={e => list("priceRules", i, { unit: e.target.value as PriceUnit })}>
        <option value="PER_PERSON">Per person</option><option value="PER_VEHICLE">Per vehicle</option><option value="PER_GROUP">Per group</option><option value="PER_ITEM">Per item</option></select></label>
      <label>Days <em className="optional">(e.g. SAT,SUN)</em><input value={p.daysOfWeek ?? ""} onChange={e => list("priceRules", i, { daysOfWeek: e.target.value || null })} /></label>
      <label>Session from/to<span className="activity-ages"><input type="time" value={p.timeFrom?.slice(0, 5) ?? ""} onChange={e => list("priceRules", i, { timeFrom: e.target.value || null })} />
        <input type="time" value={p.timeTo?.slice(0, 5) ?? ""} onChange={e => list("priceRules", i, { timeTo: e.target.value || null })} /></span></label>
      <label>Valid from/to<span className="activity-ages"><input type="date" value={p.effectiveFrom ?? ""} onChange={e => list("priceRules", i, { effectiveFrom: e.target.value || null })} />
        <input type="date" value={p.effectiveTo ?? ""} onChange={e => list("priceRules", i, { effectiveTo: e.target.value || null })} /></span></label>
      <label>Source<select value={p.sourceId ?? ""} onChange={e => list("priceRules", i, { sourceId: e.target.value || null })}>{sourceOptions}</select></label>
      <label>Checked on<input type="date" required value={p.verifiedOn} onChange={e => list("priceRules", i, { verifiedOn: e.target.value })} /></label>
      <label className="full-field">Conditions<input maxLength={400} value={p.conditions ?? ""} onChange={e => list("priceRules", i, { conditions: e.target.value || null })} /></label>
      <label className="full-field">Unresolved doubt <em className="optional">(e.g. official pages disagree; priced as an estimate until cleared)</em>
        <input maxLength={400} value={p.uncertaintyNote ?? ""} onChange={e => list("priceRules", i, { uncertaintyNote: e.target.value || null })} /></label>
      <label className="admin-check"><input type="checkbox" checked={p.estimateDefault} onChange={e => list("priceRules", i, { estimateDefault: e.target.checked })} />Default</label>
      <label className="admin-check"><input type="checkbox" checked={p.promotional} onChange={e => list("priceRules", i, { promotional: e.target.checked })} />Promotion (needs an end date)</label>
      <button type="button" className="button button-quiet" onClick={() => remove("priceRules", i)}>Remove</button>
    </div>)}
    <button type="button" className="button button-secondary" onClick={() => setA({ ...a, priceRules: [...a.priceRules, { label: "", audience: "ADULT", minAge: null, maxAge: null,
      residency: "ANY", unit: "PER_PERSON", amountCents: 0, daysOfWeek: null, timeFrom: null, timeTo: null, conditions: null, estimateDefault: true, effectiveFrom: null,
      effectiveTo: null, sourceId: a.sources[0]?.id ?? null, verifiedOn: today(), uncertaintyNote: null, promotional: false }] })}>Add price</button>

    <h3>Additional costs</h3>
    {a.extras.map((x, i) => <div key={i} className="activity-row">
      <label>Label<input required value={x.label} onChange={e => list("extras", i, { label: e.target.value })} /></label>
      <label>Amount (R)<span>{money(x.amountCents, c => list("extras", i, { amountCents: c }), "Extra amount in rand")}</span></label>
      <label>Charged<select value={x.unit} onChange={e => list("extras", i, { unit: e.target.value as PriceUnit })}>
        <option value="PER_PERSON">Per person</option><option value="PER_VEHICLE">Per vehicle</option><option value="PER_GROUP">Per group</option><option value="PER_ITEM">Per item</option></select></label>
      <label>Source<select value={x.sourceId ?? ""} onChange={e => list("extras", i, { sourceId: e.target.value || null })}>{sourceOptions}</select></label>
      <label>Checked on<input type="date" required value={x.verifiedOn} onChange={e => list("extras", i, { verifiedOn: e.target.value })} /></label>
      <label className="admin-check"><input type="checkbox" checked={x.mandatory} onChange={e => list("extras", i, { mandatory: e.target.checked })} />Required</label>
      <label className="full-field">Conditions<input value={x.conditions ?? ""} onChange={e => list("extras", i, { conditions: e.target.value || null })} /></label>
      <label className="full-field">Unresolved doubt<input maxLength={400} value={x.uncertaintyNote ?? ""} onChange={e => list("extras", i, { uncertaintyNote: e.target.value || null })} /></label>
      <button type="button" className="button button-quiet" onClick={() => remove("extras", i)}>Remove</button>
    </div>)}
    <button type="button" className="button button-secondary" onClick={() => setA({ ...a, extras: [...a.extras, { label: "", amountCents: 0, unit: "PER_VEHICLE", mandatory: false,
      conditions: null, effectiveFrom: null, effectiveTo: null, sourceId: a.sources[0]?.id ?? null, verifiedOn: today(), uncertaintyNote: null }] })}>Add cost</button>

    <h3>{a.scheduleType === "SESSIONS" ? "Sessions" : "Opening hours"}</h3>
    {a.hours.map((h, i) => <div key={i} className="activity-row">
      <label>Opens<input type="time" required value={h.opens.slice(0, 5)} onChange={e => list("hours", i, { opens: e.target.value })} /></label>
      <label>Closes<input type="time" required value={h.closes.slice(0, 5)} onChange={e => list("hours", i, { closes: e.target.value })} /></label>
      <label>Last entry<input type="time" value={h.lastEntry?.slice(0, 5) ?? ""} onChange={e => list("hours", i, { lastEntry: e.target.value || null })} /></label>
      <label>Days<input value={h.daysOfWeek ?? ""} onChange={e => list("hours", i, { daysOfWeek: e.target.value || null })} placeholder="Every day" /></label>
      <label>Season (MM-DD)<span className="activity-ages"><input value={h.seasonStart ?? ""} placeholder="09-01" onChange={e => list("hours", i, { seasonStart: e.target.value || null })} />
        <input value={h.seasonEnd ?? ""} placeholder="03-31" onChange={e => list("hours", i, { seasonEnd: e.target.value || null })} /></span></label>
      <label>Source<select value={h.sourceId ?? ""} onChange={e => list("hours", i, { sourceId: e.target.value || null })}>{sourceOptions}</select></label>
      <label>Checked on<input type="date" required value={h.verifiedOn} onChange={e => list("hours", i, { verifiedOn: e.target.value })} /></label>
      <label>Observed for <em className="optional">(sampled dates only)</em><span className="activity-ages">
        <input type="date" aria-label="Observed from" value={h.validFrom ?? ""} onChange={e => list("hours", i, { validFrom: e.target.value || null })} />
        <input type="date" aria-label="Observed until" value={h.validTo ?? ""} onChange={e => list("hours", i, { validTo: e.target.value || null })} /></span></label>
      <label className="full-field">Notes<input value={h.notes ?? ""} onChange={e => list("hours", i, { notes: e.target.value || null })} /></label>
      <label className="full-field">Unresolved doubt<input maxLength={400} value={h.uncertaintyNote ?? ""} onChange={e => list("hours", i, { uncertaintyNote: e.target.value || null })} /></label>
      <button type="button" className="button button-quiet" onClick={() => remove("hours", i)}>Remove</button>
    </div>)}
    <button type="button" className="button button-secondary" onClick={() => setA({ ...a, hours: [...a.hours, { seasonLabel: null, seasonStart: null, seasonEnd: null, daysOfWeek: null,
      opens: "09:00", closes: "17:00", lastEntry: null, notes: null, sourceId: a.sources[0]?.id ?? null, verifiedOn: today(), uncertaintyNote: null,
      validFrom: null, validTo: null }] })}>Add {a.scheduleType === "SESSIONS" ? "session" : "hours"}</button>

    <h3>Visitor questions</h3>
    <p className="form-note">Answer real visitor questions from the sources. FAQs are shown on the page; they are not marked up for search results.</p>
    {a.faqs.map((f, i) => <div key={i} className="activity-row">
      <label className="full-field">Question<input required maxLength={300} value={f.question} onChange={e => list("faqs", i, { question: e.target.value })} /></label>
      <label className="full-field">Answer<textarea required maxLength={2000} rows={2} value={f.answer} onChange={e => list("faqs", i, { answer: e.target.value })} /></label>
      <button type="button" className="button button-quiet" onClick={() => remove("faqs", i)}>Remove</button>
    </div>)}
    <button type="button" className="button button-secondary" onClick={() => setA({ ...a, faqs: [...a.faqs, { question: "", answer: "" }] })}>Add question</button>

    {(error || save.isError || verify.isError || status.isError) && <ErrorState message={error || (save.error ?? verify.error ?? status.error)!.message} />}
    <div className="admin-actions activity-actions">
      <button className="button button-primary" type="submit" disabled={busy}>{save.isPending ? "Saving…" : "Save"}</button>
      {a.id && <button type="button" className="button button-secondary" disabled={busy || dirty} onClick={() => verify.mutate()}
        title={dirty ? "Save changes first" : undefined}>Mark as checked today</button>}
      {a.id && a.status !== "PUBLISHED" && <button type="button" className="button button-secondary" disabled={busy || dirty || a.publishProblems.length > 0} onClick={() => status.mutate("PUBLISHED")}>Publish</button>}
      {a.id && a.status === "PUBLISHED" && <button type="button" className="button button-quiet" disabled={busy || dirty} onClick={() => status.mutate("DRAFT")}>Unpublish</button>}
      {a.id && a.status !== "ARCHIVED" && <button type="button" className="button button-quiet" disabled={busy || dirty} onClick={() => status.mutate("ARCHIVED")}>Archive</button>}
    </div>
    {a.updatedAt && <p className="form-note">Last saved {dateTime(a.updatedAt)}. Published pages and the sitemap update as soon as you save or publish.</p>}
  </form>;
}

function PlanningAssumptions() {
  const client = useQueryClient();
  const planning = useQuery({ queryKey: ["admin", "planning"], queryFn: api.planning, retry: false });
  const [draft, setDraft] = useState<Planning | null>(null);
  const save = useMutation({ mutationFn: api.savePlanning, onSuccess: next => { client.setQueryData(["admin", "planning"], next); setDraft(null); } });
  if (!planning.data) return null;
  const p = draft ?? planning.data;
  const set = (key: keyof Planning) => (e: { target: { value: string } }) => setDraft({ ...p, [key]: Number(e.target.value) });
  return <form className="form-surface admin-editor" onSubmit={e => { e.preventDefault(); save.mutate(p); }}>
    <h2>Day-planner assumptions</h2>
    <p className="form-note">Travel time = straight-line distance between area centres × road factor at the average speed, plus a buffer. Visitors see these assumptions with every plan.</p>
    <div className="form-grid admin-form-grid">
      <label>Road factor<input type="number" min={1} max={3} step={0.05} value={p.roadFactor} onChange={set("roadFactor")} /></label>
      <label>Average speed (km/h)<input type="number" min={10} max={120} value={p.averageSpeedKmh} onChange={set("averageSpeedKmh")} /></label>
      <label>Parking and walking buffer (minutes)<input type="number" min={0} max={60} value={p.bufferMinutes} onChange={set("bufferMinutes")} /></label>
      <label>Lunch break (minutes)<input type="number" min={0} max={180} value={p.lunchMinutes} onChange={set("lunchMinutes")} /></label>
    </div>
    {save.isError && <ErrorState message={save.error.message} />}
    <button className="button button-secondary" type="submit" disabled={save.isPending || !draft}>Save assumptions</button>
    <p className="form-note">Last changed by {planning.data.updatedBy}.</p>
  </form>;
}
