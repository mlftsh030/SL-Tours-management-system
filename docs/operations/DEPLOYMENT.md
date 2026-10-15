# Deployment

## Recommended Shape

- Serve `apps/web` through the bundled Nginx configuration (or another server with equivalent server-side includes and
  an internal proxy to the API). Plain static hosting would lose the server-rendered titles, canonical links, page
  content and live sitemap described in [Search Metadata](SEO.md).
- Run `apps/api` as a single Spring Boot service.
- Use managed PostgreSQL for production; see [Production database](PRODUCTION_DATABASE.md) for the options and the
  restricted database roles.
- Run Flyway migrations during API deployment.
- Store secrets in the hosting platform, not in Git.

## Database prerequisites for V9

Migration V9 adds PostgreSQL exclusion constraints that prevent double-booking vehicles and drivers. They need the
`btree_gist` extension. Before the first deployment that includes V9:

1. **Check availability** on the target server:
   `select name, default_version, installed_version from pg_available_extensions where name = 'btree_gist';`
   No row means the extension is not installed on that server (managed providers: check their supported-extensions list).
2. **Check permission** for the role Flyway uses. Either that role has `CREATE` on the database (e.g. it owns the
   database), or an administrator runs `create extension if not exists btree_gist;` in the application database
   beforehand. The migration's `if not exists` then succeeds without needing the privilege.
3. **Check for overlapping legacy assignments**, which would stop the constraint from being created. Earlier versions had
   no way to create assignments, so only rows inserted by hand could conflict:

   ```sql
   select a.id, b.id from assignments a join assignments b on a.id < b.id
    join bookings ba on ba.id = a.booking_id join bookings bb on bb.id = b.booking_id
   where a.status <> 'CANCELLED' and b.status <> 'CANCELLED' and ba.travel_date = bb.travel_date
     and (a.vehicle_id = b.vehicle_id or a.driver_id = b.driver_id);
   ```
   Resolve any rows returned before migrating.

## Production configuration

- Run with `SPRING_PROFILES_ACTIVE=production` and `SESSION_COOKIE_SECURE=true` behind HTTPS.
- The API refuses to start if the `dev`/`test` profile is combined with `production`/`prod`, or if
  `sltours.pricing.allow-sample-rates` is enabled outside `dev`/`test`. Demo driver sign-ins exist only in the `dev` profile.
- `docker-compose.yml` defaults to the `dev` profile for local use; set the variables above for any shared deployment.
- Set `SLTOURS_PUBLIC_BASE_URL` to the public origin (default `https://sl-tours.com`); it is used for canonical links,
  Open Graph URLs and the sitemap.
- The planning assistant is off unless configured; see [Planning Assistant](PLANNING_ASSISTANT.md). Never set
  `SLTOURS_ASSISTANT_PROVIDER=mock` outside development (the API refuses to start).
- Mount the `media-data` volume and back it up with the database ([Media Storage](MEDIA_STORAGE.md)).
- Migration V2 inserts sample bookings, people and vehicles in every environment. V12 flags them and production
  excludes them from inventory and reporting; see [Development Sample Data](DEVELOPMENT_SEED_DATA.md) for removal.

## Turnaround changes and existing assignments

The turnaround (Private-trip rates → Rules) is stored on each assignment when it is created or changed, as
`blocked_until = end + turnaround`. Changing the setting:

- applies to new assignments, to private-trip availability searches, and to any assignment whose vehicle or driver is
  changed afterwards (its window is recalculated with the current setting);
- does **not** rewrite existing assignments, so they keep the buffer they were confirmed with. Increasing the
  turnaround can therefore leave two existing trips closer together than the new setting; reducing it frees time only
  for new bookings;
- is audited (`PRICING_SETTINGS_UPDATED`).

To apply a new turnaround to an existing trip, change its vehicle or driver from **Trips & inspections**; the window is
recalculated and the usual overlap checks run with the new buffer.

## Required Production Work

- Configure HTTPS and secure headers: prepared in [HTTPS, staging and moving sl-tours.com](HTTPS_AND_MIGRATION.md).
- Configure real CORS origins.
- Schedule the database and media backups, the daily backup check and the monthly restore test
  ([Backup and recovery](BACKUP_AND_RECOVERY.md)); the scripts exist in `tools/db/`.
- Add structured log aggregation.
- Add error monitoring.
- Add rate limiting and WAF/reverse proxy protections.

## Current Local Deployment

`docker compose up --build` starts web, API, and PostgreSQL locally.
