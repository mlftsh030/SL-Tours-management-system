# Environments and production database

## Environments

Each environment has its own database, its own credentials and its own media storage. Nothing is shared.

| Environment | Database | Credentials | Data |
| --- | --- | --- | --- |
| **Development** (your machine) | Docker Compose `postgres` service, volume `postgres-data` | `.env` (local defaults: one `sltours` user) | V2 sample rows plus whatever you create. Walkthrough and e2e test records live here (see [Data model §7](../database/DATA_MODEL.md)). Email goes to Mailpit. |
| **Automated tests** | A new PostgreSQL 16 container per test run (Testcontainers, database `test`), deleted afterwards | Generated | Only what each test creates. Tests cannot consume development or live document numbers; `DataModelIntegrationTest` checks it is running on the `test` container. |
| **Browser end-to-end runs** | Use a separate Compose project, so test runs never consume development invoice numbers: `docker compose -p sltours-e2e up --build`. This gives its own volumes; change the published ports if the dev stack is running. | Own `.env` | Disposable. Delete the project's volumes afterwards. |
| **Staging** (optional, before go-live) | A separate managed database or VPS database | Own `sltours_owner` / `sltours_app` / `sltours_readonly` passwords | A copy of production restored through the backup procedure, or synthetic data. Never real customers' contact details without need. |
| **Production** | See below | Restricted roles from `tools/db/roles.sql`; secrets only in the hosting platform | Live. `SPRING_PROFILES_ACTIVE=production`; the API refuses dev or test profiles, sample rates, the mock assistant and a local mail catcher. |

Production settings, beyond those in [Deployment](DEPLOYMENT.md):

```
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/sltours?sslmode=require
SPRING_DATASOURCE_USERNAME=sltours_app
SPRING_DATASOURCE_PASSWORD=<secret>
SPRING_FLYWAY_USER=sltours_owner
SPRING_FLYWAY_PASSWORD=<secret>
```

## Expected usage

SL Tours is a small operator: tens of enquiries and bookings a week, a handful of staff, and a few drivers using phones.

- The whole development database is about 0.3 MB as a dump today. Even years of records will stay well under 1 GB.
- Media files (inspection photos, PDFs) will grow faster than the database. Plan for a few GB a year.
- Load is light: one small database instance is plenty. **What matters is reliable backups, point-in-time recovery
  and someone else patching the server**, not performance.

## Current hosting

The existing sl-tours.com is a static site, published like GitHub Pages. There is no server or database to reuse,
so the database will be the first server-side component.

## Options

Prices change and depend on region and currency, so treat the figures below as rough orders of magnitude to check on
the provider's current price list before deciding. **Nothing has been provisioned.**

| Option | What you get | Rough monthly cost | Who does the work | Fit |
| --- | --- | --- | --- | --- |
| **A. One VPS running the Docker Compose stack** (e.g. a South African VPS host, or Hetzner/DigitalOcean in Europe) | Web, API and PostgreSQL on one 2 vCPU / 4 GB server. Backups via `tools/db/backup.sh`, copied to object storage. | Lowest: a small VPS plus a few rand for backup storage | You: OS updates, PostgreSQL upgrades, backups, restore tests, disk alerts | Cheapest and simplest to reason about, but the database shares a box with the app and recovery is only as good as the daily dump (up to 24 h of data loss). |
| **B. Managed PostgreSQL + a small app server** (e.g. DigitalOcean Managed PostgreSQL, or AWS RDS) | The provider runs the database: automatic daily backups, **point-in-time recovery**, minor-version patching, monitoring, encrypted storage. The app runs on a small VPS or container service in the same region. | Moderate: about the smallest managed database tier plus a small VPS | The provider handles the database; you handle the app server and the media backup | **Recommended.** Losing minutes rather than a day of bookings and payments is worth the extra monthly cost for a business that takes payments. |
| **C. AWS in Cape Town (`af-south-1`)**: RDS PostgreSQL + Lightsail or EC2 | As B, with data held in South Africa | Higher than B (af-south-1 is priced above other regions), and AWS is more complex to set up and run | Provider for the database; more setup work for you | Choose this if the business wants customer data kept in South Africa. POPIA allows transfers abroad with adequate safeguards, so this is a preference, not a requirement. |
| Serverless Postgres (Neon, Supabase) | Cheap or free small tiers with branching | Low | Provider | Not recommended as the primary: regions are far from SA, cold starts, and free-tier limits on backups and retention. Fine for staging. |

### Recommendation

**Option B**: a managed PostgreSQL 16 database at the smallest tier that includes daily backups and point-in-time
recovery, in the region closest to the app server. Set it up as follows:

- Run the API and web container on one small server in the **same region and private network**, with
  `sslmode=require`.
- Keep running `tools/db/backup.sh` daily (read-only role) and copy the output off-site. It backs up the **media
  files**, which the managed database does not, and gives a portable copy independent of the provider.
- Apply `tools/db/roles.sql`, check `btree_gist` support (see [Deployment](DEPLOYMENT.md)), and run the first restore
  test before go-live.

If budget is the deciding factor, **option A** is acceptable for launch provided the daily off-site backup, the
monthly restore check and the alerting in [Backup and recovery](BACKUP_AND_RECOVERY.md) are actually in place. Plan
to move to B once card or online payments start.

**Before provisioning, the owner needs to decide:**

1. Hosting budget per month.
2. Whether customer data must stay in South Africa (C) or may be held in Europe (A or B).
3. Who receives backup and uptime alerts.
4. The financial-records retention period, confirmed with the accountant.
