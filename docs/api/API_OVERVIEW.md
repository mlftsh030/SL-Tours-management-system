# API Overview

OpenAPI JSON: `/api-docs`. Swagger UI: `/swagger-ui`.

## Authentication

- `GET /api/v1/auth/csrf`: obtain masked CSRF token and header name.
- `GET /api/v1/auth/me`: current account summary, or `authenticated: false`.
- `POST /api/v1/auth/login`: URL-encoded `username` and `password`; creates session, returns 204.
- `POST /api/v1/auth/register`: JSON name/email/password; CUSTOMER only, returns 201.
- `POST /api/v1/auth/logout`: invalidates session, returns 204.

All POST/PUT/PATCH/DELETE requests require the CSRF header, including public submissions. Send the session cookie (`credentials: include`). HTTP Basic is unsupported.

## Public catalogue and submissions

- `GET /api/v1/tours`
- `GET /api/v1/tours/{slug}`
- `POST /api/v1/bookings`
- `POST /api/v1/trip-planning-requests` (optional `idempotencyKey`; also appears in the enquiries inbox)
- `POST /api/v1/enquiries`: contact-form enquiry with required `idempotencyKey` (UUID). 201 when stored, 200 with `duplicate: true` for a retry.
- `GET /api/v1/private-trips/vehicles`: listed vehicles as public cards (no registration or internal data).
- `GET /api/v1/private-trips/availability?date=&time=HH:mm&durationMinutes=&passengers=`: available vehicles with backend price quotes and pricing disclosures.
- `POST /api/v1/private-trips/requests`: private-trip booking request; `expectedTotalCents` must match the current backend price (409 otherwise), `idempotencyKey` required.
- `POST /api/v1/private-trips/quote-requests`: manual-pricing request, stored as an enquiry.
- `GET /api/v1/activities?category=&area=&q=`: published activities as cards; `GET /api/v1/activities/{slug}`: activity page with current and announced prices, extras, hours, FAQs and sources.
- `POST /api/v1/itineraries`: calculates a day plan (nothing stored); see `docs/product/EXPLORE_CATALOGUE.md`.
- Planning assistant (public, CSRF-protected, session-bound; see `docs/operations/PLANNING_ASSISTANT.md`): `GET /api/v1/assistant/status`,
  `POST /api/v1/assistant/conversations`, `POST /api/v1/assistant/conversations/{id}/messages` `{message}`,
  `POST /api/v1/assistant/conversations/{id}/enquiry` `{optionId, name, email, phone?, note?, confirmed: true, idempotencyKey}`,
  `DELETE /api/v1/assistant/conversations/{id}`. 429 when limits are reached; 503 when chat is disabled.
- Partnerships: `POST /api/v1/partner-applications` (public), `GET /api/v1/partners/referrals/{code}` (public, active codes only).
  Partner area (PARTNER role, own records only): `GET /api/v1/partner/me|referrals|statement|proposals`, `POST /api/v1/partner/proposals`,
  `PUT /api/v1/partner/proposals/{id}?version=`, `POST /api/v1/partner/proposals/{id}/withdraw?version=`.
  Administration (ADMIN): `GET /api/v1/admin/partners[?status=]`, `GET /api/v1/admin/partners/{id}`, `POST …/{id}/decisions`, `POST …/{id}/agreements`,
  `POST …/{id}/agreements/{agreementId}/end`, `POST …/{id}/payouts`, `POST …/{id}/supplier-payments`, `GET /api/v1/admin/partners/proposals[?status=]`,
  `POST /api/v1/admin/partners/proposals/{id}/decision`. Booking finance: `GET /api/v1/admin/bookings/{reference}/finance` (staff and admin);
  `POST …/payments`, `POST …/commission/evaluate`, `PUT …/referral` (ADMIN).
- Public booking and enquiry submissions (tour bookings, private trips and quotes, enquiries, Plan My Trip, assistant plans) accept an optional
  `referral: {code, capturedAt, source: LINK|CODE}`.
- Itineraries accept an optional `exclude` list of activity slugs and return per-stop `cautions` and a `confidence` summary.
- `GET /api/v1/seo/head?path=`, `GET /api/v1/seo/body?path=`, `GET /api/v1/seo/sitemap.xml`: server-rendered head tags, core content and sitemap (used by Nginx).
- `GET /api/v1/public/site-images?slot=HOME_HERO|GALLERY`, `GET /api/v1/public/media/{id}` (PUBLIC files only).

Only ACTIVE tours are public/bookable. Authenticated customer bookings are linked to the server's account identity; anonymous submissions remain guest bookings.

