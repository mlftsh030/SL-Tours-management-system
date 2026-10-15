# Learning log

Newest first. See [README](README.md) for the entry format.

## 2026-10-09: Walking the journeys a customer actually takes

**What happened:** Phase 4 of the review: driving the site in a real browser (Puppeteer) against the local stack:
enquiries, bookings, private trips, accounts with real emails in Mailpit, translation, reviews, keyboard use and access
rules. Four real defects turned up that the unit tests had not caught.

**What it means:**
- *Unit tests check pieces; journeys check what a person experiences.* The language selector worked on every page
  on its own, but vanished after going to the sign-in page and back, because Google allows only one widget per page.
- *Idempotency keys protect against duplicates, but can hide edits.* Reusing a key is right for an identical retry
  and wrong once the customer changed something: the server answered with the first enquiry and the edit was lost.
- *Plan for third parties failing.* Ad blockers often block review widgets. Without a fallback, visitors saw an empty
  box and a button that did nothing.
- *Wall-clock times are not instants.* "09:00 in Cape Town" must not go through the visitor's time zone: in New
  York on 8 March 2026, 02:30 does not exist and the browser showed 03:30.
- *A test can be wrong too.* Several failures were the script's fault (capitalised text read with `innerText`, a
  check that ran before the request it waited for). Check the evidence before changing the application.
- *Test the rollback, not just the release.* The previous API was started against the migrated database to prove
  that going back needs no restore.
- *A background job's "exit code 0" can lie* if the command ends with `echo`: the Docker build had failed.

**What we did:** Fixes and the reusable checks in [tools/journeys](../../tools/journeys/README.md); status of every
requirement in [Release readiness](../operations/RELEASE_READINESS.md).

**Commands learned:**
- `git archive --format=tar <commit>:apps/api | docker build -t name -`: build an image from an old commit without
  checking it out.
- `TZ=America/New_York node -e '...'`: run JavaScript as if the computer were in another time zone.
- Mailpit's API (`/api/v1/search?query=to:...`): read test emails from a script.

**Still unsure about:**
- How to run the browser journeys in CI (Chrome and the Docker stack on the GitHub runner).
- How Flyway decides to accept a database that is newer than the application.

## 2026-10-09: Measuring speed and checking layouts honestly

**What happened:** Phase 3 of the review fixes: shareable filters, Cape Town time, clearer journey labels, Explore
"Show all", compression and caching, and an accessibility pass. A full frontend test run failed four tests that pass
on their own.

**What it means:**
- *Measure before changing.* Lighthouse ran on the unchanged build first, so the before/after table in
  [Performance](../operations/PERFORMANCE.md) compares like with like. A smaller script is not proof of a faster
  site; the measurement is.
- *Lab numbers are not real visitors.* Lighthouse simulates a slow phone. Real Core Web Vitals need the live domain.
- *Caching depends on file names.* Built scripts have a content hash in their name, so browsers may keep them for a
  year. Photos keep their names when replaced, so they are cached for a day only. HTML is always revalidated.
- *An Nginx trap:* a location with its own `add_header` loses every header from the server block, including the
  security headers. Each such location now includes `security-headers.conf` again.
- *Automated checks need interpreting.* The layout script flagged 91 page/width combinations. Most were Google
  Translate's hidden tooltip or checkboxes inside clickable labels, which are fine. The real problems were short links
  and 14px slider dots; those were fixed.
- *A test that fails only when the machine is busy is a timeout, not a bug.* The four failures each took about 5
  seconds, Vitest's default limit, while Maven was compiling. The limit is now 15 seconds.

**What we did:** See the Phase 3 commit. Lighthouse homepage score 30 → 60, bytes transferred 4048 → 2165 KiB.

**Commands learned:**
- `npx lighthouse@12 URL --form-factor=mobile --only-categories=performance`: measure a page's speed.
- `curl -H 'Accept-Encoding: gzip' -w '%{size_download}' -o /dev/null URL`: bytes actually sent with compression.
- `curl -D - -o /dev/null URL`: show response headers only.

