import { Briefcase, Car, Check, UsersRound } from "lucide-react";
import { formatAmount } from "../../lib/format";
import type { VehicleCard as Vehicle } from "../../types/api";
import { ILLUSTRATIVE_VEHICLE } from "./VehicleOptionCard";

const categoryLabels: Record<string, string> = { SEDAN: "Sedan", SUV: "SUV", VAN: "Van", LUXURY_VAN: "Luxury van", MINIBUS: "Minibus", COACH: "Coach" };

/** Public vehicle card: photo, category, seats, amenities and the backend price. No registration or internal details. */
export function VehicleCard({ vehicle, selected, onSelect, actionLabel = "Choose this vehicle", note }: {
  vehicle: Vehicle; selected?: boolean; onSelect?: () => void; actionLabel?: string; note?: string;
}) {
  const quote = vehicle.quote;
  return (
    <article className={`vehicle-card${selected ? " is-selected" : ""}`}>
      <div className="vehicle-card-image">
        {vehicle.imageUrl ? <img src={vehicle.imageUrl} alt={vehicle.imageAlt} loading="lazy" width="400" height="240" />
          : <div className="vehicle-card-placeholder" aria-hidden="true"><Car /></div>}
        {vehicle.imageUrl && vehicle.illustrativeImage && <span className="illustrative-badge">{ILLUSTRATIVE_VEHICLE}</span>}
        {vehicle.sampleRate && <span className="vehicle-sample">Sample rate · development only</span>}
      </div>
      {vehicle.imageUrl && vehicle.imageCredit && <p className="media-credit">Photo: {vehicle.imageCredit}</p>}
      <div className="vehicle-card-body">
        <div className="vehicle-card-heading">
          <div>
            <h3>{vehicle.name}</h3>
            <p className="vehicle-card-category">{categoryLabels[vehicle.category] ?? vehicle.category}</p>
          </div>
          <p className="vehicle-card-price">
            {quote ? <><strong>{formatAmount(quote.totalCents)}</strong><span>total for {quote.billableMinutes / 60} h</span></>
              : vehicle.hourlyRateCents ? <><strong>{formatAmount(vehicle.hourlyRateCents)}</strong><span>per hour</span></>
              : <><strong>Rate</strong><span>on request</span></>}
          </p>
        </div>
        <ul className="vehicle-card-specs">
          <li><UsersRound aria-hidden="true" />{vehicle.capacity} passengers</li>
          {vehicle.luggageCapacity != null && <li><Briefcase aria-hidden="true" />{vehicle.luggageCapacity} large bags</li>}
          {vehicle.hourlyRateCents != null && quote && <li>{formatAmount(vehicle.hourlyRateCents)}/hour · min {(vehicle.minimumMinutes ?? 60) / 60} h</li>}
        </ul>
        {vehicle.amenities.length > 0 && <ul className="vehicle-card-amenities">{vehicle.amenities.map(a => <li key={a}>{a}</li>)}</ul>}
        {vehicle.description && <p className="vehicle-card-description">{vehicle.description}</p>}
        {note && <p className="vehicle-card-note">{note}</p>}
        {onSelect && <button type="button" className={`button ${selected ? "button-navy" : "button-primary"}`} onClick={onSelect} aria-pressed={selected}>
          {selected ? <><Check aria-hidden="true" />Selected</> : actionLabel}
        </button>}
      </div>
    </article>
  );
}
