// Tour enquiries (standard, airport, custom): validation, a network failure that keeps the details, the pending state,
// a retry with the same idempotency key, an edited retry with a new key, and the receipt.
import { base, check, daysAhead, fill, finish, launch, run, sleep } from "./common.mjs";

const cases = [
  { slug: "cape-peninsula-journey", kind: "standard", fields: [["Group size", "6"], ["Preferred date", daysAhead(20)], ["Pickup location", "Test hotel, Sea Point"]] },
  { slug: "airport-transfer", kind: "airport", fields: [["Passengers", "2"], ["Flight number", "BA59"], ["Flight date", daysAhead(15)], ["Landing time", "07:40"],
    ["Destination", "1 Test Road, Camps Bay"], ["Luggage", "2 large suitcases"]] },
  { slug: "private-custom-tour", kind: "custom", fields: [["Group size", "3"], ["Start date", daysAhead(30)], ["End date", daysAhead(32)],
    ["Interests", "Cape Point and wine farms"]] }
];

const browser = await launch();
for (const c of cases) {
  const page = await browser.newPage();
  await page.setViewport({ width: 390, height: 900, isMobile: true, hasTouch: true });
  const posts = [];
  let mode = "fail";
  await page.setRequestInterception(true);
  page.on("request", req => {
    if (req.method() === "POST" && req.url().endsWith("/api/v1/enquiries")) {
      posts.push(JSON.parse(req.postData()));
      if (mode === "fail") return req.abort("connectionfailed");
      if (mode === "slow") return setTimeout(() => req.continue(), 1500);
    }
    req.continue();
  });
  await page.goto(`${base}/experiences/${c.slug}#enquire`, { waitUntil: "networkidle0" });
  const submit = () => page.click("#enquire button[type=submit]");
  const failed = () => page.waitForFunction(() => document.querySelector("#enquire")?.textContent.includes("We could not send your enquiry"), { timeout: 10000 });

  await submit(); await sleep(300);
  const invalid = await page.$$eval("#enquire :invalid", els => els.filter(e => e.tagName !== "FORM").length);
  check(`${c.kind}: empty form is blocked by validation`, posts.length === 0 && invalid > 0, `${invalid} invalid fields`);

  const email = `journey-${c.kind}-${run}@example.test`;
  await fill(page, "#enquire", "Your name", `Journey Test ${c.kind}`);
  await fill(page, "#enquire", "Email address", email);
  await fill(page, "#enquire", "WhatsApp number", "+27 00 000 0000");
  for (const [label, value] of c.fields) await fill(page, "#enquire", label, value);

  await submit(); await failed();
  check(`${c.kind}: network failure shows an error and keeps the details`, await page.$eval("#enquire input[type=email]", e => e.value) === email);
  await submit();
  // The error from the first attempt is still shown, so wait for the second request itself.
  for (let i = 0; i < 50 && posts.length < 2; i++) await sleep(100);
  await sleep(300);
  check(`${c.kind}: an unchanged retry reuses the idempotency key`, posts[1]?.idempotencyKey === posts[0].idempotencyKey);
  await fill(page, "#enquire", "Special requests", "Two children.");

  mode = "slow";
  await submit();
  await page.waitForFunction(() => document.querySelector("#enquire button[type=submit]")?.textContent.includes("Sending"), { timeout: 3000 });
  check(`${c.kind}: pending state disables the button`, await page.$eval("#enquire button[type=submit]", b => b.disabled));
  await page.click("#enquire button[type=submit]").catch(() => {});
  await page.waitForFunction(() => document.querySelector("#enquire [role=status]"), { timeout: 15000 });
  const receipt = await page.$eval("#enquire [role=status]", e => e.textContent);
  const reference = receipt.match(/reference is ([A-Z0-9-]+)/i)?.[1];
  check(`${c.kind}: receipt shows a reference and says it is not a booking`, reference && receipt.includes("not a confirmed booking"), reference);
  check(`${c.kind}: edited details are sent as a new submission, the double click sent nothing`,
    posts.length === 3 && posts[2].idempotencyKey !== posts[0].idempotencyKey && posts[2].message === "Two children.", `${posts.length} requests`);
  await page.close();
}
await browser.close();
finish();
