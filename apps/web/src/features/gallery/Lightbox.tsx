import { ChevronLeft, ChevronRight, X } from "lucide-react";
import { useEffect, useRef, type KeyboardEvent, type TouchEvent } from "react";
import { fullSrc, type GalleryPhoto } from "./photos";

interface LightboxProps {
  photos: GalleryPhoto[];
  index: number;
  onChange: (index: number) => void;
  onClose: () => void;
}

const SWIPE_THRESHOLD_PX = 40;

export function Lightbox({ photos, index, onChange, onClose }: LightboxProps) {
  const dialog = useRef<HTMLDialogElement>(null);
  const touchStartX = useRef<number | null>(null);
  const photo = photos[index];
  const count = photos.length;
  const show = (offset: number) => onChange((index + offset + count) % count);

  useEffect(() => {
    const element = dialog.current;
    if (!element) {
      return;
    }
    // jsdom and very old browsers lack showModal; the open attribute still renders the dialog.
    if (typeof element.showModal === "function") {
      element.showModal();
    } else {
      element.setAttribute("open", "");
    }
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previousOverflow;
      if (typeof element.close === "function" && element.open) {
        element.close();
      }
    };
  }, []);

  useEffect(() => {
    // Warm the neighbouring full-size photos so next/previous feels instant.
    for (const offset of [1, -1]) {
      const neighbour = photos[(index + offset + count) % count];
      const image = new Image();
      image.src = fullSrc(neighbour);
    }
  }, [count, index, photos]);

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === "ArrowRight") {
      event.preventDefault();
      show(1);
    } else if (event.key === "ArrowLeft") {
      event.preventDefault();
      show(-1);
    }
  }

  function onTouchEnd(event: TouchEvent) {
    const start = touchStartX.current;
    touchStartX.current = null;
    if (start === null) {
      return;
    }
    const delta = event.changedTouches[0].clientX - start;
    if (Math.abs(delta) > SWIPE_THRESHOLD_PX) {
      show(delta < 0 ? 1 : -1);
    }
  }

  return (
    <dialog
      ref={dialog}
      className="lightbox"
      aria-label="Photo viewer"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onKeyDown={onKeyDown}
      onClick={(event) => event.target === event.currentTarget && onClose()}
    >
      <figure
        className="lightbox-figure"
        onTouchStart={(event) => (touchStartX.current = event.touches[0].clientX)}
        onTouchEnd={onTouchEnd}
      >
        <img key={photo.id} src={fullSrc(photo)} alt={photo.alt} />
        <figcaption>
          <span>{photo.alt}</span>
          <span aria-live="polite">
            {index + 1} / {count}
          </span>
        </figcaption>
      </figure>
      <button className="lightbox-close" type="button" onClick={onClose} aria-label="Close photo viewer" autoFocus>
        <X aria-hidden="true" />
      </button>
      <button className="lightbox-nav lightbox-prev" type="button" onClick={() => show(-1)} aria-label="Previous photo">
        <ChevronLeft aria-hidden="true" />
      </button>
      <button className="lightbox-nav lightbox-next" type="button" onClick={() => show(1)} aria-label="Next photo">
        <ChevronRight aria-hidden="true" />
      </button>
    </dialog>
  );
}
