# Backup and recovery

The database and the private media files (inspection photos, driver documents, proof of payment, issued PDFs) are backed
up **together**, because each needs the other. The scripts are in `tools/db/` and were tested on 5 October 2026 (see
§6).

| Script | What it does |
| --- | --- |
| `backup.sh [root]` | `pg_dump` (custom format) plus a media archive, a manifest (Flyway version, row counts, next document numbers) and SHA-256 checksums, written to `<root>/<UTC timestamp>/`. Prunes old backups (§2). |
| `verify-restore.sh <backup>` | Restores into a throwaway PostgreSQL 16 container with no published port, then compares row counts, the Flyway version and the numbering with the manifest, and checks that every `media_files` row has its file. Exit code 0 means verified. |
| `restore.sh <backup> [media-dir]` | Restores into an **empty** database (and an empty media directory). Refuses non-empty targets and bad checksums. |
| `check-backup.sh [root]` | Monitoring check. Exits non-zero if the newest backup is missing, older than 26 h, fails its checksums or is suspiciously small. |
| `roles.sql` | Creates the restricted database roles (§4). |
| `dev-records-review.sql` / `dev-records-cleanup.sql` | Development-record report and the prepared cleanup ([Data model §7](../database/DATA_MODEL.md)). |

Source selection:

- **Local Docker:** with no variables set, the scripts use the Compose `postgres` and `api` services.
- **Production:** set `DATABASE_URL` (the read-only role is enough for `pg_dump`) and `SLTOURS_MEDIA_DIR`.

## 1. Schedule

| What | When | Where |
| --- | --- | --- |
| `backup.sh` | Daily, 02:00 SAST | Local disk of the backup host, then copied **off the server** to object storage in another location, encrypted (e.g. `rclone` with crypt, or the provider's server-side encryption). |
| `check-backup.sh` | Daily, 06:00 | Alert by email or WhatsApp on failure (cron `MAILTO`, or an uptime/heartbeat monitor). |
| `verify-restore.sh` on the newest backup | Monthly, plus after any change to the database setup | Record the result in the operations log. |
| Managed provider snapshots and point-in-time recovery | Continuous (if a managed database is used) | Provider. These are **in addition to** the `pg_dump` backups, which are portable and include the media files. |

Example crontab on the backup host:

```cron
0 2 * * *  cd /srv/sl-tours && DATABASE_URL=… SLTOURS_MEDIA_DIR=/srv/sl-tours/media tools/db/backup.sh /srv/backups && rclone sync /srv/backups remote:sl-tours-backups
0 6 * * *  /srv/sl-tours/tools/db/check-backup.sh /srv/backups || /usr/local/bin/notify "SL Tours backup check failed"
0 3 1 * *  /srv/sl-tours/tools/db/verify-restore.sh /srv/backups/$(ls /srv/backups | tail -1) || /usr/local/bin/notify "SL Tours restore check failed"
```

## 2. Retention

- **Backups:** `backup.sh` keeps the newest 14 daily backups and the newest backup of each of the last 12 months
  (`KEEP_DAILY`, `KEEP_MONTHLY`). Keep a yearly copy for 5 years off-site.
- **Financial records:** invoices, receipts and payments are kept for at least **5 years** after the tax period. This
  follows the Tax Administration Act's 5-year minimum; confirm the period with SL Tours' accountant.
- **Records the business may decide to remove** (decision D5 in the data model): unsuccessful enquiries after about
  2 years, unverified or disabled customer logins after 1 year without activity, and inspection photos after the
  vehicle-claim period.
  - Nothing is deleted automatically today.
  - Removal must be a reviewed, backed-up job, like the development-record cleanup.
  - Removing a customer means anonymising their contact fields, not deleting rows that issued documents refer to.

## 3. Restoring

1. **Stop writes:** put the site into maintenance, or stop the `api` container.
2. **Pick the backup** and verify it: `tools/db/verify-restore.sh <backup>`.
3. **Create a new, empty database** (never restore over the live one), then
   `TARGET_DATABASE_URL=… tools/db/restore.sh <backup> <new-media-dir>`.
4. **Point the API at the new database and media directory, and start it.** Flyway validates the schema version on
   start (it refuses a mismatch).
5. **Check:**
   - sign in as an administrator;
   - open the latest invoice and its PDF;
   - check that the numbering in **Billing settings** matches the manifest's `numbers=` line.
6. **Keep the old database** until the business confirms nothing is missing.

If only media is lost, restore `media.tar.gz` from the same backup into the media volume. `verify-restore.sh` reports
any rows whose file is missing.

## 4. Database credentials

`roles.sql` creates three roles. Use them for every shared or production environment.

| Role | Used by | Can |
| --- | --- | --- |
| `sltours_owner` | Flyway at deploy time only (`SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD`) | Own and change the schema |
| `sltours_app` | The running API (`SPRING_DATASOURCE_USERNAME` / `_PASSWORD`) | Read and write rows. It cannot change the schema or delete customers, enquiries, bookings, assignments, inspections, documents, payments or numbering, and can only **add** to `admin_audit_events`, `billing_events`, `customer_merges`, `payments` and `referral_attribution_changes`. |
| `sltours_readonly` | Backups and reporting | Read only |

Run it again after a migration that adds tables, so the narrowing applies to them. Passwords go in the hosting
platform's secret store, never in Git, and are different per environment. The local Docker setup keeps the single
development user for convenience.

## 5. Monitoring

| What | How |
| --- | --- |
| API and database up | `GET /actuator/health` returns `{"status":"UP"}` only when the database answers. Point an uptime monitor at it every minute. |
| Backups | `check-backup.sh` daily, alerting on a non-zero exit. |
| Restores | Monthly `verify-restore.sh`. |
| Disk | Alert at 80% on the database and media volumes. |
| Managed database | Enable the provider's alerts for CPU, storage, connections and failed backups. |

## 6. Verification on 5 October 2026 (local development database)

| Check | Result |
| --- | --- |
| `backup.sh` before V25 | 324 KB dump + 1.3 MB media; manifest at Flyway 24 |
| `verify-restore.sh` (isolated container) | All row counts, Flyway version, numbering (INVOICE 100106, QUOTATION 1002, RECEIPT 1002) and all 31 media files matched |
| V25 on a restored copy before the live dev database | Applied in one transaction: 19 customers created, 4 linked to verified logins, all bookings and enquiries linked |
| `backup.sh` + `verify-restore.sh` after V25 | Matched at Flyway 25 |
| `roles.sql` on a restored copy | `sltours_app` refused `DELETE` on `billing_documents` and `bookings`, `UPDATE` on `admin_audit_events`, `CREATE TABLE` and `DROP TABLE`; `sltours_readonly` refused writes and produced a full `pg_dump` |
| The real API image on the restored copy, as `sltours_app` (Flyway as `sltours_owner`) | Started, validated 25 migrations, health UP; a guest enquiry was stored and linked to its customer by the trigger |
| `check-backup.sh` | OK on the newest backup |

Not yet tested: off-site copy, alerting and production credentials. They depend on the hosting choice
([Production database](PRODUCTION_DATABASE.md)).
