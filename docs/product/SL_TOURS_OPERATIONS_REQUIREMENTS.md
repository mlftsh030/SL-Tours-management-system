# SL Tours Operations Requirements

Status: approved brief (October 2026), implemented in stages. This document is the authoritative
consolidated brief for admin, driver, fleet, inspection, enquiry and private-trip features. Section
numbers match the brief; the decisions and plan below record how each requirement is met.

## 1. Consolidated brief

1. **Website image management.** Admins manage website images, including replacement, ordering and
   alternative text. Existing assets stay in place until a replacement is explicitly selected.
2. **Tour management.** Admins create, edit, publish and unpublish tours. Public pages use
   backend-managed content and prices.
3. **Vehicle management.** Vehicle details, images, passenger capacity, operational condition, public
   listing visibility and assignments. Operational status, listing status and assignment status are
   separate: a publicly listed vehicle is not automatically available.
4. **Polished vehicle cards.** Professional, Uber-inspired cards that fit the existing SL Tours design:
   image, name/category, passenger capacity, relevant amenities and the applicable hourly rate. Public
   cards exclude private documents and internal operational information.
5. **Admin and driver access.** Separate admin and driver experiences with backend-enforced
   permissions. Drivers cannot grant themselves elevated access.
6. **Collection inspection.** Before taking an assigned vehicle the driver submits a condition
   checklist, defect photographs where applicable, odometer, fuel level, notes and a fresh collection
   selfie. The driver, vehicle, assignment and submission time are recorded. The selfie is an
   accountability record; no facial recognition.
7. **Driver verification.** Admins manage and review private driver verification documents and
   eligibility. Eligibility is enforced on the backend before collection.
8. **Inspection review and approval.** Admin-only. The collection approval policy is configurable with
   a documented default. When approval is required, submitting an inspection does not release the
   vehicle. Serious safety defects block collection, availability and booking. Routine approval never
   clears a maintenance restriction; that needs an explicit, audited, authorised action.
9. **Return inspection.** Checklist, photographs, odometer, fuel level and notes, linked to the same
   assignment as the collection. A return without a collection, or with an odometer below the
   collection reading, is rejected. A returned vehicle with unresolved restrictions does not become
   available.
10. **Booking and assignment integration.** Bookings, drivers, vehicles and inspections are connected.
    Conflicting confirmed vehicle or driver assignments are prevented, including concurrent attempts.
    Workflow transitions are enforced on the backend.
11. **Security and data integrity.** Role and ownership checks on every relevant endpoint, including
    photo access. Uploads are validated; inspection media and verification documents are private;
    important operational changes are audited. The backend is authoritative for permissions,
    availability and pricing.
12. **Admin and driver usability.** A clear admin dashboard and a mobile-friendly driver portal.
13. **Complete implementation.** Working frontend and backend, additive Flyway migrations, storage
    configuration, meaningful tests and documentation. Existing data and functionality are preserved.
    The Docker development setup is documented.
14. **Website enquiries inbox.** Website enquiries are persisted and shown to admins in an inbox with
    contact details, content, submission time and handling status (New, In progress, Awaiting
    customer, Converted to booking, Closed), the requested tour or private trip, date, pickup location,
    passenger count and internal notes. Admins search, filter, review, update and convert an enquiry to
    a booking without re-entering customer details. Customers see a clear confirmation only after the
    enquiry is saved; duplicate submissions are prevented and failures handled gracefully. Drivers have
    no access to the inbox or customer records.
15. **Assignment-only driver visibility.** Drivers see only their own assigned trips, the associated
    vehicle and the customer details needed to carry out the assignment, enforced server-side for
    lists, individual records, inspections and media. Hiding navigation is not enough.
16. **Mandatory collection and return inspections** for every vehicle assignment, preserving the
    approval policy, driver verification checks and maintenance restrictions throughout.
