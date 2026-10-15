import { CalendarDays, Clock, Compass, MapPin } from "lucide-react";
import { Link } from "react-router-dom";
import type { ActivityCard } from "../../types/api";
import { ActivityImage } from "./ActivityImage";
import { minutesLabel, operatingLabels } from "./exploreLabels";

export const transportLink = (title: string) => `/book-private-trip?destination=${encodeURIComponent(title)}`;

/** Activity card in the style of the tour package cards. Things to Do shows no prices. */
export function ActivityCardView({ activity }: { activity: ActivityCard }) {
  const place = activity.area ? activity.areaLabel : activity.town;
  const status = operatingLabels[activity.operatingStatus];
  return (
    <article className="package-card activity-card">
      <Link to={`/explore/${activity.slug}`} className="package-image" aria-label={`View ${activity.title}`} tabIndex={-1}>
        {activity.image ? <ActivityImage media={activity.image} sizes="(max-width: 700px) 100vw, 400px" />
          : <span className="activity-card-placeholder" aria-hidden="true"><Compass /></span>}
        <span className="package-ribbon">{activity.regionLabel}</span>
      </Link>
      <div className="package-body">
        <div className="package-heading">
          <h3><Link to={`/explore/${activity.slug}`}>{activity.title}</Link></h3>
          {status && <span className="activity-status">{status}</span>}
        </div>
        <p className="activity-card-summary">{activity.summary}</p>
        {place && <p className="package-meta"><MapPin aria-hidden="true" />{place}</p>}
        {activity.kind === "EVENT" && activity.eventSchedule
          ? <p className="package-meta"><CalendarDays aria-hidden="true" />{activity.eventSchedule}</p>
          : <p className="package-meta"><Clock aria-hidden="true" />About {minutesLabel(activity.durationMinutes)}{activity.indoor ? " · indoors" : ""}</p>}
        <div className="package-actions">
          <Link className="button button-primary" to={`/explore/${activity.slug}`} aria-label={`View details: ${activity.title}`}>View details</Link>
          {activity.area
            ? <Link className="button button-navy" to={`/explore/plan?include=${activity.slug}`} aria-label={`Plan a visit: ${activity.title}`}>Plan a visit</Link>
            : <Link className="button button-navy" to={transportLink(activity.title)} aria-label={`Enquire about transport to ${activity.title}`}>Enquire about transport</Link>}
        </div>
      </div>
    </article>
  );
}
