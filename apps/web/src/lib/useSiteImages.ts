import { useEffect, useState } from "react";
import { request } from "./api";
import type { SiteImage } from "../types/api";

/**
 * Administrator-activated website images for a slot. Returns an empty list (so callers keep their built-in
 * images) while loading, when none are active, or if the API cannot be reached.
 */
export function useSiteImages(slot: SiteImage["slot"]) {
  const [images, setImages] = useState<SiteImage[]>([]);
  useEffect(() => {
    let live = true;
    request<SiteImage[]>(`/api/v1/public/site-images?slot=${slot}`)
      .then(result => { if (live && Array.isArray(result)) setImages(result); })
      .catch(() => undefined);
    return () => { live = false; };
  }, [slot]);
  return images;
}
