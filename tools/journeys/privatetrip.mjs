// Private trips: search, Cape Town time, stale results after a change, a booking request that meets a price change
// (409) and then succeeds with the same key, and the custom-quote fallback. Needs priced vehicles (sample rates in the
// dev profile, or real rates).
import { base, check, daysAhead, fill, finish, launch, run, sleep } from "./common.mjs";

const date = daysAhead(12);
const text = page => page.evaluate(() => document.body.textContent);
const clickText = (page, selector, label) =>
  page.evaluate((s, l) => [...document.querySelectorAll(s)].find(b => b.textContent.trim().startsWith(l))?.click(), selector, label);

async function search(page) {
  await page.goto(`${base}/book-private-trip`, { waitUntil: "networkidle0" });
  await fill(page, "#trip-form", "Date", date); await fill(page, "#trip-form", "Start time", "09:00");
  await fill(page, "#trip-form", "Passengers", "3");
  await fill(page, "#trip-form", "Pickup location", "Test hotel, Green Point");
  await fill(page, "#trip-form", "Destination", "Chapman's Peak and Cape Point");
  await page.click("#trip-form button[type=submit]");
  // textContent, not innerText: the eyebrow is displayed in capitals.
  await page.waitForFunction(() => document.body.textContent.includes("Available for your trip"), { timeout: 15000 });
  await sleep(800);
}

const browser = await launch();
let page = await browser.newPage();
await page.setViewport({ width: 390, height: 900, isMobile: true, hasTouch: true });
const posts = [];
let conflict = true;
await page.setRequestInterception(true);
page.on("request", req => {
  if (req.method() === "POST" && req.url().endsWith("/api/v1/private-trips/requests")) {
    posts.push(JSON.parse(req.postData()));
    if (conflict) { conflict = false; return req.respond({ status: 409, contentType: "application/json",
      body: JSON.stringify({ status: 409, code: "CONFLICT", message: "The price for this trip has changed.", errors: {} }) }); }
  }
  req.continue();
});

await search(page);
const heading = await page.$eval(".private-trip-heading h2", h => h.textContent);
check("search: heading shows the entered time as Cape Town time", heading.includes("09:00") && heading.includes("Cape Town time"), heading);
check("search: vehicles offered", (await page.$$(".vehicle-grid button")).length > 0);

await fill(page, "#trip-form", "Passengers", "4"); await sleep(300);
const stale = await text(page);
check("stale: changing passengers withdraws the old results", /You changed your trip details/.test(stale) && !/Request this trip/.test(stale));
await page.click("#trip-form button[type=submit]");
await page.waitForFunction(() => !document.body.textContent.includes("You changed your trip details") && document.querySelector(".vehicle-grid button"), { timeout: 15000 });
await sleep(800);

await clickText(page, ".vehicle-grid button", "Choose this vehicle"); await sleep(500);
if (!(await text(page)).includes("Request this trip")) {
  check("book: a priced vehicle is offered (sample or real rates needed)", false);
} else {
  const checkout = ".private-trip-checkout form";
  await fill(page, checkout, "Name", `Journey Trip ${run}`);
  await fill(page, checkout, "Email", `journey-trip-${run}@example.test`);
  await fill(page, checkout, "Mobile number", "+27 00 000 0001");
  await clickText(page, ".private-trip-checkout button[type=submit]", "Request this trip");
  await page.waitForFunction(() => document.body.textContent.includes("price for this trip has changed"), { timeout: 10000 });
  check("conflict: explains the change, refreshes the options and keeps the details",
    /options above have been refreshed/.test(await text(page)) && (await page.$eval(".private-trip-checkout input[type=email]", e => e.value)).includes("journey-trip"));
  await sleep(2000); // the options refresh before the button is enabled again
  await clickText(page, ".private-trip-checkout button[type=submit]", "Request this trip");
  await page.waitForFunction(() => /SLT-\d{4}-/.test(document.body.textContent), { timeout: 15000 });
  const done = await text(page);
  check("book: receipt shows a reference and that it is not yet confirmed", /not yet a confirmed booking/.test(done), done.match(/SLT-\d{4}-[A-Z0-9]+/)?.[0]);
  check("book: the retry at the new price kept its key", posts.length === 2 && posts[0].idempotencyKey === posts[1].idempotencyKey, `${posts.length} requests`);
}
await page.close();

page = await browser.newPage();
await search(page);
await clickText(page, ".vehicle-grid button", "Choose this vehicle"); await sleep(400);
await clickText(page, "button", "My itinerary needs a custom quote"); await sleep(400);
check("quote: says nothing is booked yet", /Nothing is booked yet/.test(await text(page)));
await fill(page, ".private-trip-checkout form", "Name", `Journey Quote ${run}`);
await fill(page, ".private-trip-checkout form", "Email", `journey-quote-${run}@example.test`);
await clickText(page, ".private-trip-checkout button[type=submit]", "Send quote request");
await page.waitForFunction(() => /SLE-\d{4}-/.test(document.body.textContent), { timeout: 15000 });
check("quote: request stored with an enquiry reference", true, (await text(page)).match(/SLE-\d{4}-[A-Z0-9]+/)?.[0]);

await browser.close();
finish();
