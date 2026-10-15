# Release Readiness

Status of the 7 October 2026 website review, as of 9 October 2026, on branch `feature/release-readiness`. Everything
below was implemented and verified on a local Docker stack. **Nothing has been deployed**: the preview droplet and
sl-tours.com still run older code.

> **Update, 15 October 2026:** the preview droplet now serves https://sltours.tlokwadigital.com for client review,
> but with an earlier build: the redirect and 404 checks in `tools/smoke/routes.sh` still fail there. This code is on
> GitHub, where CI runs on every push. sl-tours.com is unchanged. The release assessment below is kept as written on
> 9 October.

How each status was established:

- **Fixed:** changed in code, with a test that fails without the change where the defect was behavioural.
- **Verified locally:** exercised in a real browser against the local stack ([journey checks](../../tools/journeys/README.md)).
- **Waiting on SL Tours:** needs a fact or decision only the owner can give ([Owner decisions](OWNER_DECISIONS.md)).

## Requirements

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 1 | Promotions match tour inclusions | Fixed | Homepage slides describe actual inclusions and link to the tour ([HeroSlider.tsx](../../apps/web/src/features/home/HeroSlider.tsx), test "promises nothing a tour does not include"). Whether any promotion exists: owner question P1. |
| 2 | Development pricing safe in production | Fixed, tested | Sample rates refused outside `dev`/`test` (`SampleRatePolicyTest`). Real rates: owner questions R1–R4. |
| 3 | Essential facts on tour cards | Fixed | Summary, group minimum and "Ask for a private quote" on each card (`PackageCard.test.tsx`). |
| 4 | Vehicle options match the fleet | Fixed | Migration V26 links fleet vehicles to models; alternatives must be chosen explicitly. |
| 5 | Photos through the vehicle journey | Fixed | Model photo, labelled illustrative, when a vehicle has none of its own. |
| 6 | No stale availability or mixed requests | Fixed, reproduced first | `PrivateTripPage.test.tsx`; verified locally (`privatetrip` journey: stale results withdrawn). |
| 7 | Commercial terms without invented facts | Waiting on SL Tours | Pages say what is not published. Owner questions T1–T8. |
| 8 | Privacy information | Added; needs review | `/privacy` describes what the code does. POPIA review and missing facts: PR1–PR4. |
| 9 | Accessibility and layout | Fixed, verified locally | Contrast 4.64:1 or better; tap targets of at least 24 px; `layout` journey 98/98 (14 pages × 7 widths); `keyboard` journey 21/21. |
| 10 | HTTPS; staging separate from production | Prepared, verified locally | Staging is `noindex` with no sitemap; production refuses to start without explicit indexing; HTTPS tested with a local certificate. Needs a domain: W3, W4. |
| 11 | Existing URLs preserved | Fixed, verified locally | Old sl-tours.com addresses redirect; unknown pages return 404 (`tools/smoke/routes.sh`, all checks pass). |
| 12 | Coherent page metadata | Fixed, tested | Canonical, social and structured data follow client-side navigation (`useDocumentTitle.test.tsx`). |
| 13 | Delivery measured and improved | Fixed, measured | Gzip, cache policy, route-specific preload, code splitting, smaller images. Homepage Lighthouse 30 → 60 ([Performance](PERFORMANCE.md)). Lab figures only. |
| 14 | Shareable, recoverable filters | Fixed, verified locally | Category and search in the URL, "Clear filters" (`ExperiencesPage.test.tsx`, `keyboard` journey). |
| 15 | Clear customer journeys | Fixed, verified locally | Labels match destinations; `/book/:slug` starts at the group minimum, refuses fewer guests and confirms a *request* (`booking` journey). Reply time: owner question C1. |
| 16 | Explore and planner accuracy | Fixed, tested | Region jump links and "Show all"; every listing still in the server-rendered page and sitemap. Planner rules covered by `ItineraryServiceTest`. |
| 17 | Cape Town time | Fixed, tested | Times formatted in Africa/Johannesburg in UTC, New York, Tokyo and Auckland test runs; staff inbox checked from a New York browser. |
| 18 | Previously unverified journeys | Verified locally; external parts pending | See below. |

## Requirement 18: journeys

All run in Chrome against the local stack with test addresses (`@example.test`) and Mailpit as the mail server.

| Journey | Result |
|---|---|
| Standard, airport and custom-tour enquiries: validation, network failure, retry, pending, receipt | 18/18 |
| `/book/:slug` booking request | 5/5 |
| Private trip: search, stale results, price change (409) and retry, quote fallback | 8/8 |
| Registration, verification email, linking a guest enquiry, duplicate address, password reset, single-use links | 15/15 |
| Translation across route changes and the sign-in page | 7/7 |
| Reviews with the widget blocked and loaded | 3/3 |
| Keyboard and phone: menu, carousel, lightbox, filters, Explore | 21/21 |
| Access: anonymous, customer and admin against staff, driver, partner and account endpoints | All refused or allowed as expected |
| Staff enquiry inbox shows the new enquiries, in Cape Town time | Checked |
| Idle expiry, version checks, inspection uploads, payments, refunds, commission | Not changed by this work; covered by the API suite (178 tests) |

**Pending external checks:** real email delivery (needs an SMTP provider, E1); HTTPS with a real certificate and
the domain switch (W3, W4); live driver and partner sessions in a browser (covered by `RoleBoundarySecurityTest`).

### Defects found and fixed during verification

- **Edited enquiries were lost after a failed send.** If a request reached the server but the reply was lost, an
  edited retry reused the same idempotency key and received the *earlier* enquiry. Fixed in all five public forms
  ([useIdempotencyKey.ts](../../apps/web/src/lib/useIdempotencyKey.ts)).
- **The language selector disappeared** for the rest of a visit after the sign-in page. Google allows one widget per
  page, so the widget element is now kept and moved between layouts.
- **Reviews showed an empty box and a dead button** when the review widget was blocked. A heading and a link to the
  Google listing now appear instead.
- **A private-trip time could shift by an hour** for visitors in a country changing to daylight-saving time that day.
  Entered Cape Town times are now formatted without conversion.

## Deployment and rollback

Follow [HTTPS, staging and moving sl-tours.com](HTTPS_AND_MIGRATION.md). This work adds one migration (V26, additive);
rolling the application back to the previous version was tested and needs no database restore.

## Release assessment

**Not ready for sl-tours.com yet.** The code-side work is done and verified locally, but these are open:

1. **Owner decisions that block launch:** private-trip rates or a quote-only launch (R1–R4), deposit and cancellation
   terms (T1), POPIA review, retention periods and Information Officer (PR1–PR3), staging domain and launch approval
   (W3, W4), and an email provider (E1).
2. **Not deployed or pushed.** The branch exists only locally. When last checked (7 October) the preview droplet ran a
   build with the Tomcat vulnerabilities fixed that day; deploying a patched build there is worth doing before launch work.
3. **CI has never run on GitHub,** because nothing has been pushed.
