import { ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";
import { galleryPhotos, thumbSrc } from "../gallery/photos";

const featuredIds = [30, 5, 43, 16, 44, 34];
const featured = featuredIds.flatMap((id) => galleryPhotos.filter((photo) => photo.id === id));

export function GalleryStrip() {
  return (
    <section className="gallery-strip" aria-labelledby="gallery-strip-title">
      <div className="gallery-strip-heading">
        <h2 id="gallery-strip-title">Pictures Taken On Tour</h2>
        <Link className="text-link" to="/gallery">
          View Gallery
          <ArrowRight aria-hidden="true" />
        </Link>
      </div>
      <ul className="gallery-strip-grid">
        {featured.map((photo) => (
          <li key={photo.id}>
            <Link to="/gallery" aria-label={`View gallery: ${photo.alt}`}>
              <img
                src={thumbSrc(photo)}
                alt=""
                width={photo.width}
                height={photo.height}
                loading="lazy"
                decoding="async"
              />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
