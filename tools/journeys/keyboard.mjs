// Keyboard-only use of the mobile menu, hero carousel, gallery lightbox, tour filters and Explore, on a phone-sized screen.
import { base, check, finish, launch, sleep } from "./common.mjs";

const browser = await launch();

const describe = page => page.evaluate(() => { const e = document.activeElement; return `${e.tagName.toLowerCase()} "${(e.getAttribute("aria-label") || e.textContent || e.value || "").trim().slice(0, 40)}"`; });
// Press Tab until the focused element matches; fails if it is never reached (not keyboard reachable).
async function tabTo(page, test, max = 120) {
  for (let i = 0; i < max; i++) {
    await page.keyboard.press("Tab");
    if (await page.evaluate(test)) return true;
  }
  return false;
}
const focusVisible = page => page.evaluate(() => { const s = getComputedStyle(document.activeElement); return s.outlineStyle !== "none" && parseFloat(s.outlineWidth) > 0 || s.boxShadow !== "none"; });
async function open(path, width = 390) {
  const page = await browser.newPage();
  await page.setViewport({ width, height: 844, isMobile: width < 768, hasTouch: width < 768 });
  await page.emulateMediaFeatures([{ name: "prefers-reduced-motion", value: "reduce" }]);
  await page.goto(base + path, { waitUntil: "networkidle0" });
  await sleep(500);
  return page;
}

// Mobile menu.
let page = await open("/");
check("menu: button reachable by Tab", await tabTo(page, () => document.activeElement?.getAttribute("aria-controls") === "mobile-menu"));
check("menu: focus is visible", await focusVisible(page));
await page.keyboard.press("Enter"); await sleep(300);
check("menu: opens and moves focus inside", await page.evaluate(() => document.getElementById("mobile-menu")?.contains(document.activeElement)), await describe(page));
let inside = true;
for (let i = 0; i < 40; i++) { await page.keyboard.press("Tab"); inside &&= await page.evaluate(() => document.getElementById("mobile-menu")?.contains(document.activeElement)); }
check("menu: Tab stays inside the open menu", inside);
await page.keyboard.press("Escape"); await sleep(300);
check("menu: Escape closes it and returns focus to the button",
  await page.evaluate(() => !document.getElementById("mobile-menu") || getComputedStyle(document.getElementById("mobile-menu")).display === "none" || document.activeElement?.getAttribute("aria-controls") === "mobile-menu")
  && await page.evaluate(() => document.activeElement?.getAttribute("aria-controls") === "mobile-menu"), await describe(page));
await page.close();

// Hero carousel.
page = await open("/");
const label = () => page.evaluate(() => [...document.querySelectorAll(".hero-slide")].find(s => s.getAttribute("aria-hidden") === "false")?.getAttribute("aria-label"));
check("carousel: starts paused when the visitor prefers reduced motion",
  await page.evaluate(() => Boolean([...document.querySelectorAll("button")].find(b => b.getAttribute("aria-label") === "Play slideshow"))));
check("carousel: pause/play reachable by Tab, before the slide buttons",
  await tabTo(page, () => /slideshow/.test(document.activeElement?.getAttribute("aria-label") ?? "")));
check("carousel: slide buttons reachable by Tab", await tabTo(page, () => document.activeElement?.getAttribute("aria-label") === "Show slide 2"));
await page.keyboard.press("Enter"); await sleep(400);
check("carousel: Enter on a slide button shows that slide", (await label()) === "2 of 3", await label());
const hiddenFocusable = await page.evaluate(() => [...document.querySelectorAll(".hero-slide[aria-hidden=true] a, .hero-slide[aria-hidden=true] button")].filter(e => e.tabIndex >= 0).length);
check("carousel: links on hidden slides are skipped by Tab", hiddenFocusable === 0, `${hiddenFocusable} focusable`);
await page.close();

// Gallery lightbox.
page = await open("/gallery");
check("lightbox: photos reachable by Tab", await tabTo(page, () => document.activeElement?.getAttribute("aria-label")?.startsWith("Open photo")));
const opener = await describe(page);
await page.keyboard.press("Enter"); await sleep(400);
check("lightbox: Enter opens a modal dialog with focus inside", await page.evaluate(() => { const d = document.querySelector("dialog[open]"); return Boolean(d && d.contains(document.activeElement)); }));
const first = await page.evaluate(() => document.querySelector("dialog[open] img")?.getAttribute("src"));
await page.keyboard.press("ArrowRight"); await sleep(300);
const second = await page.evaluate(() => document.querySelector("dialog[open] img")?.getAttribute("src"));
check("lightbox: arrow keys move between photos", first && second && first !== second);
await page.keyboard.press("Escape"); await sleep(400);
const after = await describe(page);
check("lightbox: Escape closes and focus returns to the last photo viewed", await page.evaluate(() => !document.querySelector("dialog[open]")) && after.includes("Open photo") && after !== opener, after);
await page.close();

// Tour filters.
page = await open("/experiences");
check("filters: category buttons reachable by Tab", await tabTo(page, () => document.activeElement?.closest(".filter-row, .tour-filters, [role=group]") && document.activeElement.tagName === "BUTTON" && !/^All/.test(document.activeElement.textContent.trim())), await describe(page));
await page.keyboard.press("Enter"); await sleep(500);
check("filters: Enter applies the category and records it in the address", /category=/.test(page.url()), page.url().replace(base, ""));
check("filters: search box reachable by Tab", await tabTo(page, () => document.activeElement?.type === "search" || document.activeElement?.getAttribute("aria-label")?.toLowerCase().includes("search"), 40));
await page.keyboard.type("  zzzz-no-such-tour "); await sleep(700);
check("filters: no matches shows Clear filters, reachable by Tab",
  await tabTo(page, () => /Clear filters/.test(document.activeElement?.textContent ?? ""), 40));
await page.keyboard.press("Enter"); await sleep(500);
check("filters: Clear filters resets the address", !/category=|q=/.test(page.url()), page.url().replace(base, ""));
await page.close();

// Explore.
page = await open("/explore");
check("explore: Show all reachable by Tab", await tabTo(page, () => /^Show all \d+ in/.test(document.activeElement?.textContent ?? ""), 200), await describe(page));
const before = await page.$$eval(".explore-group:first-of-type .package-grid > *", e => e.length);
await page.keyboard.press("Enter"); await sleep(400);
const afterCount = await page.$$eval(".explore-group:first-of-type .package-grid > *", e => e.length);
check("explore: Enter shows the rest of the region", afterCount > before, `${before} → ${afterCount}`);
await page.close();

await browser.close();
finish();
