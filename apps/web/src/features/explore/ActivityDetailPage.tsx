import { AssistantLauncher } from "../assistant/AssistantLauncher";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { AlertTriangle, CalendarDays, Clock, CloudSun, ExternalLink, HelpCircle, MapPin, Navigation, PlayCircle, ShieldCheck } from "lucide-react";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { getActivity } from "./exploreApi";
import { transportLink } from "./ActivityCardView";
import { ActivityImage, ILLUSTRATIVE_LABEL, MediaCredit } from "./ActivityImage";
import { dayList, hhmm, minutesLabel, monthDay, operatingLabels, shortDate, themeLabels } from "./exploreLabels";
import type { ActivityMediaItem, ActivityPage } from "../../types/api";

export function ActivityDetailPage() {
  const { slug = "" } = useParams();
  const activity = useQuery({ queryKey: ["activity", slug], queryFn: () => getActivity(slug), retry: false });
  useDocumentTitle(activity.data?.seoTitle ?? "Things to Do in Cape Town | SL Tours", activity.data?.seoDescription);
  if (activity.isLoading) return <LoadingState label="Loading activity" />;
  if (activity.isError || !activity.data) return <section className="section"><ErrorState message="This activity could not be found." /><Link to="/explore">Back to Things to Do in Cape Town</Link></section>;
  const a: ActivityPage = activity.data;
  const images = a.media.filter(m => m.kind === "IMAGE");
  const videos = a.media.filter(m => m.kind === "VIDEO");
  const hero = images[0];
  const place = [a.area ? a.areaLabel : a.town, a.regionLabel].filter(Boolean).join(", ");
  const status = operatingLabels[a.operatingStatus];
  const mapLink = a.latitude != null && a.longitude != null ? `https://www.google.com/maps/search/?api=1&query=${a.latitude},${a.longitude}` : null;
  const actions = <>
    {a.plannable && <Link className="button button-primary" to={`/explore/plan?include=${a.slug}`}>Plan a visit</Link>}
    <Link className={a.plannable ? "button button-secondary" : "button button-primary"} to={transportLink(a.title)}>Enquire about transport</Link>
  </>;
  return (
    <>
      <section className="detail-hero activity-hero" style={{ backgroundImage: `linear-gradient(90deg, rgba(5, 30, 98, .88) 0%, rgba(5, 30, 98, .6) 45%, rgba(5, 30, 98, .15) 100%), url(${hero?.url ?? "/assets/gallery/41.jpg"})` }}>
        <div>
          <p className="eyebrow">{a.regionLabel}</p>
          <h1>{a.title}</h1>
          <p>{a.summary}</p>
          <div className="detail-meta">
            {place && <span><MapPin aria-hidden="true" />{place}</span>}
            {a.kind === "EVENT" && a.eventSchedule ? <span><CalendarDays aria-hidden="true" />{a.eventSchedule}</span>
              : <span><Clock aria-hidden="true" />About {minutesLabel(a.durationMinutes)}</span>}
            {a.weatherDependent && <span><CloudSun aria-hidden="true" />Weather dependent</span>}
          </div>
          <div className="detail-hero-actions">{actions}</div>
        </div>
        {hero?.depiction === "ILLUSTRATIVE" && <span className="illustrative-badge illustrative-badge-hero">{ILLUSTRATIVE_LABEL}</span>}
      </section>
      <section className="section detail-grid">
        <article className="activity-article">
          {status && <p className="activity-status-note" role="note"><AlertTriangle aria-hidden="true" /><span><strong>{status}.</strong> {a.operatingNote}
            {a.operatingCheckedOn && <small>Checked {shortDate(a.operatingCheckedOn)}.</small>}</span></p>}
          <p className="eyebrow">Overview</p>
          <h2>About {a.title}</h2>
          <p>{a.description}</p>
          {a.themes.length > 0 && <ul className="activity-themes" aria-label="Activity categories">{a.themes.map(t => <li key={t}>
            <Link to={`/explore?theme=${t}`}>{themeLabels[t]}</Link></li>)}</ul>}
          {images.length > 0 && <Gallery images={images} title={a.title} />}
          {a.whatToExpect && <><h3>What to expect</h3><p>{a.whatToExpect}</p></>}
          {videos.length > 0 && <><h3>Video</h3>{videos.map(v => <VideoFacade key={v.url} video={v} />)}</>}

          <h3>{a.kind === "EVENT" ? "When" : "Opening times"}</h3>
          {a.eventSchedule && <p>{a.eventSchedule}</p>}
          {a.scheduleType === "SCHEDULE_VARIES" && <p>Times vary. Check the operator's schedule before you go.</p>}
          {a.scheduleType === "ALWAYS_OPEN" && <p>No fixed opening hours.</p>}
          {a.scheduleType === "CHECK_OPERATOR" && a.hours.length === 0 && <p>Opening hours were not published on the official pages we checked. Confirm them with the operator before you go.</p>}
          {a.hours.length > 0 && <div className="admin-table-wrap activity-table"><table>
            <thead><tr><th>{a.scheduleType === "SESSIONS" ? "Session" : "Hours"}</th><th>Days</th><th>Season</th></tr></thead>
            <tbody>{a.hours.map((h, i) => <tr key={i}>
              <td data-label={a.scheduleType === "SESSIONS" ? "Session" : "Hours"}>{hhmm(h.opens)}-{hhmm(h.closes)}{h.lastEntry && <small>Last entry {hhmm(h.lastEntry)}</small>}{h.notes && <small>{h.notes}</small>}
                {h.caution && <small className="activity-caution">Not confirmed: {h.caution}</small>}</td>
              <td data-label="Days">{dayList(h.daysOfWeek)}</td>
              <td data-label="Season">{h.seasonStart ? `${h.seasonLabel ? `${h.seasonLabel}: ` : ""}${monthDay(h.seasonStart)} - ${monthDay(h.seasonEnd!)}` : "All year"}</td>
            </tr>)}</tbody></table></div>}

          <h3>Practical information</h3>
          <ul className="activity-extras">
            <li>Typical visit: about {minutesLabel(a.durationMinutes)}{a.indoor ? ", mostly indoors" : ""}.</li>
            {a.practicalInfo && <li>{a.practicalInfo}</li>}
            {a.bookingNotes && <li>{a.bookingNotes}</li>}
          </ul>

          <h3>Who can take part</h3>
          <ul className="activity-extras">
            {a.minAge != null && <li>Minimum age {a.minAge}.</li>}{a.accompanimentRule && <li>{a.accompanimentRule}</li>}
            {a.participationRules === "KNOWN" && a.participationNote && <li>{a.participationNote}</li>}
            {a.participationRules !== "KNOWN" && <li className="activity-caution">{a.participationRules === "UNKNOWN"
              ? "Age, fitness or other limits are not published" : "Limits are only partly published"}{a.participationNote ? `: ${a.participationNote}` : "."}
              {" "}Check with the operator before you go.</li>}
            {a.participationRules === "KNOWN" && a.minAge == null && !a.accompanimentRule && !a.participationNote && <li>No age or other limits are published.</li>}
          </ul>

          <h3>Accessibility</h3>
          <p>{a.accessibilityInfo ?? "Accessibility information is not published by the operator. Contact them before you go."}</p>

          {a.seasonalInfo && <><h3>Seasons, permits and closures</h3><p>{a.seasonalInfo}</p></>}
        </article>
        <aside className="booking-panel detail-panel">
          {a.address && <p className="detail-guests"><MapPin aria-hidden="true" />{a.address}</p>}
          {mapLink && <a className="text-link" href={mapLink} target="_blank" rel="noopener noreferrer"><Navigation aria-hidden="true" />Open in maps</a>}
          {a.officialUrl && <a className="button button-secondary" href={a.officialUrl} target="_blank" rel="noopener noreferrer"><ExternalLink aria-hidden="true" />Official website</a>}
          {actions}
          {a.plannable && <AssistantLauncher />}
          {!a.plannable && <p className="form-note">Day plans cover Cape Town only. Tell us about your trip and we will help with transport to {a.town ?? a.regionLabel}.</p>}
          <h3>Sources</h3>
          <ul className="activity-sources">{a.sources.map(s => <li key={s.url}><a href={s.url} target="_blank" rel="noopener noreferrer">{s.publisher}</a>
            <small>{s.covers}. Checked {shortDate(s.accessedOn)}.</small></li>)}</ul>
          {a.lastVerifiedOn && <p className="activity-check"><ShieldCheck aria-hidden="true" />Details checked against these sources on {shortDate(a.lastVerifiedOn)}.</p>}
          {a.supplierName ? <p className="form-note partner-disclosure">This experience is provided by {a.supplierName}, an SL Tours supplier partner.
            SL Tours has a commercial relationship with this supplier. Times can change; confirm before you go.</p>
          : <p className="form-note">SL Tours does not operate, sell or partner with this activity. Details can change; confirm with the operator before you go.</p>}
        </aside>
      </section>
      {a.faqs.length > 0 && <section className="section faq-section">
        <p className="eyebrow">Questions</p>
        <div className="faq-grid">{a.faqs.map(f => <div key={f.question}><HelpCircle aria-hidden="true" /><p><strong>{f.question}</strong><br />{f.answer}</p></div>)}</div>
      </section>}
    </>
  );
}

function Gallery({ images, title }: { images: ActivityMediaItem[]; title: string }) {
  return <div className="activity-gallery" role="group" aria-label={`Pictures of ${title}`}>
    {images.map(m => <figure key={m.url}>
      <div className="activity-gallery-image"><ActivityImage media={m} sizes="(max-width: 900px) 100vw, 640px" /></div>
      <figcaption>{m.caption && <span>{m.caption}. </span>}<MediaCredit media={m} /></figcaption>
    </figure>)}
  </div>;
}

/** Loads the official video only when asked; nothing plays until the visitor chooses to. */
function VideoFacade({ video }: { video: ActivityMediaItem }) {
  const [open, setOpen] = useState(false);
  return <figure className="activity-video">
    {open ? <iframe src={`${video.url}?autoplay=1&rel=0`} title={video.altText} loading="lazy" allow="autoplay; encrypted-media; picture-in-picture" allowFullScreen />
      : <button type="button" className="activity-video-poster" onClick={() => setOpen(true)} aria-label={`Play video: ${video.altText}`}>
        {video.thumbnailUrl && <img src={video.thumbnailUrl} alt="" loading="lazy" />}<PlayCircle aria-hidden="true" /></button>}
    <figcaption><MediaCredit media={video} /></figcaption>
  </figure>;
}