**Still unsure about:**
- Why Chrome downloads lazy images that are still far below the screen (its distance threshold).
- When Brotli compression is worth adding next to gzip.

## 2026-10-08: Reproduce first, then fix

**What happened:** Phase 2 of the review fixes. The private-trip form could submit an old search's date and price with
new pickup details; vehicles were swapped by category without asking; colours failed contrast checks.

**What it means:**
- *Write the failing test before the fix.* The stale-search test failed first ("Request this trip" was still offered
  after the duration changed), which proved the bug was real and that the fix actually addresses it.
- *Derived state beats synchronised state.* Instead of clearing the selection in many places, the form compares the
  current fields with the last search; any difference makes the results "stale" automatically.
- *Make relationships explicit in the data.* "A Staria was requested" and "this van is a Staria" were not linked, so
  the code guessed by category. Migration V26 links a fleet vehicle to its model; only exact matches are pre-selected.
- *Contrast is measured, not judged by eye.* WCAG asks 4.5:1 for normal text. White on #e85e34 is 3.45:1; #d14318 is
  4.64:1 with the same hue.
- *A privacy notice must describe what the code does.* Checking the code corrected two draft claims (only account
  emails are sent; only drivers, not guides, see customer names).

**Still unsure about:** How to test layouts automatically at many screen widths (Playwright).

## 2026-10-08: HTTPS, redirects and real 404 pages

**What happened:** An external review of the preview found launch blockers: a homepage promotion that contradicted the
tour's exclusions, sample prices, a preview open to search engines, no HTTPS, and old sl-tours.com addresses that would
break. Each was reproduced in the source first, then fixed and checked against the full Docker stack.

**What it means:**
- *Status codes matter, not just what the page shows.* The site showed "Page not found" but answered HTTP 200, so
  search engines could treat missing pages as real ones. Nginx now asks the API whether a page exists
  (`auth_request`) and answers 404 when it does not.
- *A 301 redirect* tells browsers and search engines a page moved permanently, so the old address's ranking passes to
  the new one.
- *Behind a proxy, the server sees the proxy, not the visitor.* With Caddy in front, every visitor would have shared
  Caddy's IP address, and one person could have used up everyone's sign-in attempts. Nginx now takes the visitor's
  address from `X-Forwarded-For`, but only when the request really comes from Caddy, so nobody can fake it.
- *Testing found a real bug:* Nginx built redirect addresses with its internal port 80, which is wrong behind Docker's
  `8080:80` mapping. `absolute_redirect off` makes redirects relative.
- *A flaky test* failed about 1 time in 100: it checked that a random token did not contain the digits "21". Flaky tests
  teach people to ignore failures, so it was fixed to check what it meant.
- *Make dangerous settings explicit.* Production refuses to start unless search indexing is set to true or false, so
  a staging "noindex" cannot silently reach the public site, or the reverse.

**Commands learned:**
- `curl -s -o /dev/null -w '%{http_code} %{redirect_url}' URL`: show only the status code and redirect target.
- `nginx -t`: check an Nginx configuration without starting it.
- `docker compose -f a.yml -f b.yml config`: show the merged result of several Compose files.
- `curl --cacert root.crt`: trust a specific certificate authority instead of switching verification off with `-k`.

**Still unsure about:** How Let's Encrypt proves domain ownership (the HTTP-01 challenge on port 80).

## 2026-10-07: First CI pipeline and the vulnerabilities it found

**What happened:** We added a GitHub Actions workflow ([ci.yml](../../.github/workflows/ci.yml)) with four jobs:
frontend checks, API tests, a security scan and a Docker build. Before committing it, every step was run locally the
same way CI runs it. The security scanner (Trivy) failed immediately: the API depended on library versions with
known vulnerabilities, including three CRITICAL flaws in Tomcat, the web server built into Spring Boot. Two of them
bypass authentication; one is specific to form login, which is how this application signs people in.

