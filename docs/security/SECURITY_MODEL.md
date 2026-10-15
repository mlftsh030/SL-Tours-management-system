# Security Model

## Authentication

Accounts are persisted in `user_accounts`; passwords are BCrypt hashes with cost 12. Registration creates only customer accounts, validates email/name/password fields, and rejects passwords exceeding BCrypt's 72-byte UTF-8 limit. The environment-configured administrator is bootstrapped at startup; example/short passwords fail startup outside the explicit dev/test profiles.

Spring Security handles form login, session fixation protection, CSRF token rotation, concurrent session tracking, and POST logout with server-side invalidation. Sessions expire after 30 minutes without user activity (`SLTOURS_IDLE_TIMEOUT`). An `IdleSessionFilter` tracks the last user-initiated request per session; requests the web client sends without recent interaction (polling) carry `X-SLTours-Background: 1` and do not extend it, so unattended admin and driver screens are signed out. The client also signs out after 30 idle minutes and sends a keep-alive while the user is actively typing. Cookies are HttpOnly, SameSite=Lax, and Secure by default; local Docker explicitly disables Secure for HTTP. Passwords and tokens are never stored in browser local storage or public build variables. HTTP Basic is disabled.

The client obtains a fresh masked CSRF token from `/api/v1/auth/csrf` before every mutation. All writes, including login, registration, public booking submissions, and logout, require a token. The API has an explicit endpoint/method allowlist and denies unspecified routes. CORS allows only configured origins.

## Authorization and ownership

- ADMIN: operations reads, tour creation/editing, and booking status changes.
- STAFF: operations reads and booking status changes; tour writes additionally require ADMIN through method security.
- CUSTOMER: only `/api/v1/account/**`, always scoped to the signed-in account: profile, own enquiries, own bookings, own issued quotations and invoices (PDF download and proof-of-payment upload). Another customer's document looks exactly like a missing one (404). No staff, driver or partner access.
- DRIVER: only `/api/v1/driver/**`. Every handler resolves the driver from the session and loads assignments with `id AND driver_id`, so lists, records, inspections and media are limited to their own assignments; other records return 404. Drivers cannot reach the enquiries inbox, customer records, pricing, fleet documents or review notes. Driver accounts are created only by administrators; no endpoint lets a user change a role.
- ADMIN-only (method security): driver records and documents, assignment resource options (they include driver eligibility), inspection approval/rejection, clearing vehicle restrictions, assignments and private-trip confirmation, vehicle/pricing/site-image writes. STAFF can read operations data, work the enquiries inbox and open restrictions.
- Anonymous: active public catalogue and validated, CSRF-protected booking/planner submissions.

Registered bookings are associated with an authenticated account UUID. Supplied customer emails, account IDs, and role fields do not grant ownership or privileges. Enquiries and bookings made while signed in as a customer are linked to that account. Guest records (bookings, enquiries, and issued documents without a booking) stay unowned until the customer proves control of the address by opening the emailed verification link; registering with a matching but unverified address exposes nothing. Verification and password-reset links are single-use 256-bit tokens stored as SHA-256 hashes, bound to the address they were sent to, and expire after 48 hours and 1 hour respectively. A password reset signs out every existing session of the account. Reset requests give the same answer whether or not the address has an account. Contact summaries group email addresses only for staff reporting.

## Integrity and confidentiality

Validated request DTOs prevent entity mass assignment. JPA/JDBC parameterization avoids SQL injection. React escapes text; no raw HTML is accepted/rendered. Tour image values are restricted to local raster asset paths and reject traversal and external URLs. Uploads (inspection photos, selfies, driver documents, vehicle and website images) are validated by detected content type and size, stored under server-generated keys outside the web root, and served only through authorised endpoints with no-store and nosniff headers; see [Media Storage](../operations/MEDIA_STORAGE.md).

Tour and booking version columns reject concurrent stale edits. Booking prices come from the database and are snapshotted at creation. Price/guest bounds prevent integer overflow. Booking status transitions reject reopening terminal states. Private-trip and enquiry requests carry idempotency keys with unique constraints. PostgreSQL exclusion constraints (`assignments_vehicle_no_overlap`, `assignments_driver_no_overlap`) reject overlapping vehicle or driver assignments, including concurrent confirmations. Private-trip prices are computed on the server, re-checked on submission against the customer's displayed total, and stored as a breakdown. Vehicle restrictions can be cleared only by an administrator with a recorded note; enquiry, driver, inspection, assignment, restriction, pricing and media actions are audited. Staff tour/status edits produce database audit records transactionally, with actor, resource, action, and change details; audit details exclude passwords and customer notes.

Sensitive API responses use Spring Security's no-store defaults. Nginx adds nosniff, frame denial, referrer/permissions headers, body limits, and a restrictive CSP on private HTML routes. Private routes avoid the public translation widget; public-to-private links reload the document. Private caches are cleared on sign-in, logout, and expired sessions.

## Brute-force and deployment

Login/registration have bounded per-process IP limits plus an Nginx IP limit. Nginx overwrites forwarding headers. The API debug port is loopback-only; deployments must keep the API behind trusted proxies because forwarded-header interpretation is enabled.

Session registries and application limits are instance-local. Shared deployments require distributed session/rate-limit storage, HTTPS, strong secrets, staff MFA/SSO, a production SMTP provider for verification and password-recovery email, and operational monitoring/backups/retention. These external integrations are not implemented by the local administration workflow. No raw payment card data is stored.

See [Admin Guide](../development/ADMIN_GUIDE.md) for operation and local configuration.
