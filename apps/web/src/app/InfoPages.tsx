import {
  Car,
  Compass,
  Heart,
  Mail,
  MapPin,
  Phone,
  ShieldCheck,
  Sparkles,
  UsersRound
} from "lucide-react";
import { Link } from "react-router-dom";
import { PageBanner } from "../components/PageBanner";
import { EnquiryForm } from "../features/enquiries/EnquiryForm";
import { address, contact } from "../lib/contact";
import { useDocumentTitle } from "../lib/useDocumentTitle";

const pillars = [
  {
    icon: <UsersRound aria-hidden="true" />,
    title: "Local Expert Team",
    text: "Cape Town-based guides who know the routes, culture, and best moments for each stop."
  },
  {
    icon: <Compass aria-hidden="true" />,
    title: "Tailored Travel",
    text: "Flexible options from quick city visits to multi-day adventures based on your travel style."
  },
  {
    icon: <ShieldCheck aria-hidden="true" />,
    title: "Comfort and Safety",
    text: "Well-maintained vehicles and reliable, professional service focused on a smooth guest experience."
  }
];

const apart = [
  {
    icon: <Sparkles aria-hidden="true" />,
    title: "Expert Guides",
    text: "Our guides are not just knowledgeable but passionate storytellers who bring South Africa's history, culture, and natural wonders to life."
  },
  {
    icon: <Compass aria-hidden="true" />,
    title: "Tailored Experiences",
    text: "From half-day city tours to multi-day adventures along the Garden Route, we customize services to meet your travel needs."
  },
  {
    icon: <Car aria-hidden="true" />,
    title: "Comfort and Safety",
    text: "Travel in our well-maintained vehicles, ensuring your safety and comfort every step of the way."
  },
  {
    icon: <Heart aria-hidden="true" />,
    title: "Customer Focused",
    text: "We go above and beyond to create memorable, hassle-free adventures for every guest."
  }
];

const services = [
  {
    title: "City and Cultural Tours",
    text: "Guided city tours to major attractions such as Table Mountain, Robben Island, and the V&A Waterfront, including immersive visits to places like Bo-Kaap."
  },
  {
    title: "Adventure and Nature",
    text: "Thrilling experiences such as shark cage diving, hiking, paragliding, scenic Cape Peninsula drives, and wildlife safaris."
  },
  {
    title: "Custom Travel Packages",
    text: "Personalized itineraries designed around your preferences, travel style, and group size for a unique and memorable journey."
  },
  {
    title: "Airport Transfers",
    text: "Reliable and convenient transportation with timely pickups and drop-offs between Cape Town International Airport and your destination, tailored for comfort."
  }
];