**What it means:**
- Our code can be correct and still be vulnerable through its dependencies. Most of the code that runs is libraries,
  and their flaws are published as CVEs (numbered vulnerability reports).
- *Direct* dependencies are listed in `pom.xml`; *indirect* (transitive) ones are pulled in by those. Tomcat and
  Jackson are indirect: Spring Boot chooses their versions. Upgrading Spring Boot from 3.3.5 to 3.5.16 fixed some
  findings; the rest needed newer Tomcat, Jackson and PostgreSQL driver versions than Spring Boot chose, so we pinned
  them in `pom.xml` properties.
- One finding (Spring XsltView) is fixed only in a new major version. The app does not use XSLT, so the flaw cannot be
  reached. We accepted it in `.trivyignore` with a reason and an expiry date instead of ignoring it forever.
- A scanner can fail for reasons unrelated to security: Maven Central rate-limited Trivy (HTTP 429 Too Many
  Requests). The fix was to download dependencies with Maven first and let Trivy read that cache offline.

**What we did:** Upgraded dependencies; all 167 API tests passed, including every database migration run against a
real PostgreSQL. Documented the stages in [CI/CD Pipeline](../operations/CI_CD_PIPELINE.md).

**Commands learned:**
- `npm ci`: install exactly what the lock file records (unlike `npm install`, which may pick newer versions).
- `mvn verify`: compile, run all tests and package.
- `docker run ... aquasec/trivy fs /src`: scan a folder for vulnerable dependencies and secrets.
- `actionlint`: check a GitHub Actions workflow file for mistakes before pushing it.

**Still unsure about:**
- How Spring Boot's dependency management decides which Tomcat/Jackson versions to use.
- What makes a vulnerability HIGH versus CRITICAL (CVSS scores).

## 2026-10-07: Cleaning the repository

**What happened:** The repository held both the old static website (HTML, CSS, jQuery plugins, 23 MB of uploads) and
the new application. The old site was removed in one commit, after first committing all unsaved work in another.

**What it means:**
- Commit before a big cleanup: once work is committed, any deletion can be undone (`git checkout <commit> -- <path>`).
  Uncommitted work has no safety net.
- Keep a cleanup in its own commit, separate from feature work, so it is easy to review and to revert.
- Deleting files can have side effects beyond the files: the GitHub Pages workflow would have published the bare
  repository as the website once the HTML pages were gone, so it was removed too.
- Old public URLs (for example `/our_tours.html`) are indexed by Google. When the new site takes over the domain they
  need redirects, or visitors and search rankings land on "Page not found".

**Commands learned:** `git rm -r <paths>` (delete and stage), `git rm --cached` (stop tracking but keep the file).

**Still unsure about:** How to map old URLs to new pages with Nginx redirects (`return 301`).

## 2026-10-07: Why the droplet build froze

**What happened:** `docker compose build` on the droplet stalled. `top` showed `kswapd0` using about 22% CPU while
Java (Maven) and Node were both compiling.

**What it means:** The droplet has 1 GB of RAM. `kswapd0` is the kernel process that frees memory; when it is busy,
memory has run out and the system spends its time moving data around instead of working. Two compilers at once needed
more memory than the server has.

**What we did:** Built one service at a time (`docker compose build api`, then `web`). Longer term, the server should
not compile at all: CI builds the images and the server only downloads them
([ADR-007](../architecture/ADR/ADR-007-build-images-in-ci.md)).

**Commands learned:**
- `free -h`: memory and swap in use, human-readable.
- `top`: live processes by CPU and memory use (press `q` to exit).
- `swapon --show`: whether swap (disk used as overflow memory) is enabled.
- `docker compose build <service>`: build a single service.

**Still unsure about:** How Docker decides which build steps it can reuse from cache (`CACHED` in the output).