17. **Hourly private-trip booking.** A public "Book a Private Trip" flow based on vehicles available for
    the requested date, start time, duration and passenger count. Configurable ZAR hourly rates,
    minimum durations and disclosed fees; prices calculated on the backend and the quoted breakdown
    preserved with the request. Submission is a request until an admin confirms it; availability is
    rechecked on confirmation and overlapping confirmed assignments are prevented. No invented rates;
    sample rates are never shown as live prices.
18. **Preserve the public website.** Keep the current design, branding, layout and journeys. New
    features use the established visual language; no redesign or rebrand.

**Photo storage decision.** A private persistent Docker volume behind a storage abstraction that can
later target object storage. File metadata and relationships live in PostgreSQL; private files are
served only through authorised backend endpoints. Oracle Object Storage is a future option, not a
dependency of this stage.

## 2. Roles and permissions

| Capability | ADMIN | STAFF | DRIVER | CUSTOMER / public |
|---|---|---|---|---|
| Dashboard, bookings, enquiries inbox, customers | ✅ | ✅ | ❌ | ❌ |
| Tours, prices, private-trip rates and fees | ✅ | read only | ❌ | public data only |
| Vehicles: create/edit, listing, operational status | ✅ | read only | own assignment only | public card only |
| Open a vehicle restriction | ✅ | ✅ | via inspection defects | ❌ |
| **Clear** a vehicle restriction | ✅ (audited, note required) | ❌ | ❌ | ❌ |
| Drivers, verification documents, eligibility | ✅ | ❌ | ❌ | ❌ |
| Review/approve inspections | ✅ | ❌ | ❌ | ❌ |
| Confirm private trips, assign vehicle/driver | ✅ | ❌ | ❌ | ❌ |
| Website images | ✅ | ❌ | ❌ | public images only |
| Submit collection/return inspections | ❌ | ❌ | own assignments | ❌ |
| Inspection media | ✅ / STAFF | ✅ | own assignments only | ❌ |

Driver accounts are created by an admin only. Self-registration always creates a `CUSTOMER`; no
endpoint lets a user change their own role.

## 3. Decisions and defaults

All defaults are configuration, not hard-coded business facts. Items marked **open** need a business
decision; the system works without them and documents its interim behaviour.

| Topic | Default / behaviour | Where to change |
|---|---|---|
| Collection approval | **Required.** A collection without safety defects waits for admin approval before the vehicle is released. | `SLTOURS_COLLECTION_APPROVAL_REQUIRED` (`true`/`false`) |
| Safety defects | Always open a `SAFETY` restriction and block release, availability and booking, regardless of the approval policy. | Not configurable |
| Minor defects on return | Open a `MAINTENANCE` restriction so damage is reviewed before the vehicle is offered again. | Not configurable |
| Clearing restrictions | Admin only, separate action, mandatory note, audit event. Inspection approval never clears one. | — |
| Collection window | A driver may collect the vehicle from 12 hours before the trip starts. A vehicle that is still out on another trip (collected and not yet returned) cannot be collected or released again. | `SLTOURS_COLLECTION_OPENS_BEFORE` (e.g. `12h`) |
| Minimum vehicle photographs | 4 per inspection (front, rear, left, right); each defect needs at least one photo. | `SLTOURS_INSPECTION_MIN_PHOTOS` |
| Driver eligibility | Driver active, verification `VERIFIED`, driving licence and PrDP expiry on or after the trip date. | Admin driver screen |
| Turnaround between trips | 60 minutes, applied to vehicle and driver availability. | Admin pricing settings |
| Billing increment | Durations round **up** to 60 minutes. | Admin pricing settings |
| Hourly rates | Seeded only as **sample** rates. A sample rate is never shown or charged as a live price; in the `dev` profile it is displayed with a "Sample rate — development only" label. | Admin rates screen ("Confirm as live rate") |
| Minimum booking duration | Per category, overridable per vehicle. | Admin rates / vehicle screen |
| Additional fees | None seeded. Admin-defined fixed or per-hour fees are disclosed before submission. | Admin pricing settings |
| Overtime and included distance | Disclosed only when configured; otherwise "Overtime by arrangement" is shown. | Admin pricing settings |
| Manual pricing | Customers can request a quote; it lands in the enquiries inbox. | — |
| VAT treatment | **Open.** Prices are shown as entered by the admin; no VAT is added. | Business decision |
| Media retention | **Open.** Files are kept until an admin deletes them; deletion removes the file and keeps an audit record. | `docs/operations/MEDIA_STORAGE.md` |

