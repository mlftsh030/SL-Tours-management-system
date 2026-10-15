// Customer account through the real UI and SMTP (Mailpit): a guest enquiry, registration, guest records hidden until the
// address is verified, verification links them, duplicate registration, password reset signs out other sessions, and
// emailed links work once only.
import { base, check, finish, launch, mailpit, run, sleep } from "./common.mjs";

const email = `journey-account-${run}@example.test`;
const password = "Journey-Test-2026!a";
const newPassword = "Journey-Test-2026!b";

async function linkFromMail(path, after) {
  for (let i = 0; i < 30; i++) {
    const list = await (await fetch(`${mailpit}/api/v1/search?query=${encodeURIComponent("to:" + email)}`)).json();
    for (const m of list.messages ?? []) {
      if (new Date(m.Created) < after) continue;
      const full = await (await fetch(`${mailpit}/api/v1/message/${m.ID}`)).json();
      const link = (full.Text + full.HTML).match(new RegExp(`https?://[^\\s"'<>]+${path}\\?token=[A-Za-z0-9_-]+`))?.[0];
      if (link) return { link, subject: m.Subject };
    }
    await sleep(500);
  }
  throw new Error(`No email to ${email} with ${path}`);
}
const field = async (page, name, value) => { const el = await page.$(`input[name=${name}]`); await el.click({ clickCount: 3 }); await el.type(value); };
const text = page => page.evaluate(() => document.body.innerText);
async function enquiriesPage(page) { await page.goto(`${base}/account/enquiries`, { waitUntil: "networkidle0" }); await sleep(800); return text(page); }
async function signIn(page, pass) {
  await page.goto(`${base}/login`, { waitUntil: "networkidle0" });
  await field(page, "email", email); await field(page, "password", pass);
  await Promise.all([page.click("form button[type=submit]"), sleep(1500)]);
}

const browser = await launch();
const page = await (await browser.createBrowserContext()).newPage();

// A guest enquiry made before the account exists.
await page.goto(`${base}/`, { waitUntil: "networkidle0" });
const guestRef = await page.evaluate(async email => {
  const csrf = await (await fetch("/api/v1/auth/csrf", { credentials: "include" })).json();
  const response = await fetch("/api/v1/enquiries", { method: "POST", credentials: "include",
    headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
    body: JSON.stringify({ idempotencyKey: crypto.randomUUID(), customerName: "Journey Account", customerEmail: email, message: "Guest enquiry before an account", enquiryType: "GENERAL" }) });
  return (await response.json()).reference;
}, email);
check("guest enquiry stored", /^SLE-/.test(guestRef ?? ""), guestRef);

const started = new Date(Date.now() - 2000);
await page.goto(`${base}/register`, { waitUntil: "networkidle0" });
await page.click("form button[type=submit]"); await sleep(500);
const errors = await page.$$eval("form [aria-invalid=true]", e => e.length);
check("register: empty form shows field errors without sending", errors >= 3, `${errors} fields marked invalid`);
await field(page, "name", "Journey Account"); await field(page, "email", email);
await field(page, "password", password); await field(page, "passwordConfirmation", password);
await Promise.all([page.click("form button[type=submit]"), sleep(2500)]);

let seen = await enquiriesPage(page);
check("account: unverified address shows the confirm banner", /confirm your email/i.test(seen));
check("account: guest enquiry hidden until the address is confirmed", !seen.includes(guestRef));

const verify = await linkFromMail("/verify-email", started);
check("email: verification delivered through SMTP, linking to this site", verify.link.startsWith(base), verify.subject);
await page.goto(verify.link, { waitUntil: "networkidle0" }); await sleep(1500);
seen = await text(page);
check("verify: confirms the address and reports linked records", /is confirmed/.test(seen) && /We found and added/.test(seen), seen.match(/We found[^.]*\./)?.[0]);
check("account: guest enquiry now listed", (await enquiriesPage(page)).includes(guestRef), guestRef);
await page.goto(verify.link, { waitUntil: "networkidle0" }); await sleep(1200);
check("verify: link works once only", !/is confirmed/.test(await text(page)));

// A second, signed-out browser.
const other = await (await browser.createBrowserContext()).newPage();
await other.goto(`${base}/register`, { waitUntil: "networkidle0" });
for (const [n, v] of [["name", "Someone Else"], ["email", email], ["password", password], ["passwordConfirmation", password]]) await field(other, n, v);
await Promise.all([other.click("form button[type=submit]"), sleep(2000)]);
check("register: existing address refused, with sign-in and reset links",
  /already exists/i.test(await text(other)) && Boolean(await other.$("a[href='/forgot-password']")));

const resetStarted = new Date(Date.now() - 2000);
await other.goto(`${base}/forgot-password`, { waitUntil: "networkidle0" });
await field(other, "email", email);
await Promise.all([other.click("form button[type=submit]"), sleep(2000)]);
check("reset: request gives the same answer whether or not the account exists", /If .* has an SL Tours account/.test(await text(other)));
const reset = await linkFromMail("/reset-password", resetStarted);
await other.goto(reset.link, { waitUntil: "networkidle0" });
const fields = await other.$$("input[type=password]");
for (const f of fields) await f.type(newPassword);
await Promise.all([other.click("form button[type=submit]"), sleep(2000)]);
check("reset: confirms and says every session was signed out", fields.length === 2 && /signed out everywhere/.test(await text(other)));

seen = await enquiriesPage(page);
check("reset: the other signed-in browser is signed out", !seen.includes(guestRef) && page.url().includes("/login"));
await signIn(page, password);
check("sign-in: old password refused", !(await enquiriesPage(page)).includes(guestRef));
await signIn(page, newPassword);
check("sign-in: new password works and the linked enquiry remains", (await enquiriesPage(page)).includes(guestRef));
await other.goto(reset.link, { waitUntil: "networkidle0" });
for (const f of await other.$$("input[type=password]")) await f.type("Journey-Test-2026!c");
await Promise.all([other.click("form button[type=submit]").catch(() => {}), sleep(1500)]);
check("reset: link works once only", !/signed out everywhere/.test(await text(other)));

await browser.close();
finish();
