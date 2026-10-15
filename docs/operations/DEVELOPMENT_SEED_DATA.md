# Development Sample Data in Production

Migration V2 (written before production use) inserts sample customers, bookings, a trip-planner request, guides,
drivers, vehicles, an assignment, a quote and a payment **in every environment**. Flyway history must not be
edited, so V2 still runs on fresh databases.

## What protects production

- **V12** flags exactly those rows by their fixed IDs (`demo_data = true`) and deletes nothing. The seeded tours are
  the real catalogue (aligned with the live site by V3) and are not flagged.
- Outside the `dev`/`test` profiles (`DemoDataPolicy`):
  - sample vehicles never appear on the website or in private-trip availability;
  - sample vehicles and drivers cannot be assigned or confirmed, and appear as unavailable ("Development sample
    record") in admin choices;
  - dashboard figures exclude sample bookings, enquiries, guides and vehicles;
  - admin lists show a *Sample data* badge on sample vehicles, drivers, bookings and enquiries.
- Demo driver sign-ins are created only by the `dev` profile and are disabled at startup elsewhere
  (`DemoAccountGuard`).
- If a flagged vehicle or driver was turned into a real record, an administrator can open it and choose
  **Mark as genuine** (audited). Check every field first.

## Fresh production database

1. Deploy normally; V2 inserts the samples and V12 flags them immediately. Nothing sample-based is offered.
2. Optionally remove them before go-live with `docs/operations/sql/remove-development-seed-data.sql` (see below).

## Database that has already been migrated

1. Upgrade; V12 flags the samples by ID. Review the admin *Sample data* badges.
2. For any flagged vehicle or driver that is genuinely in use, **Mark as genuine**.
3. Back up the database and media volume ([Media Storage](MEDIA_STORAGE.md)).
4. Run the removal script. It checks first and **aborts without changes** if any real row still refers to a sample row
   (for example an inspection, assignment, payment or private trip involving a sample vehicle), and lists the
   reason in the error. Resolve or mark those records genuine, then run it again.
5. The script records a `SAMPLE_DATA_REMOVED` audit event.

Keeping the flagged rows is also safe: they stay inert outside development.
