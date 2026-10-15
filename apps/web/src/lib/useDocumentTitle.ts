import { useEffect } from "react";

// The page the browser loaded: its head was rendered by the server (SSI) for exactly this path.
const entryPath = typeof window === "undefined" ? "/" : window.location.pathname;

function setContent(selector: string, value: string) {
  document.querySelector(selector)?.setAttribute("content", value);
}

/**
 * Keeps the head describing the current page after in-app navigation: title, description, social titles, and the
 * canonical and og:url links (same host as the server-rendered ones, current path, no query). Structured data
 * describes the entry page only, so it is removed once the visitor has moved to another page; a direct load of that
 * page gets its own from the server.
 */
export function useDocumentTitle(title: string, description?: string) {
  useEffect(() => {
    document.title = title;
    setContent('meta[property="og:title"]', title);
    if (description) {
      setContent('meta[name="description"]', description);
      setContent('meta[property="og:description"]', description);
    }
    const path = window.location.pathname;
    const canonical = document.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (canonical) {
      const url = new URL(path, canonical.href).toString();
      canonical.href = url;
      setContent('meta[property="og:url"]', url);
    }
    if (path !== entryPath) document.querySelectorAll('script[type="application/ld+json"]').forEach(script => script.remove());
  }, [description, title]);
}
