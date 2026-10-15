# Architecture

## System Shape

SL Tours is implemented as a modular monolith with a separate web client:

```text
apps/web  ->  apps/api  ->  PostgreSQL
React         Spring Boot   Flyway-managed schema
```

The public website, booking flow, My Trip portal, and operations UI use the same API contract. Tour data is seeded and read from PostgreSQL so prices are not duplicated across frontend pages.

## Applications

- `apps/web`: React + TypeScript + Vite. Feature-oriented folders for tours, booking, trip planning, customer trip, and operations.
- `apps/api`: Java 21 + Spring Boot 3. Domain-oriented packages for tours, bookings, trip planning, operations, shared errors, and security.
- `docker-compose.yml`: production-like local stack with Nginx web container, API, and PostgreSQL.

## API Style

- REST endpoints under `/api/v1`.
- Public catalogue and CSRF-protected booking/trip-planning submission routes; private account history is scoped to authenticated customer IDs.
- Protected operations routes under `/api/v1/admin/**`.
- Validation uses Jakarta Bean Validation.
- Errors use `ApiError` with timestamp, status, code, message, and field errors.

## Security Shape

- Admin routes use database-backed Spring Security sessions with CSRF, role checks, hashed passwords and sign-in limits. Tour mutations additionally require ADMIN.
- Passwords are BCrypt encoded in memory from environment variables.
- Future production authentication should move to a proper user table, session/JWT strategy, refresh policy, MFA options, and audit logging.

## Data Ownership

- Tours are the source of truth for public catalogue, detail pages, booking estimates, quotes, and operations.
- Bookings use public references such as `SLT-2026-ABC123` and must not rely on sequential IDs.
- Customer trip URLs must not expose predictable trip IDs in production.

## Extension Points

- Payment gateway providers can sit behind a payment service/adapter.
- Email and WhatsApp providers should sit behind notification providers.
- File storage can be introduced behind a storage abstraction when uploads are enabled.
- Travel agent/corporate portals should reuse the same domain model with stricter authorization.

Administration uses separate tour, authentication, customer, booking-policy, mapping and audit services. Controllers accept validated DTOs, repositories own persistence, and private/public tour reads share the database price source. See [Admin Guide](../development/ADMIN_GUIDE.md).