### Unresolved business decisions

None of these values are invented in code or seed data. The system works without them as described.

| Decision | Current behaviour until decided |
|---|---|
| Real hourly rates per category or vehicle | Seeded rates are labelled SAMPLE; production shows those vehicles as "priced on request" and only quote requests are possible. |
| VAT treatment (inclusive, exclusive, registration status) | Totals are shown as entered; nothing is added or labelled as VAT. |
| Retention periods for selfies, inspection photos and driver documents | Kept until deleted by an administrator; no automatic purge. |
| Overtime charges | Disclosed as "by arrangement" unless an overtime rate is configured. |
| Included distance and excess-distance rate | Disclosed as "distance within your itinerary is included" unless both values are configured. |

## 4. Workflow summary

**Private trip.** Customer chooses date, start time, hours, pickup, destination/itinerary and
passengers → API lists vehicles that are listed, operational, unrestricted, large enough and free for
the whole period including turnaround → customer selects a vehicle and sees the backend price
breakdown → submits a request (booking type `PRIVATE_TRIP`, status `ENQUIRY`, price snapshot stored) →
admin confirms with a vehicle and an eligible driver → the API rechecks availability inside the
transaction; PostgreSQL exclusion constraints reject overlapping vehicle or driver assignments even
under concurrency → booking `CONFIRMED`, assignment `SCHEDULED`.

**Assignment lifecycle.** `SCHEDULED` → collection submitted → `AWAITING_RELEASE` (approval required)
or `RELEASED` → return submitted → `RETURNED` → admin review → `CLOSED`. `CANCELLED` from `SCHEDULED`
or `AWAITING_RELEASE`. Safety defects keep the assignment in `SCHEDULED` with a blocked collection
until an admin clears the restriction and the driver re-inspects.

**Enquiry lifecycle.** `NEW` → `IN_PROGRESS` ⇄ `AWAITING_CUSTOMER` → `CONVERTED` (linked booking) or
`CLOSED`. Closed enquiries can be reopened to `IN_PROGRESS`; converted enquiries are final.

## 5. Implementation plan

1. Fix review issues: idle timeout enforced server-side, background polling flagged by the client;
   missing `version` rejected with 400. **Done.**
2. Save this brief and plan; link it from `PRODUCT_REQUIREMENTS.md`. **Done.**
Steps 3–8 below are implemented; see the admin guide for operation.

3. Enquiries inbox — `enquiries` and `enquiry_notes` tables, public submission with idempotency key,
   admin inbox with search/filter/status/notes, conversion to a tour booking; trip-planner requests
   also appear in the inbox.
4. Drivers — `DRIVER` role, driver profiles linked to accounts, verification fields and private
   documents, admin driver management, `/api/v1/driver/**` endpoints scoped to the signed-in driver.
5. Fleet, assignments and inspections — vehicle fields split into operational/listing status,
   restrictions, assignments with time windows and exclusion constraints, collection/return
   inspections with private media on a Docker volume, admin review and comparison.
6. Private trips — rates, settings and fees, availability, pricing calculator, public vehicle cards and
   booking flow, admin confirmation with driver/vehicle assignment.
7. Website image slots (hero slider, gallery) with fallback to the current built-in assets.
8. Verification — TypeScript, Vitest, Maven (including PostgreSQL concurrency tests via
   Testcontainers) and Docker Compose build/run.

## 6. Out of scope for this stage

Payment gateway, outbound email/WhatsApp notifications, password reset/self-service password change,
automated retention purge jobs, and object-storage deployment.
