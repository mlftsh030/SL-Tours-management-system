// Loads public pages at seven widths and reports sideways scrolling, elements past the screen edge, tap targets under
// 24 x 24 px (WCAG 2.5.8) and pages without exactly one h1. Saves screenshots at 320, 768 and 1280 px when SHOTS_DIR is set.
import { mkdirSync } from "node:fs";
import { base, check, finish, launch } from "./common.mjs";

const out = process.env.SHOTS_DIR;
if (out) mkdirSync(out, { recursive: true });
const widths = [320, 375, 414, 768, 1024, 1280, 1440];
const pages = ["/", "/experiences", "/experiences/cape-peninsula-journey", "/explore", "/explore/plan", "/book-private-trip",
  "/plan-my-trip", "/gallery", "/about", "/contact", "/privacy", "/partner-with-us", "/login", "/register"];
const shotsAt = new Set([320, 768, 1280]);

const browser = await launch();
const page = await browser.newPage();
for (const path of pages) {
  for (const w of widths) {
    await page.setViewport({ width: w, height: 900, isMobile: w < 768, hasTouch: w < 768 });
    await page.goto(base + path, { waitUntil: "networkidle0", timeout: 30000 });
    const r = await page.evaluate(() => {
      const vw = document.documentElement.clientWidth;
      const wide = [...document.querySelectorAll("body *")].filter(e => {
        const b = e.getBoundingClientRect(); const cs = getComputedStyle(e);
        if (b.width === 0 || cs.visibility === "hidden" || cs.display === "none") return false;
        // Ignore content inside deliberate horizontal scrollers (sliders, tables in wrappers) and Google Translate's
        // tooltip, which it parks off-screen on purpose.
        if (e.closest("[class^=VIpgJd], [class*=' VIpgJd']") || [...e.classList].some(c => c.startsWith("VIpgJd"))) return false;
        for (let p = e.parentElement; p; p = p.parentElement) { const o = getComputedStyle(p).overflowX; if (o === "hidden" || o === "auto" || o === "scroll" || o === "clip") return false; }
        return b.right > vw + 1 || b.left < -1;
      }).slice(0, 3).map(e => `${e.tagName.toLowerCase()}.${[...e.classList].join(".")} (${Math.round(e.getBoundingClientRect().right)}px)`);
      const small = [...document.querySelectorAll("a, button, input, select, textarea, [role=button]")].filter(e => {
        const b = e.getBoundingClientRect(); if (b.width === 0 || b.height === 0) return false;
        // Inline links inside running text are exempt (WCAG 2.5.8); check standalone controls only.
        if (e.tagName === "A" && e.closest("p, li small, small, .field-hint, .privacy-note")) return false;
        // A checkbox or radio inside its label: the whole label is the target. Google's own widget is out of our hands.
        if (/checkbox|radio/.test(e.type) && e.closest("label")) return false;
        if (e.closest("#google_translate_element")) return false;
        return b.width < 24 || b.height < 24;
      }).slice(0, 3).map(e => `${e.tagName.toLowerCase()} "${(e.textContent || e.getAttribute("aria-label") || "").trim().slice(0, 25)}" ${Math.round(e.getBoundingClientRect().width)}x${Math.round(e.getBoundingClientRect().height)}`);
      return { scroll: document.documentElement.scrollWidth > vw + 1, wide, small, h1: document.querySelectorAll("h1").length };
    });
    const issues = [];
    if (r.scroll) issues.push("PAGE SCROLLS SIDEWAYS");
    if (r.wide.length) issues.push("overflow: " + r.wide.join(", "));
    if (r.small.length) issues.push("small targets: " + r.small.join(", "));
    if (r.h1 !== 1) issues.push(`${r.h1} h1 elements`);
    check(`${path} at ${w}px`, issues.length === 0, issues.join(" | "));
    if (out && shotsAt.has(w)) await page.screenshot({ path: `${out}/${path.replace(/\//g, "_") || "_"}-${w}.png`, fullPage: true });
  }
}
await browser.close();
finish();
