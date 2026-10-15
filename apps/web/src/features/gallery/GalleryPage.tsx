import { Expand } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { PageBanner } from "../../components/PageBanner";
import { contact } from "../../lib/contact";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { Lightbox } from "./Lightbox";
import { galleryPhotos, thumbSrc, type GalleryPhoto } from "./photos";
import { useSiteImages } from "../../lib/useSiteImages";

export function GalleryPage() {
  useDocumentTitle(
    "Tour Gallery | Real Moments From SL Tours Cape Town",
    "View real tour photos from SL Tours in Cape Town and surrounding destinations across South Africa."
  );
  const managed = useSiteImages("GALLERY");
  // Administrator-added photos lead the gallery; every existing photo stays.
  const photos: GalleryPhoto[] = [...managed.map((image, index) => ({ id: -(index + 1), width: 640, height: 480, alt: image.altText, src: image.url })), ...galleryPhotos];
  const [openIndex, setOpenIndex] = useState<number | null>(null);
  const triggers = useRef<Array<HTMLButtonElement | null>>([]);
  const lastViewed = useRef<number | null>(null);

  useEffect(() => {
    if (openIndex !== null) {
      lastViewed.current = openIndex;
    } else if (lastViewed.current !== null) {
      // Runs after the modal dialog unmounts; while it is open the rest of the page is inert.
      triggers.current[lastViewed.current]?.focus();
      lastViewed.current = null;
    }
  }, [openIndex]);

  return (
    <>
      <PageBanner title="Gallery" subtitle="Pictures taken on tour with our guests" image="/assets/gallery/30.webp" />
      <section className="section gallery-section" aria-labelledby="gallery-title">
        <div className="gallery-heading">
          <h2 id="gallery-title">Pictures Taken On Tour</h2>
          <p>
            Real moments from SL Tours days around Cape Town, the Winelands, the Garden Route and beyond. Tag us on{" "}
            <a href={contact.instagram} target="_blank" rel="noopener noreferrer">
              Instagram
            </a>{" "}
            to share yours.
          </p>
        </div>
        <ul className="gallery-grid">
          {photos.map((photo, index) => (
            <li key={photo.id}>
              <button
                ref={(element) => {
                  triggers.current[index] = element;
                }}
                type="button"
                className="gallery-item"
                onClick={() => setOpenIndex(index)}
                aria-label={`Open photo: ${photo.alt}`}
              >
                <img
                  src={thumbSrc(photo)}
                  alt=""
                  width={photo.width}
                  height={photo.height}
                  loading="lazy"
                  decoding="async"
                />
                <span className="gallery-item-overlay" aria-hidden="true">
                  <Expand />
                </span>
              </button>
            </li>
          ))}
        </ul>
        <div className="gallery-cta">
          <h2>Want to be in the next photo?</h2>
          <div className="gallery-cta-actions">
            <Link className="button button-primary" to="/experiences">
              Browse Our Tours
            </Link>
            <Link className="button button-navy" to="/plan-my-trip">
              Plan My Trip
            </Link>
          </div>
        </div>
      </section>
      {openIndex !== null && (
        <Lightbox photos={photos} index={openIndex} onChange={setOpenIndex} onClose={() => setOpenIndex(null)} />
      )}
    </>
  );
}
