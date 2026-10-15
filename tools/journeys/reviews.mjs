// Homepage reviews when the Elfsight widget is blocked (ad blocker or outage) and when it loads. The second case needs
// internet access to elfsight.com.
import { base, check, finish, launch, sleep } from "./common.mjs";

const browser = await launch();
for (const blocked of [true, false]) {
  const page = await browser.newPage();
  await page.setViewport({ width: 390, height: 900, isMobile: true });
  await page.setRequestInterception(true);
  page.on("request", r => blocked && /elfsight/.test(r.url()) ? r.abort("blockedbyclient") : r.continue());
  await page.goto(`${base}/#reviews`, { waitUntil: "networkidle0" });
  await sleep(blocked ? 3000 : 10000);
  const section = await page.$eval("#reviews", e => ({
    text: e.innerText,
    google: [...e.querySelectorAll("a")].find(a => a.textContent.includes("Read them on Google"))?.href,
    toggle: [...e.querySelectorAll("button")].some(b => /all reviews/.test(b.textContent))
  }));
  if (blocked) {
    check("blocked: heading and a link to read the reviews on Google", /What Our Customers Say/.test(section.text) && section.google && !section.google.endsWith("/review"));
    check("blocked: no dead Show all reviews button", !section.toggle);
  } else {
    check("loaded: reviews shown with the Show all reviews button", section.toggle && !section.google);
  }
  await page.close();
}
await browser.close();
finish();
