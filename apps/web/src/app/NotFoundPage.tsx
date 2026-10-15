import { useEffect } from "react";
import { Link } from "react-router-dom";
import { useDocumentTitle } from "../lib/useDocumentTitle";

/** Unknown public paths. Nginx already answers them with HTTP 404; this keeps the page out of search results after client navigation too. */
export function NotFoundPage() {
  useDocumentTitle("Page not found | SL Tours", "This page could not be found.");
  useEffect(() => {
    const robots = document.createElement("meta");
    robots.name = "robots";
    robots.content = "noindex";
    document.head.appendChild(robots);
    return () => robots.remove();
  }, []);

  return (
    <section className="section not-found">
      <h1>Page not found</h1>
      <p>The page you asked for does not exist or has moved.</p>
      <ul className="not-found-links">
        <li><Link to="/">Home</Link></li>
        <li><Link to="/experiences">Our Tours</Link></li>
        <li><Link to="/explore">Things to Do in Cape Town</Link></li>
        <li><Link to="/contact">Contact Us</Link></li>
      </ul>
    </section>
  );
}
