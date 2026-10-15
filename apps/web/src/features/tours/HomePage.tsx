import { ArrowRight, CalendarHeart, Plane, Route } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { ActivityCardView } from "../explore/ActivityCardView";
import { listActivities } from "../explore/exploreApi";
import { VehicleSlideshow } from "../private-trips/VehicleSlideshow";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { CustomerReviews } from "../home/CustomerReviews";
import { GalleryStrip } from "../home/GalleryStrip";
import { HeroSlider } from "../home/HeroSlider";
import { TourPackages } from "../home/TourPackages";

const reasons = [
  { icon: "/assets/home/icon-10.png", text: "Our guides are knowledgeable and friendly" },
  { icon: "/assets/home/icon-11.png", text: "We have clean and safe Vehicles" },
  { icon: "/assets/home/icon-12.png", text: "We are reliable and always on time" }
];

export function HomePage() {
  useDocumentTitle(
    "SL Tours Cape Town | Guided Day Trips, Safaris & Transfers",
    "Book Cape Town tours and transfers with local guides. Explore Cape Point, Winelands, Garden Route, safaris, and private tour options with SL Tours."
  );

  return (
    <>
      <HeroSlider />

      <section className="intro-section" aria-labelledby="intro-title">
        <h2 id="intro-title">Cape Town Tours With a Local Team</h2>
        <p>
          SL Tours is a Cape Town-based tour operator offering guided day trips, Winelands wine tastings, Garden Route
          excursions, safaris, and airport transfers. Every tour is run by local guides who know the routes, the history,
          and the best spots to stop along the way. Whether you're visiting for a few days or exploring the Western Cape
          over a longer trip, we tailor each outing around your schedule and interests, using clean, well-maintained
          vehicles and drivers who show up on time. Browse our most requested packages below, or get in touch to build a
          custom itinerary.
        </p>
        <Link className="text-link" to="/about">About SL Tours<ArrowRight aria-hidden="true" /></Link>
      </section>

      <TourPackages />

      <section className="gallery-strip" aria-labelledby="vehicles-title">
        <div className="gallery-strip-heading">
          <h2 id="vehicles-title">Private Trips</h2>
          <Link className="text-link" to="/book-private-trip">Book a Private Trip<ArrowRight aria-hidden="true" /></Link>
        </div>
        <p className="home-section-lead">A vehicle and professional SL Tours driver by the hour, for your own itinerary.</p>
        <VehicleSlideshow />
      </section>

      <FeaturedThingsToDo />

      <CustomerReviews />

      <GalleryStrip />

      <section className="planning-band" aria-labelledby="planning-title">
        <div className="planning-inner">
          <div>
            <p className="eyebrow">Plan Your Cape Town Journey</p>
            <h2 id="planning-title">Not sure which tours fit? Tell us your dates and interests.</h2>
            <p>
              Share your arrival and departure dates, who is travelling, and what you enjoy. SL Tours will shape a tailored
              plan with tours, transfers, and a quote.
            </p>
          </div>
          <ul className="planning-points">
            <li>
              <CalendarHeart aria-hidden="true" />
              Day tours around your schedule
            </li>
            <li>
              <Plane aria-hidden="true" />
              Airport transfers and private drivers
            </li>
            <li>
              <Route aria-hidden="true" />
              Multi-day Garden Route and safari trips
            </li>
          </ul>
          <div className="planning-actions">
            <Link className="button button-primary" to="/contact">
              Send an Enquiry
            </Link>
            <Link className="button button-quiet" to="/plan-my-trip">
              Plan My Trip
            </Link>
          </div>
        </div>
      </section>

      <section className="why-band" aria-labelledby="why-title">
        <div className="why-label">
          <h2 id="why-title">Why Book With Us?</h2>
        </div>
        {reasons.map((reason) => (
          <div key={reason.text} className="why-item">
            <img src={reason.icon} alt="" width="40" height="40" loading="lazy" />
            <p>{reason.text}</p>
          </div>
        ))}
      </section>
    </>
  );
}

/** Well-known highlights from Things to Do in Cape Town, shown with the same cards as the full page. */
const featuredActivities = ["table-mountain-aerial-cableway", "cape-point", "boulders-penguin-colony"];
function FeaturedThingsToDo() {
  const activities = useQuery({ queryKey: ["activities"], queryFn: () => listActivities({}) });
  const picks = (activities.data ?? []).filter(a => featuredActivities.includes(a.slug))
    .sort((a, b) => featuredActivities.indexOf(a.slug) - featuredActivities.indexOf(b.slug));
  return (
    <section className="gallery-strip" aria-labelledby="things-title">
      <div className="gallery-strip-heading">
        <h2 id="things-title">Things to Do in Cape Town</h2>
        <Link className="text-link" to="/explore">View Things to Do in Cape Town<ArrowRight aria-hidden="true" /></Link>
      </div>
      {picks.length > 0 && <div className="package-grid">{picks.map(a => <ActivityCardView key={a.slug} activity={a} />)}</div>}
    </section>
  );
}
