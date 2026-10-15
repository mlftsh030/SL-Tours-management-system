import { useEffect } from "react";

/**
 * Small layout helpers for the administration screens:
 * - a detail panel that opens below a long list is scrolled into view;
 * - table cells get a data-label from their column heading so tables can stack as cards on phones.
 */
export function useAdminLayout(root: HTMLElement | null) {
  useEffect(() => {
    if (!root) return;
    const labelTables = () => root.querySelectorAll("table").forEach(table => {
      const headings = Array.from(table.querySelectorAll("thead th")).map(th => th.textContent?.trim() ?? "");
      table.querySelectorAll("tbody tr").forEach(row => Array.from(row.children).forEach((cell, index) => {
        if (headings[index] && cell.getAttribute("data-label") !== headings[index]) cell.setAttribute("data-label", headings[index]);
      }));
    });
    labelTables();
    const reduceMotion = typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const observer = new MutationObserver(mutations => {
      labelTables();
      for (const mutation of mutations) mutation.addedNodes.forEach(node => {
        if (node instanceof HTMLElement && node.matches(".admin-detail, .admin-editor") && typeof node.scrollIntoView === "function")
          node.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "start" });
      });
    });
    observer.observe(root, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, [root]);
}
