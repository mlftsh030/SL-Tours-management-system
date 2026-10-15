import { AssistantLauncher } from "../assistant/AssistantLauncher";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { Search } from "lucide-react";
import { PageBanner } from "../../components/PageBanner";
import { EmptyState, ErrorState, LoadingState } from "../../components/StateBlocks";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { listActivities } from "./exploreApi";
import { ActivityCardView } from "./ActivityCardView";
import { areaLabels, regionLabels, regionOrder, themeLabels } from "./exploreLabels";
import type { ActivityArea, ActivityCard, ActivityRegion, ActivityTheme } from "../../types/api";

const themes = Object.keys(themeLabels) as ActivityTheme[];

export function ExplorePage() {
  useDocumentTitle("Things to Do in Cape Town and the Western Cape | SL Tours",
    "Things to do in Cape Town, the Winelands, Overberg, West Coast, Garden Route and Karoo, with opening hours and details checked against official sources.");
  const [params, setParams] = useSearchParams();
  const region = (params.get("region") ?? "") as ActivityRegion | "";
  const theme = (params.get("theme") ?? "") as ActivityTheme | "";
  const area = (params.get("area") ?? "") as ActivityArea | "";
  const [query, setQuery] = useState(params.get("q") ?? "");
  const activities = useQuery({ queryKey: ["activities"], queryFn: () => listActivities({}) });
  const update = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) => { if (value) next.set(key, value); else next.delete(key); });
    setParams(next, { replace: true });
  };
  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (activities.data ?? []).filter(a => (!region || a.region === region) && (!theme || a.themes.includes(theme)) && (!area || a.area === area)
      && (!q || `${a.title} ${a.summary} ${a.town ?? ""}`.toLowerCase().includes(q)));
  }, [activities.data, region, theme, area, query]);
  const themesPresent = new Set((activities.data ?? []).flatMap(a => a.themes));
  const filtered = Boolean(region || theme || area || query.trim());
  // Places to visit, grouped by region with Cape Town first; dated and recurring events are listed separately.
  const places = visible.filter(a => a.kind !== "EVENT");
  const events = visible.filter(a => a.kind === "EVENT");
  const groups = regionOrder.map(r => ({ region: r, items: places.filter(a => a.region === r) })).filter(g => g.items.length > 0);
  return (
    <>
      <PageBanner title="Things to Do in Cape Town" subtitle="And further afield in the Western Cape, with details checked against official sources" image="/assets/gallery/41.webp" />
      <section className="section tours-section">
        <aside className="private-trip-cta">
          <div>
            <p className="eyebrow">Day planner</p>
            <h2>Put a day together in Cape Town</h2>
            <p>Choose your interests and who is travelling. We order the stops sensibly, check opening times and show every assumption.</p>
          </div>
          <div className="explore-cta-actions">
            <Link className="button button-primary" to="/explore/plan">Build a day plan</Link>
            <AssistantLauncher />
          </div>
        </aside>
        <div className="tours-toolbar explore-toolbar">
          <div className="filter-row" role="group" aria-label="Filter by activity category">
            <button type="button" className={!theme ? "chip chip-active" : "chip"} aria-pressed={!theme} onClick={() => update({ theme: "" })}>All</button>
            {themes.filter(t => themesPresent.has(t)).map(t =>
              <button key={t} type="button" className={theme === t ? "chip chip-active" : "chip"} aria-pressed={theme === t}
                onClick={() => update({ theme: t })}>{themeLabels[t]}</button>)}
          </div>
          <div className="explore-filters">
            <label className="explore-area"><span className="sr-only">Region</span>
              <select value={region} onChange={event => update({ region: event.target.value, area: "" })}>
                <option value="">All regions</option>{regionOrder.map(r => <option key={r} value={r}>{regionLabels[r]}</option>)}
              </select></label>
            {region === "CAPE_TOWN" && <label className="explore-area"><span className="sr-only">Area of Cape Town</span>
              <select value={area} onChange={event => update({ area: event.target.value })}>
                <option value="">All of Cape Town</option>{(Object.keys(areaLabels) as ActivityArea[]).map(a => <option key={a} value={a}>{areaLabels[a]}</option>)}
              </select></label>}
            <label className="search-control"><Search aria-hidden="true" /><span className="sr-only">Search things to do</span>
              <input type="search" value={query} onChange={event => { setQuery(event.target.value); update({ q: event.target.value }); }} placeholder="Search things to do" /></label>
          </div>
        </div>
        {activities.isLoading && <LoadingState label="Loading things to do" />}
        {activities.isError && <><ErrorState message="Things to do could not be loaded right now." />
          <button className="button button-secondary" type="button" onClick={() => activities.refetch()}>Try again</button></>}
        {activities.data && <p className="result-count" aria-live="polite">{visible.length} {visible.length === 1 ? "listing" : "listings"}
          {filtered && <> · <button type="button" className="text-link" onClick={() => { setQuery(""); setParams(new URLSearchParams(), { replace: true }); }}>Clear filters</button></>}</p>}
        {activities.data && visible.length === 0 && <EmptyState message="Nothing matches these filters." />}
        {groups.length > 1 && <nav className="explore-jump" aria-label="Jump to a region">
          {groups.map(g => <a key={g.region} href={`#region-${g.region.toLowerCase()}`}>{regionLabels[g.region]} ({g.items.length})</a>)}</nav>}
        {groups.map(g => <ActivityGroup key={`${g.region}-${filtered}`} id={`region-${g.region.toLowerCase()}`} title={regionLabels[g.region]} items={g.items}
          limit={filtered ? undefined : INITIAL_PER_REGION}
          note={g.region === "CAPE_TOWN" ? undefined : "Further from Cape Town: plan a full day or an overnight stay. Day plans cover Cape Town only."} />)}
        {events.length > 0 && <ActivityGroup id="events" title="Events and performances" items={events}
          note="Recurring events, seasons and venues with changing programmes. Check the dates with the organiser." />}
        <p className="form-note explore-note">Details come from each operator's or authority's official website, on the date shown on each page.
          They can change, so check before you go. Pictures marked "illustrative" show the kind of activity, not the place itself.</p>
      </section>
    </>
  );
}

/** Unfiltered, each region opens with this many listings; the rest are one click away (all are linked in the page the
 *  server renders, so search engines still see every listing). Filters always show every match. */
const INITIAL_PER_REGION = 6;

function ActivityGroup({ id, title, items, note, limit }: { id: string; title: string; items: ActivityCard[]; note?: string; limit?: number }) {
  const [expanded, setExpanded] = useState(false);
  const shown = limit && !expanded ? items.slice(0, limit) : items;
  return <section className="explore-group" aria-labelledby={id}>
    <div className="explore-group-heading"><h2 id={id}>{title}</h2><span>{items.length}</span></div>
    {note && <p className="explore-group-note">{note}</p>}
    <div className="package-grid">{shown.map(a => <ActivityCardView key={a.slug} activity={a} />)}</div>
    {shown.length < items.length && <button type="button" className="button button-secondary explore-more" onClick={() => setExpanded(true)}>
      Show all {items.length} in {title}</button>}
  </section>;
}
