import { WhatsAppIcon } from "../../components/WhatsAppIcon";
import { CalendarCheck, CheckCircle2, Clock, HelpCircle, Info, MapPin, UsersRound, XCircle } from "lucide-react";
import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useLocation, useParams } from "react-router-dom";
import { getTour, listTours } from "../../lib/api";
import { tourWhatsappUrl } from "../../lib/contact";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import type { TourDetail, TourSummary } from "../../types/api";
import { PackageCard } from "./PackageCard";
import { TourEnquiryForm } from "./TourEnquiryForm";
import { groupLabel, TourPrice } from "./tourDisplay";

/** Splits "07:30 Hotel pickup", "Day 2, 08:30 Knysna" or "Step 1 Send your flight details" into a label and text. */
export function itineraryStep(item: string) {
  const m = /^((?:Day \d+, )?\d{1,2}:\d{2}|Step \d+)\s+(.*)$/.exec(item);
  return m ? { label: m[1], text: m[2] } : { label: null, text: item };
}

/** Up to three other tours, same category first, then in catalogue order. */
function related(all: TourSummary[], tour: TourDetail) {
  const others = all.filter(t => t.slug !== tour.slug && (tour.category === "TRANSFER" || t.category !== "TRANSFER"));
  return [...others.filter(t => t.category === tour.category), ...others.filter(t => t.category !== tour.category)].slice(0, 3);
}

export function TourDetailPage() {
  const { slug = "" } = useParams();
  const { hash } = useLocation();
  const tour = useQuery({ queryKey: ["tour", slug], queryFn: () => getTour(slug), enabled: Boolean(slug) });
  const all = useQuery({ queryKey: ["tours"], queryFn: () => listTours() });
  useDocumentTitle(tour.data?.seoTitle ?? "Experience | SL Tours", tour.data?.seoDescription);
  useEffect(() => {
    if (tour.data && hash === "#enquire") document.getElementById("enquire")?.scrollIntoView?.({ block: "start" });
  }, [tour.data, hash]);

  if (tour.isLoading) return <LoadingState label="Loading experience" />;
  if (tour.isError || !tour.data) return <ErrorState message={(tour.error as Error)?.message ?? "Experience not found"} />;

  const detail = tour.data;
  const isTransfer = detail.enquiryForm === "AIRPORT";
  return (
    <>
      <section
        className="detail-hero"
        style={{
          backgroundImage: `linear-gradient(90deg, rgba(5, 30, 98, .88) 0%, rgba(5, 30, 98, .6) 45%, rgba(5, 30, 98, .15) 100%), url(${detail.bannerImage ?? detail.heroImage})`
        }}
      >
        <div>
          <p className="eyebrow">{detail.tourTypeLabel ?? detail.category.replace("_", " ")}</p>
          <h1>{detail.title}</h1>
          <p>{detail.shortDescription}</p>
          <div className="detail-meta">
            <span><Clock aria-hidden="true" />{detail.duration}</span>
            <span><MapPin aria-hidden="true" /><TourPrice tour={detail} /></span>
            <span><UsersRound aria-hidden="true" />{groupLabel(detail)}</span>
          </div>
          <a className="button button-primary" href="#enquire">Send Enquiry</a>
        </div>
      </section>
      <section className="section detail-grid">
        <article>
          <p className="eyebrow">Overview</p>
          <h2>{detail.title}</h2>
          <p>{detail.fullDescription}</p>
          {detail.highlights.length > 0 && <>
            <h3>{isTransfer ? "What you can expect" : "Highlights"}</h3>
            <ul className="check-list">
              {detail.highlights.map(item => <li key={item}><CheckCircle2 aria-hidden="true" />{item}</li>)}
            </ul>
          </>}
          {detail.itinerary.length > 0 && <>
            <h3>{isTransfer ? "How it works" : "Sample itinerary"}</h3>
            {detail.itineraryNote && !isTransfer && <p className="form-note">{detail.itineraryNote}</p>}
            <div className="timeline">
              {detail.itinerary.map(item => { const step = itineraryStep(item);
                return <div key={item}><strong>{step.label ?? "•"}</strong><span>{step.text}</span></div>; })}
            </div>
          </>}
          {detail.practicalInfo.length > 0 && <>
            <h3>Practical information</h3>
            <ul className="check-list">
              {detail.practicalInfo.map(item => <li key={item}><Info aria-hidden="true" />{item}</li>)}
            </ul>
          </>}
        </article>
        <aside className="booking-panel detail-panel">
          <p className="detail-price">
            <strong>{detail.pricingType === "CUSTOM" ? "Quote on request" : <TourPrice tour={detail} />}</strong>
          </p>
          <p className="detail-guests"><UsersRound aria-hidden="true" />{groupLabel(detail)}</p>
          {detail.pickupSummary && <p className="detail-guests"><MapPin aria-hidden="true" />Pickup: {detail.pickupSummary}</p>}
          {detail.availabilityLabel && <p className="detail-guests"><CalendarCheck aria-hidden="true" />{detail.availabilityLabel}</p>}
          <div className="detail-actions">
            <a className="button button-primary" href="#enquire">Send Enquiry</a>
            <a className="button button-whatsapp" href={tourWhatsappUrl(detail.title)} target="_blank" rel="noopener noreferrer">
              <WhatsAppIcon aria-hidden="true" />Enquire on WhatsApp
            </a>
          </div>
          {detail.included.length > 0 && <>
            <h3>Included</h3>
            <ul className="inclusion-list">{detail.included.map(item => <li key={item}><CheckCircle2 aria-hidden="true" />{item}</li>)}</ul>
          </>}
          {detail.excluded.length > 0 && <>
            <h3>Excluded</h3>
            <ul className="inclusion-list exclusion-list">{detail.excluded.map(item => <li key={item}><XCircle aria-hidden="true" />{item}</li>)}</ul>
          </>}
          <p className="form-note">Cancellation terms, deposits and child prices are not published. SL Tours confirms them with your quote.</p>
        </aside>
      </section>
      {detail.faqs.length > 0 && (
        <section className="section faq-section">
          <p className="eyebrow">Questions</p>
          <div className="faq-grid">{detail.faqs.map(item => <div key={item}><HelpCircle aria-hidden="true" /><p>{item}</p></div>)}</div>
        </section>
      )}
      <section className="section contact-form-section" id="enquire" aria-label={`Enquire about ${detail.title}`}>
        <TourEnquiryForm tour={detail} />
      </section>
      {all.data && <section className="section tours-section" aria-labelledby="related-heading">
        <p className="eyebrow">Keep exploring</p>
        <h2 id="related-heading">You might also like</h2>
        <div className="package-grid">{related(all.data, detail).map(r => <PackageCard key={r.slug} tour={r} />)}</div>
      </section>}
    </>
  );
}
