// Shared helpers for the browser journey checks. See README.md.
import puppeteer from "puppeteer-core";

export const base = (process.env.BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
export const mailpit = (process.env.MAILPIT_URL ?? "http://localhost:8025").replace(/\/$/, "");
/** Distinguishes the records of one run: test addresses are journey-<what>-<run>@example.test. */
export const run = process.env.RUN_ID ?? Date.now().toString(36);
export const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

export function launch() {
  return puppeteer.launch({ executablePath: process.env.CHROME_PATH ?? "/usr/bin/google-chrome", args: ["--no-sandbox", "--headless=new"] });
}

const results = [];
export function check(name, ok, detail = "") {
  results.push(Boolean(ok));
  console.log(`${ok ? "ok   " : "FAIL "} ${name}${detail ? " — " + detail : ""}`);
}
/** Prints the summary and sets the exit code, so a failed check fails the command. */
export function finish() {
  const passed = results.filter(Boolean).length;
  console.log(`${passed}/${results.length} passed`);
  process.exitCode = passed === results.length ? 0 : 1;
}

/** Sets a form field by its visible label text, as a person would see it. Date, time and number inputs are set
 *  directly because typing into them depends on the browser's locale. */
export async function fill(page, scope, label, value) {
  const handle = await page.evaluateHandle((s, l) => [...document.querySelectorAll(`${s} label`)]
    .find(x => x.querySelector("span")?.textContent.trim().startsWith(l))?.querySelector("input, textarea, select"), scope, label);
  const el = handle.asElement();
  if (!el) throw new Error(`No field "${label}" in ${scope}`);
  const kind = await el.evaluate(e => `${e.tagName}:${e.type}`);
  if (kind.startsWith("SELECT")) await el.select(value);
  else if (/:(date|time|number)$/.test(kind)) await el.evaluate((e, v) => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set.call(e, v);
    e.dispatchEvent(new Event("input", { bubbles: true }));
  }, value);
  else { await el.click({ clickCount: 3 }); await el.type(value); }
}

/** A date this many days ahead, as YYYY-MM-DD. */
export const daysAhead = days => new Date(Date.now() + days * 864e5).toISOString().slice(0, 10);
