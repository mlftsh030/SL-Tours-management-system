import { useEffect, useRef, useState } from "react";
import { contact } from "../../lib/contact";

const ELFSIGHT_SCRIPT = "https://static.elfsight.com/platform/platform.js";
const ELFSIGHT_APP_CLASS = "elfsight-app-a7526722-da34-4ca8-bb16-bb263f4611d4";

/** How long to wait for the widget to draw before offering the links instead (blocked, offline or slow service). */
export const WIDGET_TIMEOUT_MS = 12_000;

function loadElfsight(onFail: () => void) {
  if (document.querySelector(`script[src="${ELFSIGHT_SCRIPT}"]`)) {
    return;
  }
  const script = document.createElement("script");
  script.src = ELFSIGHT_SCRIPT;
  script.async = true;
  script.onerror = onFail;
  document.body.appendChild(script);
}

export function CustomerReviews() {
  const section = useRef<HTMLElement>(null);
  const widget = useRef<HTMLDivElement>(null);
  // "ready" once the widget has drawn reviews; "unavailable" if its script fails or nothing appears in time.
  const [widgetState, setWidgetState] = useState<"waiting" | "ready" | "unavailable">("waiting");
  const [requested, setRequested] = useState(false);
  const unavailable = () => setWidgetState(state => state === "ready" ? state : "unavailable");

  useEffect(() => {
    const target = widget.current;
    if (!target) return;
    const drawn = () => target.querySelector(`.${ELFSIGHT_APP_CLASS}`)?.childElementCount;
    const observer = new MutationObserver(() => { if (drawn()) { setWidgetState("ready"); observer.disconnect(); } });
    observer.observe(target, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    if (!requested || widgetState !== "waiting") return;
    const timer = window.setTimeout(unavailable, WIDGET_TIMEOUT_MS);
    return () => window.clearTimeout(timer);
  }, [requested, widgetState]);
  // The widget's script is large and third-party: load it only when the visitor scrolls near the reviews
  // (or straight away when the page is opened at #reviews, or the browser cannot observe scrolling).
  useEffect(() => {
    const target = section.current;
    const load = () => { setRequested(true); loadElfsight(unavailable); };
    if (!target || typeof IntersectionObserver === "undefined" || window.location.hash === "#reviews") { load(); return; }
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) { load(); observer.disconnect(); }
    }, { rootMargin: "600px 0px" });
    observer.observe(target);
    return () => observer.disconnect();
  }, []);
  const [expanded, setExpanded] = useState(false);

  return (
    <section className="reviews-band" id="reviews" aria-label="Customer reviews" ref={section}>
      <div className="reviews-inner">
        {/* The Elfsight Google Reviews widget renders its own "What Our Customers Say" heading. */}
        <div id="reviews-widget" ref={widget} hidden={widgetState === "unavailable"}
          className={expanded ? "reviews-widget" : "reviews-widget reviews-widget-collapsed"}>
          <div className={ELFSIGHT_APP_CLASS} data-elfsight-app-lazy />
        </div>
        {widgetState === "unavailable" && <div className="reviews-fallback">
          <h2>What Our Customers Say</h2>
          <p>Our reviews could not be shown here just now.{" "}
            <a href={contact.googleProfile} target="_blank" rel="noopener noreferrer">Read them on Google</a>.</p>
        </div>}
        {widgetState === "ready" && <button
          className="button button-quiet reviews-toggle"
          type="button"
          aria-expanded={expanded}
          aria-controls="reviews-widget"
          onClick={() => setExpanded((value) => !value)}
        >
          {expanded ? "Show fewer reviews" : "Show all reviews"}
        </button>}
        <noscript>
          <p>
            Read our reviews on <a href={contact.googleProfile}>Google</a> and{" "}
            <a href={contact.tripadvisorReview}>Tripadvisor</a>.
          </p>
        </noscript>
        <div className="rate-actions">
          <a
            className="button button-primary"
            href={contact.tripadvisorReview}
            target="_blank"
            rel="noopener noreferrer"
          >
            Rate us on Tripadvisor
          </a>
          <a className="button button-navy" href={contact.googleReview} target="_blank" rel="noopener noreferrer">
            Rate us on Google
          </a>
        </div>
      </div>
    </section>
  );
}
