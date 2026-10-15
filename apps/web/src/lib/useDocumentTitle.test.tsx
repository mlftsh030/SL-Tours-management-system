import { render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { useDocumentTitle } from "./useDocumentTitle";

function Page({ title }: { title: string }) { useDocumentTitle(title, `${title} description`); return null; }

afterEach(() => { document.head.innerHTML = ""; window.history.replaceState(null, "", "/"); });

describe("page metadata after in-app navigation", () => {
  it("moves canonical and social tags to the current page and drops the entry page's structured data", () => {
    document.head.innerHTML = `<meta name="description" content="Home"><meta property="og:title" content="Home">
      <meta property="og:description" content="Home"><meta property="og:url" content="https://sl-tours.com/">
      <link rel="canonical" href="https://sl-tours.com/"><script type="application/ld+json">{"@type":"TravelAgency"}</script>`;
    window.history.pushState(null, "", "/experiences/cape-peninsula-journey?ref=x");
    render(<Page title="Cape Peninsula | SL Tours" />);
    expect(document.title).toBe("Cape Peninsula | SL Tours");
    expect(document.querySelector('link[rel="canonical"]')).toHaveAttribute("href", "https://sl-tours.com/experiences/cape-peninsula-journey");
    expect(document.querySelector('meta[property="og:url"]')).toHaveAttribute("content", "https://sl-tours.com/experiences/cape-peninsula-journey");
    expect(document.querySelector('meta[property="og:title"]')).toHaveAttribute("content", "Cape Peninsula | SL Tours");
    expect(document.querySelector('meta[name="description"]')).toHaveAttribute("content", "Cape Peninsula | SL Tours description");
    expect(document.querySelector('script[type="application/ld+json"]')).toBeNull();
  });

  it("keeps the structured data on the page the server rendered", () => {
    document.head.innerHTML = `<script type="application/ld+json">{"@type":"TravelAgency"}</script>`;
    render(<Page title="Home" />);
    expect(document.querySelector('script[type="application/ld+json"]')).not.toBeNull();
  });
});
