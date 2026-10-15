// The separate /book/:slug page: starts at the tour's minimum group, refuses fewer guests, and confirms a request
// (not a booking) with a reference and an estimated total.
import { base, check, daysAhead, fill, finish, launch, run, sleep } from "./common.mjs";

const browser = await launch();
const page = await browser.newPage(); await page.setViewport({ width: 390, height: 900, isMobile: true });
await page.goto(`${base}/book/cape-peninsula-journey`, { waitUntil: "networkidle0" }); await sleep(800);
const guests = () => page.evaluate(() => [...document.querySelectorAll("label")].find(l => l.querySelector("span")?.textContent === "Guests")?.querySelector("input").value);
check("opens at the tour's minimum group", await guests() === "5", await guests());
check("explains the minimum and offers a private quote", await page.evaluate(() => /Minimum 5 guests\. Fewer\?/.test(document.body.textContent)));
await fill(page, "form", "Date", daysAhead(14)); await fill(page, "form", "Guests", "3");
await fill(page, "form", "Name", "Journey Book"); await fill(page, "form", "Email", `journey-book-${run}@example.test`);
await fill(page, "form", "Phone", "+27 00 000 0002");
await page.click("form button[type=submit]"); await sleep(1200);
const text = await page.evaluate(() => document.body.textContent);
const underflow = await page.evaluate(() => [...document.querySelectorAll("label")].find(l => l.querySelector("span")?.textContent === "Guests").querySelector("input").validity.rangeUnderflow);
check("too few guests is refused before sending", underflow && !/Booking request received/i.test(text), "browser validation: below the minimum of 5");
await fill(page, "form", "Guests", "6");
await page.click("form button[type=submit]");
await page.waitForFunction(() => /Booking request received/i.test(document.body.textContent), { timeout: 15000 });
const done = await page.evaluate(() => document.body.textContent);
check("receipt shows a reference", /[A-Z]{2,4}-\d{4}-[A-Z0-9]+/.test(done), done.match(/[A-Z]{2,4}-\d{4}-[A-Z0-9]+/)?.[0]);
check("says it is a request that SL Tours will confirm, with an estimated total",
  /Booking request received/.test(done) && /review your request and confirm availability/.test(done) && /Estimated total/.test(done));
await browser.close();
finish();
