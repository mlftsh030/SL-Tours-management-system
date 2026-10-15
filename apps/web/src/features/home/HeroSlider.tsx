import { ChevronLeft, ChevronRight, Pause, Play } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useSiteImages } from "../../lib/useSiteImages";

interface Slide { image: string; title: string; subtitle: string; text: string; alt?: string; link?: string; cta?: string }

// Fallback slides shown until an administrator activates HOME_HERO images. Every claim here must match the linked tour's
// inclusions (V22): no promotion is advertised unless SL Tours has approved its terms, eligibility and dates.
const builtInSlides: Slide[] = [
  {
    image: "/assets/home/slider-1.webp",
    title: "Exceptional",
    subtitle: "Holiday in Cape Town",
    text: "With SL Tours where we offer excellent opportunities to explore South Africa"
  },
  {
    image: "/assets/home/slider-3.webp",
    title: "Cape Town City & Heritage",
    subtitle: "Bo-Kaap, District Six and the Company's Garden",
    text: "A guided city tour with hotel pickup and drop-off",
    link: "/experiences/cape-town-essentials",
    cta: "See the City Tour"
  },
  {
    image: "/assets/home/slider-2.webp",
    title: "Cape Winelands Tour",
    subtitle: "Stellenbosch and Franschhoek",
    text: "Two wine tastings, cheese pairing and lunch included",
    link: "/experiences/cape-winelands-escape",
    cta: "See the Winelands Tour"
  }
];

const SLIDE_INTERVAL_MS = 6000;

function prefersReducedMotion() {
  return typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function HeroSlider() {
  const managed = useSiteImages("HOME_HERO");
  // Built-in slides stay until an administrator activates replacement images.
  const slides: Slide[] = managed.length
    ? managed.map(image => ({ image: image.url, title: image.title ?? "", subtitle: image.subtitle ?? "", text: image.body ?? "", alt: image.altText }))
    : builtInSlides;
  const [current, setActive] = useState(0);
  const active = current % slides.length;
  // Hover and focus pause temporarily; the button pauses until pressed again (WCAG 2.2.2). Reduced motion starts paused.
  const [paused, setPaused] = useState(false);
  const [stopped, setStopped] = useState(prefersReducedMotion);
  // Photos load for the slide on show and the next one, then stay loaded: the first visit downloads one photo less.
  const [shown, setShown] = useState<ReadonlySet<number>>(() => new Set([0]));
  useEffect(() => setShown(previous => previous.has(active) ? previous : new Set(previous).add(active)), [active]);
  const loads = (index: number) => shown.has(index) || index === (active + 1) % slides.length;

  useEffect(() => {
    if (paused || stopped) {
      return;
    }
    const timer = window.setTimeout(() => setActive((index) => (index + 1) % slides.length), SLIDE_INTERVAL_MS);
    return () => window.clearTimeout(timer);
  }, [active, paused, stopped, slides.length]);

  const go = (offset: number) => setActive((index) => (index + offset + slides.length) % slides.length);

  return (
    <section
      className="hero-slider"
      aria-roledescription="carousel"
      aria-label="SL Tours highlights"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      {slides.map((slide, index) => (
        <div
          key={slide.image}
          className={index === active ? "hero-slide hero-slide-active" : "hero-slide"}
          role="group"
          aria-roledescription="slide"
          aria-label={`${index + 1} of ${slides.length}`}
          aria-hidden={index !== active}
        >
          <div className="hero-slide-image" style={loads(index) ? { backgroundImage: `url(${slide.image})` } : undefined}
            {...(slide.alt ? { role: "img", "aria-label": slide.alt } : { "aria-hidden": true })} />
          <div className="hero-slide-content">
            {index === 0 ? <h1>{slide.title}</h1> : <p className="hero-slide-title">{slide.title}</p>}
            <p className="hero-slide-subtitle">{slide.subtitle}</p>
            {slide.text && <p className="hero-slide-text">{slide.text}</p>}
            <Link className="button button-navy" to={slide.link ?? "/experiences"} tabIndex={index === active ? 0 : -1}>
              {slide.cta ?? "Book a Tour"}
            </Link>
          </div>
        </div>
      ))}
      <button
        className="slider-arrow slider-arrow-prev"
        type="button"
        onClick={() => go(-1)}
        aria-label="Previous slide"
      >
        <ChevronLeft aria-hidden="true" />
      </button>
      <button className="slider-arrow slider-arrow-next" type="button" onClick={() => go(1)} aria-label="Next slide">
        <ChevronRight aria-hidden="true" />
      </button>
      <button className="slider-toggle" type="button" onClick={() => setStopped(value => !value)}
        aria-label={stopped ? "Play slideshow" : "Pause slideshow"}>
        {stopped ? <Play aria-hidden="true" /> : <Pause aria-hidden="true" />}
      </button>
      <div className="slider-dots">
        {slides.map((slide, index) => (
          <button
            key={slide.image}
            type="button"
            className={index === active ? "slider-dot slider-dot-active" : "slider-dot"}
            onClick={() => setActive(index)}
            aria-label={`Show slide ${index + 1}`}
            aria-current={index === active}
          />
        ))}
      </div>
    </section>
  );
}