## Customer accounts

- `GET /api/v1/account/bookings?page=0&size=20`: CUSTOMER only; current account's bookings.

## Administration

ADMIN/STAFF session required:

- `GET /api/v1/admin/dashboard`
- `GET /api/v1/admin/bookings?page=0&size=20&status=ENQUIRY`
- `PATCH /api/v1/admin/bookings/{reference}/status`: `{status, version}`
- `GET /api/v1/admin/customers?page=0&size=20`
- `GET /api/v1/admin/tours?page=0&size=20`

ADMIN additionally required:

- `POST /api/v1/admin/tours`: validated `SaveTourRequest`; defaults supplied by UI, not by entity mass assignment.
- `PUT /api/v1/admin/tours/{id}`: complete validated tour payload with current `version`.

Enquiries, fleet, assignments and pricing (ADMIN/STAFF; writes marked ADMIN require ADMIN):

- `GET /api/v1/admin/enquiries?q=&status=&source=&page=&size=`, `GET /api/v1/admin/enquiries/{reference}`
- `PATCH /api/v1/admin/enquiries/{reference}` `{status, version}`; `POST …/notes` `{body}`; `POST …/convert` (tour booking)
- `POST /api/v1/admin/enquiries/{reference}/convert-private-trip` (ADMIN)
- `GET /api/v1/admin/vehicles`, `GET /api/v1/admin/vehicles/{id}`; `POST`/`PUT` (ADMIN); `POST|PATCH|DELETE …/images` (ADMIN)
- `POST /api/v1/admin/vehicles/{id}/restrictions` (STAFF or ADMIN); `POST …/restrictions/{rid}/clear` `{version, note}` (ADMIN)
- `GET /api/v1/admin/assignments?scope=active|recent`, `GET …/assignments/options?startsAt=&endsAt=&passengers=`
- `POST /api/v1/admin/assignments` (tour bookings), `PUT …/{id}`, `POST …/{id}/cancel` (ADMIN)
- `GET /api/v1/admin/assignments/{id}/inspections` (collection/return comparison), `GET /api/v1/admin/inspections/pending`, `GET /api/v1/admin/inspections/{id}`
- `POST /api/v1/admin/inspections/{id}/approve|reject` `{version, notes}` (ADMIN)
- `GET /api/v1/admin/private-trips/{reference}`, `POST …/confirm` `{version, vehicleId, driverId}` (ADMIN)
- `GET /api/v1/admin/pricing`; `PUT …/pricing/settings`, `PUT …/pricing/rates/{category}`, `POST|PUT …/pricing/fees` (ADMIN)
- `GET /api/v1/admin/media/{id}` (driver documents ADMIN only)
- `/api/v1/admin/drivers/**`: list, create, update, sign-in account, verification, documents (ADMIN only)
- `/api/v1/admin/site-images` (ADMIN only)
- `GET|POST /api/v1/admin/activities`, `GET|PUT /api/v1/admin/activities/{id}`, `POST …/{id}/verify` `{version}`, `POST …/{id}/status` `{version, status}` (ADMIN only)
- `GET|PUT /api/v1/admin/planning` (day-planner assumptions, ADMIN only)
- `GET /api/v1/admin/assignments/attention` (upcoming trips at risk); vehicles and drivers return `upcomingAssignments`; `POST /api/v1/admin/vehicles/{id}/genuine`, `POST /api/v1/admin/drivers/{id}/genuine` (ADMIN)

## Driver portal

DRIVER session required. Every response is scoped to the signed-in driver; other drivers' records return 404.

- `GET /api/v1/driver/me`, `GET /api/v1/driver/checklist`
- `GET /api/v1/driver/assignments`, `GET /api/v1/driver/assignments/{id}`
- `POST /api/v1/driver/assignments/{id}/collection`: multipart `inspection` (JSON), `photos[]`, `selfie`
- `POST /api/v1/driver/assignments/{id}/return`: multipart `inspection` (JSON), `photos[]`
- `GET /api/v1/driver/media/{id}`: the driver's own inspection photos and selfies only

Requests made without recent user interaction carry `X-SLTours-Background: 1` and do not extend the idle timeout.

Paginated responses have `content`, `number`, `totalPages`, and `totalElements`. Page is zero-based; size is 1–100. Tour/admin booking edits use optimistic concurrency and return 409 for stale versions. Duplicate tour slugs also return 409. Invalid DTOs return structured 400 errors. Unauthorized and forbidden requests return 401/403; rate-limited authentication returns 429.
