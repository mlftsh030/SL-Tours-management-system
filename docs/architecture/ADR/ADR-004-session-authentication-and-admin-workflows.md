# ADR-004: Session authentication and administration workflows

Status: Accepted. Supersedes the HTTP Basic foundation decision in ADR-003.

## Context

Administration requires interactive staff login, authenticated customer bookings, tour creation and pricing edits, and private customer records. Frontend Basic credentials are incompatible with those workflows and cannot be shipped in a public build.

## Decision

Use Spring Security form authentication backed by database accounts and BCrypt hashes. Let Spring's authentication/session filters handle session-ID rotation, CSRF token rotation and logout. The SPA obtains CSRF tokens through a dedicated endpoint before mutations. Store session credentials only in HttpOnly cookies.

Protect operations at the security filter layer. Require ADMIN additionally for tour writes through method authorization; STAFF may review bookings/customers and update booking status. Customer history is selected by the authenticated account UUID. Do not infer booking ownership from guest email addresses.

Keep tour management, account authentication/customer reads, booking status policy, mappers, repositories and audit persistence in distinct domain modules. Use validated request DTOs instead of binding entities. Use stable paginated response DTOs. Use Flyway migrations and version columns for stale-write protection. Keep price calculations in the server booking service and snapshot totals at booking time.

Private routes use a separate layout and fresh HTML navigation from the public site, preventing translation scripts from entering account/admin documents. Nginx applies a private-route CSP and no-store caching. Public catalogue prices continue to come from the same tour database records edited by administration.

## Consequences

Local administration/customer workflows work without an external identity provider. Sessions and application sign-in limits are instance-local; a distributed deployment needs shared storage/limits. Email verification, password recovery and staff MFA/SSO remain integration work before broader deployment. Image inputs use existing local raster assets; uploads require a separate validated storage workflow.
