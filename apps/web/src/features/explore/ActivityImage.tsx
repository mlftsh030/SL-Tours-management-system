import type { ActivityMediaItem } from "../../types/api";

export const ILLUSTRATIVE_LABEL = "Illustrative image—actual location may differ";

/** A catalogue picture with responsive sizes. Illustrative pictures carry a visible label. */
export function ActivityImage({ media, sizes, eager = false, label = true }: { media: ActivityMediaItem; sizes: string; eager?: boolean; label?: boolean }) {
  return <>
    <img src={media.thumbnailUrl ?? media.url} srcSet={media.thumbnailUrl ? `${media.thumbnailUrl} 480w, ${media.url} 960w` : undefined} sizes={sizes}
      alt={media.altText} loading={eager ? "eager" : "lazy"} decoding="async" width={media.width ?? 960} height={media.height ?? 640} />
    {label && media.depiction === "ILLUSTRATIVE" && <span className="illustrative-badge">{ILLUSTRATIVE_LABEL}</span>}
  </>;
}

/** "Photo: credit, licence" with links to the source page and licence. */
export function MediaCredit({ media }: { media: ActivityMediaItem }) {
  return <span className="media-credit">{media.kind === "VIDEO" ? "Video" : "Photo"}: <a href={media.sourcePage} target="_blank" rel="noopener noreferrer">{media.credit}</a>
    {media.licence && <>, {media.licenceUrl ? <a href={media.licenceUrl} target="_blank" rel="noopener noreferrer license">{media.licence}</a> : media.licence}</>}</span>;
}
