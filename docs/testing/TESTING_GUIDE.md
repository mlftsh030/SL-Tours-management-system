# Testing Guide

## Current Coverage

- Frontend: tour/home/gallery rendering and interactions, plus session API client checks for CSRF, cookie credentials, form encoding and expired-session handling.
- Backend: tour mapping, booking prices/ownership, password hashing, role/CSRF/session controls, pagination validation, safe tour images, concurrent edit rejection and booking status policy.
- Docker smoke: health, public tour API, proxied API, booking creation, and admin dashboard with credentials.

- Operations features (backend):
  - `EnquiryIntegrationTest`: stored-before-confirmed, idempotent retries, search escaping, status policy, notes, conversion copying customer data, trip-planner backfill.
  - `InspectionWorkflowIntegrationTest`: cross-driver denial for records, inspections and media; selfie/checklist/photo/content-type validation; approval releasing but never clearing restrictions; safety defects blocking collection, approval, availability and assignment; return without collection and odometer rollback rejected; collection/return comparison.
  - `PrivateTripIntegrationTest`: availability filtering (hidden, too small, restricted, busy incl. turnaround), server repricing and stored breakdown, **concurrent confirmations of the same vehicle (one succeeds, one 409)**, driver double-booking, cancellation freeing resources, quote requests and manual pricing.
  - `PriceCalculatorTest`: hourly × billable hours, minimum charge, rounding increments, fees and half-up rounding, overflow.
  - `DriverVerificationIntegrationTest`: verification requires accepted documents, re-verification after licence changes, disabled accounts.
  - `RoleBoundarySecurityTest`: driver/customer/staff/admin boundaries for every new endpoint group, driver documents admin-only, private media never public, CSRF on uploads.
  - `IdleSessionFilterTest` and `ApiSecurityTest`: idle timeout despite polling; missing `version` rejected.
- Operations features (frontend): enquiry form (confirmation only after save, retry idempotency, double-submit), idle sign-out and background request labelling, private-trip booking (price-change refresh, idempotent resubmit), driver inspection form (validation and multipart upload).

- Explore catalogue: `ItineraryServiceTest` (age/residency/session pricing, expired prices become unverified, per-item quantities, opening hours, seasons, last entry, minimum age, geographic ordering, budget verdicts, sample vehicle estimates), `ActivityCatalogueIntegrationTest` (seeded catalogue, Cape Point fees after 31 Oct 2026, publication rules, immediate SEO and sitemap refresh, noindex and escaping), role checks in `RoleBoundarySecurityTest`, frontend `DayPlannerPage.test.tsx`.
- Catalogue uncertainty: `ItineraryServiceTest` (conflicting, stale and promotional prices are estimates; sampled sessions and observed date ranges; stale and doubtful hours; unpublished participation limits; exclusions) and `ActivityCatalogueIntegrationTest#officialUncertaintiesAreCarriedIntoPagesAndPlans`.
- Planning assistant: `PlanningAssistantIntegrationTest` (mock conversation through collection, planning, budget change, swap and confirmed enquiry; unsupported questions; injection in messages and in catalogue text; invented prices, discounts and guarantees; provider failure, turn timeout and tool-loop limits; spending and request caps; disabled mode), `ReplyGuardTest`, `ConversationStoreTest`, `AnthropicChatModelTest` (local HTTP stand-in), `RoleBoundarySecurityTest#assistant…`, `ProductionSafetyCheckTest`, frontend `AssistantPanel.test.tsx`. Live-model behaviour needs a separate evaluation with real credentials.
- Partnerships: `PartnershipFlowIntegrationTest` (application, approval, real partner login, referral without and with an agreement, payment and completion, exact commission, concurrent re-evaluation, payment/refund/payout retries, proportional refund adjustment, payout and owed-back balance, self-referral, suspended codes, admin attribution change, cross-partner isolation, audit events; concurrent payouts; agreement validation and end dates; supplier proposal to draft activity, verification and disclosure), `CommissionArithmeticTest`, `RoleBoundarySecurityTest#partner…`, frontend `Partners.test.tsx`.
- Operations follow-up: `ProductionSampleDataIntegrationTest`, `AssignmentAttentionIntegrationTest`, `DemoAccountGuardTest`, `ProductionSafetyCheckTest`.
- Browser walkthroughs (Playwright, desktop 1366 px and phone 390 px) were run manually for admin, driver, Explore and planner screens; they are not part of `npm test`.
- Browser journeys ([tools/journeys](../../tools/journeys/README.md)): layout at seven widths, keyboard use, tour
  enquiries with failures and retries, private trips with a price change, accounts with real emails through Mailpit,
  translation and the reviews fallback. Run against the local Docker stack before a release; not part of `npm test`.

Integration tests use Testcontainers with PostgreSQL 16 and apply every Flyway migration, so Docker must be running for `mvn test`.

## Commands

```bash
npm --workspace @sltours/web run typecheck
npm --workspace @sltours/web run test
npm --workspace @sltours/web run build
mvn -f apps/api/pom.xml test
tools/smoke/routes.sh http://localhost:8080 false        # routes, redirects, 404s (read-only)
(cd tools/journeys && npm ci && npm run all)               # browser journeys (local stack only)
```

## Next Tests To Add

- Controller tests for booking/trip planning validation.
- Journeys still to automate: admin booking assignment, trip-planner request to
  quote, and the driver inspection upload in a real browser.
- Running the journeys in CI (they need the Docker stack and Chrome on the runner).

See [Admin Guide](../development/ADMIN_GUIDE.md) for the Docker/PostgreSQL browser workflow verified during admin development.
