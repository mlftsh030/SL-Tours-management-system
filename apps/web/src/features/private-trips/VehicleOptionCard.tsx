import { Check, UsersRound } from "lucide-react";
import type { ReactNode } from "react";
import type { VehicleOption } from "../../types/api";

export const ILLUSTRATIVE_VEHICLE = "Illustrative image—actual vehicle may differ";

/** Picture of a vehicle option, with the illustrative label when it is not an SL Tours photo. */
export function VehicleOptionImage({ option, sizes }: { option: VehicleOption; sizes: string }) {
  return <>
    <img src={option.imageThumbnailUrl ?? option.imageUrl} srcSet={option.imageThumbnailUrl ? `${option.imageThumbnailUrl} 480w, ${option.imageUrl} 960w` : undefined}
      sizes={sizes} alt={option.imageAlt} loading="lazy" decoding="async" width="960" height="600" />
    {option.illustrativeImage && <span className="illustrative-badge">{ILLUSTRATIVE_VEHICLE}</span>}
  </>;
}

export function capacityText(option: VehicleOption) {
  return `Up to ${option.passengerCapacity} passengers${option.capacityConfirmed ? "" : " (capacity to be confirmed)"}`;
}

export function VehicleCredit({ option }: { option: VehicleOption }) {
  return <p className="media-credit">Photo: <a href={option.imageSourcePage} target="_blank" rel="noopener noreferrer">{option.imageCredit}</a>, {option.imageLicenceUrl
    ? <a href={option.imageLicenceUrl} target="_blank" rel="noopener noreferrer license">{option.imageLicence}</a> : option.imageLicence}</p>;
}

/** Selectable vehicle option for the private-trip form. */
export function VehicleOptionCard({ option, selected, onSelect, children }: { option: VehicleOption; selected?: boolean; onSelect?: () => void; children?: ReactNode }) {
  return <article className={`vehicle-card${selected ? " is-selected" : ""}`}>
    <div className="vehicle-card-image"><VehicleOptionImage option={option} sizes="(max-width: 700px) 100vw, 360px" /></div>
    <div className="vehicle-card-body">
      <div className="vehicle-card-heading"><div>
        <h3>{option.name}</h3>
        <p className="vehicle-card-category">{option.typeLabel}</p>
      </div></div>
      <ul className="vehicle-card-specs"><li><UsersRound aria-hidden="true" />{capacityText(option)}</li></ul>
      <p className="vehicle-card-description">{option.description}</p>
      {!option.capacityConfirmed && <p className="vehicle-card-note">{option.capacityNote}</p>}
      {onSelect && <button type="button" className={`button ${selected ? "button-navy" : "button-primary"}`} onClick={onSelect} aria-pressed={selected}
        aria-label={selected ? `${option.name} selected` : `Choose the ${option.name}`}>
        {selected ? <><Check aria-hidden="true" />Selected</> : "Choose this vehicle"}</button>}
      {children}
      <VehicleCredit option={option} />
    </div>
  </article>;
}
