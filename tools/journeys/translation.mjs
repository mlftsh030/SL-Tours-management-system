// Google Translate: a chosen language stays across route changes and the sign-in page, and the selector still works
// after visiting a page that has no selector. Needs internet access to translate.google.com.
import { base, check, finish, launch, sleep } from "./common.mjs";

const browser = await launch();
const page = await browser.newPage();
await page.setViewport({ width: 1280, height: 900 });
const go = async path => {
  await page.evaluate(path => { history.pushState({}, "", path); dispatchEvent(new PopStateEvent("popstate")); }, path);
  await sleep(2000);
};
const selector = () => page.evaluate(() => Boolean(document.querySelector("#google_translate_element .goog-te-gadget")));
const german = () => page.evaluate(() => document.documentElement.classList.contains("translated-ltr") && document.documentElement.lang === "de");
const menuOpen = () => page.evaluate(() => [...document.querySelectorAll("iframe")]
  .some(f => /goog|VIpgJd/.test(f.className) && f.getBoundingClientRect().height > 50 && getComputedStyle(f).display !== "none"));

await page.goto(`${base}/`, { waitUntil: "networkidle0" }); await sleep(3000);
check("selector shown on the homepage", await selector());
// The cookie Google Translate sets when a visitor picks German.
await page.setCookie({ name: "googtrans", value: "/en/de", url: base });
await page.reload({ waitUntil: "networkidle0" }); await sleep(5000);
check("German applied", await german(), await page.$eval("h1", h => h.textContent));
await go("/experiences");
check("German kept after a route change", await german(), await page.$eval("h1", h => h.textContent));
await go("/login");
check("German kept on the sign-in page", await german(), await page.$eval("h1", h => h.textContent));
await go("/experiences");
check("selector back after leaving the sign-in page", await selector());
await page.goBack(); await sleep(1500); await page.goBack(); await sleep(2000);
check("selector and German kept after Back", await selector() && await german());
await page.click("#google_translate_element .goog-te-gadget-simple a"); await sleep(1500);
check("returned selector opens the language menu", await menuOpen());

await browser.close();
finish();
