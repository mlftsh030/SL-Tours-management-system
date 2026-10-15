import { WhatsAppIcon } from "../../components/WhatsAppIcon";
import { Clock, Star, UsersRound } from "lucide-react";
import { Link } from "react-router-dom";
import { tourWhatsappUrl as whatsappReserveUrl } from "../../lib/contact";
import { Price } from "../../lib/currency";
import { priceLabel } from "../../lib/format";
import type { TourSummary } from "../../types/api";

/** The group rule as published, or derived from the minimum when no label was published. */
export function groupRule(tour: Pick<TourSummary, "groupSizeLabel" | "minimumGuests">) {
  return tour.groupSizeLabel ?? (tour.minimumGuests > 1 ? `Minimum ${tour.minimumGuests} guests` : null);
}

export function PackageCard({ tour }: { tour: TourSummary }) {
  const group = groupRule(tour);
  // Group prices apply from the minimum; smaller groups are quoted privately rather than charged the group price.
  const smallerGroupsQuoted = tour.pricingType === "PER_PERSON" && tour.minimumGuests > 1;
  return (
    <article className="package-card">
      <Link to={`/experiences/${tour.slug}`} className="package-image" aria-label={`View ${tour.title}`}>
        <img src={tour.heroImage} alt={tour.heroImageAlt ?? ""} loading="lazy" width="400" height="260" />
        {tour.cardNote && <span className="package-ribbon">{tour.cardNote}</span>}
      </Link>
      <div className="package-body">
        <div className="package-heading">
          <h3>
            <Link to={`/experiences/${tour.slug}`}>{tour.title}</Link>
          </h3>
          <p className="package-price">
            <strong>{tour.pricingType === "CUSTOM" ? "Quote" : <Price cents={tour.basePriceCents} />}</strong>
            <span>{tour.pricingType === "CUSTOM" ? "on request" : priceLabel(tour.pricingType)}</span>
          </p>
        </div>
        {tour.shortDescription && <p className="package-summary">{tour.shortDescription}</p>}
        <p className="package-meta">
          <Clock aria-hidden="true" />
          {tour.duration}
        </p>
        {group && <p className="package-meta package-group">
          <UsersRound aria-hidden="true" />
          {group}
        </p>}
        {smallerGroupsQuoted && <p className="package-small-group">
          Fewer than {tour.minimumGuests}? <Link to="/experiences/private-custom-tour#enquire">Ask for a private quote</Link>
        </p>}
        <p className="package-meta package-trust">
          <Star aria-hidden="true" />
          Trusted by TripAdvisor travelers
        </p>
        <div className="package-actions">
          <Link className="button button-primary" to={`/experiences/${tour.slug}`}>
            View Details
          </Link>
          <Link className="button button-navy" to={`/experiences/${tour.slug}#enquire`} aria-label={`Enquire about ${tour.title}`}>
            Enquire
          </Link>
        </div>
        <a className="package-whatsapp" href={whatsappReserveUrl(tour.title)} target="_blank" rel="noopener noreferrer">
          <WhatsAppIcon aria-hidden="true" />
          Enquire on WhatsApp
        </a>
      </div>
    </article>
  );
}