export function AboutPage() {
  useDocumentTitle(
    "About SL Tours | Trusted Cape Town Tour Company",
    "SL Tours is a proudly Black-owned, Cape Town-based travel company run by professional tourist guide Lisolethu Nkcaza."
  );
  return (
    <>
      <PageBanner title="About Us" subtitle="Get to know more about SL Tours" image="/assets/gallery/44.webp" />

      <section className="section pillar-grid" aria-label="What we offer">
        {pillars.map((pillar) => (
          <div key={pillar.title} className="pillar">
            {pillar.icon}
            <h2>{pillar.title}</h2>
            <p>{pillar.text}</p>
          </div>
        ))}
      </section>

      <section className="section about-story">
        <div className="about-copy">
          <p className="eyebrow">About SL Tours</p>
          <h2>Unforgettable journeys that connect you with the heart of South Africa.</h2>
          <p>
            At SL Tours, we specialize in creating unforgettable travel experiences that showcase the best of South
            Africa. From the breathtaking landscapes of the Cape Winelands to the vibrant streets of Cape Town, we are
            dedicated to crafting journeys that connect you with the heart of our country.
          </p>
          <h3>Who We Are</h3>
          <p>
            Founded with a passion for exploration, SL Tours is a proudly Black-owned, Cape Town-based travel company
            that prides itself on delivering personalized and seamless travel experiences. Our team of knowledgeable
            guides, top-notch service, and commitment to safety make us the trusted choice for travelers exploring South
            Africa.
          </p>
        </div>
        <figure className="owner-card">
          <img
            src="/assets/home/owner.jpg"
            alt="Lisolethu Nkcaza, owner and guide of SL Tours"
            width="480"
            height="640"
            loading="lazy"
          />
          <figcaption>
            <h3>About the Owner</h3>
            <p>
              SL Tours is proudly owned by Lisolethu Nkcaza, who is originally from a rural area in the Eastern Cape. He
              holds a qualification in Tourism and is a professional tourist guide. He is driven by his love of South
              African history and is passionate about sharing it with others, giving guests an unforgettable experience.
              Beyond the business, he is also a devoted father and a present husband, and the business name is inspired
              by both his and his wife's names.
            </p>
          </figcaption>
        </figure>
      </section>

      <section className="apart-band" aria-labelledby="apart-title">
        <div className="apart-inner">
          <div>
            <p className="eyebrow">What Sets Us Apart?</p>
            <h2 id="apart-title">Expert guides, tailored trips, and real care for every guest.</h2>
          </div>
          <div className="apart-grid">
            {apart.map((item) => (
              <div key={item.title}>
                {item.icon}
                <h3>{item.title}</h3>
                <p>{item.text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="section services-section" aria-labelledby="services-title">
        <p className="eyebrow">About Services</p>
        <h2 id="services-title">Travel services across Cape Town and South Africa</h2>
        <p className="services-lead">
          SL Tours offers a wide range of travel services across Cape Town and South Africa, from city highlights and
          cultural experiences to adventure activities, wildlife safaris, and customized private travel packages.
        </p>
        <div className="services-grid">
          {services.map((service, index) => (
            <div key={service.title}>
              <span className="service-number" aria-hidden="true">
                {String(index + 1).padStart(2, "0")}
              </span>
              <h3>{service.title}</h3>
              <p>{service.text}</p>
            </div>
          ))}
        </div>
        <div className="page-actions">
          <Link className="button button-primary" to="/experiences">
            See Our Tours
          </Link>
          <Link className="button button-navy" to="/contact">
            Get in Touch
          </Link>
        </div>
      </section>
    </>
  );
}

export function ContactPage() {
  useDocumentTitle(
    "Contact SL Tours | Book Tours & Transfers in Cape Town",
    "Call, email, or WhatsApp SL Tours to book Cape Town tours, private trips, and airport transfers."
  );
  return (
    <>
      <PageBanner title="Contact Us" subtitle="Get in touch" image="/assets/gallery/21.webp" />
      <section className="section contact-cards" aria-label="Contact details">
        <a className="contact-card" href={contact.phoneHref}>
          <Phone aria-hidden="true" />
          <h2>Phone</h2>
          <p>We are just a phone call away from creating your perfect experience! Call us!</p>
          <strong>{contact.phoneDisplay}</strong>
        </a>
        <a className="contact-card" href={`mailto:${contact.email}`}>
          <Mail aria-hidden="true" />
          <h2>Email</h2>
          <p>We're here to assist you whenever you need. Don't hesitate to reach out!</p>
          <strong>{contact.email}</strong>
        </a>
        <a
          className="contact-card"
          href={`https://maps.google.com/?q=${encodeURIComponent(address)}`}
          target="_blank"
          rel="noopener noreferrer"
        >
          <MapPin aria-hidden="true" />
          <h2>Location</h2>
          <p>{address}. Serving Cape Town &amp; the Western Cape.</p>
          <strong>View On Google Map</strong>
        </a>
      </section>
      <section className="section contact-layout" id="enquiry">
        <EnquiryForm />
        <aside className="contact-aside">
          <h2>Planning something bigger?</h2>
          <p>
            For multi-day trips, groups, or a mix of tours and transfers, send your dates and interests and we will
            prepare a tailored plan.
          </p>
          <Link className="button button-primary" to="/plan-my-trip">
            Plan My Trip
          </Link>
        </aside>
      </section>
    </>
  );
}
