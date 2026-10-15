#!/usr/bin/env bash
# Backs up the SL Tours database and private media together, so a restore has matching rows and files.
#
#   tools/db/backup.sh [backup-root]          (default root: ./backups, or $SLTOURS_BACKUP_ROOT)
#
# Database source:
#   - DATABASE_URL set (postgresql://user:pass@host:port/db): pg_dump runs locally against that server (production).
#   - otherwise: the local Docker Compose "postgres" service.
# Media source: SLTOURS_MEDIA_DIR if set, otherwise the Compose "api" service's /var/lib/sltours/media.
#
# Output: <root>/<UTC timestamp>/ with database.dump (pg_dump custom format), media.tar.gz, manifest.txt (row counts,
# Flyway version, sizes) and SHA256SUMS. Old backups are pruned by KEEP_DAILY (default 14) and KEEP_MONTHLY (default 12):
# the newest backup of each calendar month is kept as the monthly copy.
set -euo pipefail
ROOT="${1:-${SLTOURS_BACKUP_ROOT:-./backups}}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DIR="$ROOT/$STAMP"
KEEP_DAILY="${KEEP_DAILY:-14}"
KEEP_MONTHLY="${KEEP_MONTHLY:-12}"
mkdir -p "$DIR"
umask 077

psql_cmd() {
  if [[ -n "${DATABASE_URL:-}" ]]; then psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -tA "$@"
  else docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -tA "$@"' -- "$@"; fi
}

echo "Backing up database…"
if [[ -n "${DATABASE_URL:-}" ]]; then
  pg_dump "$DATABASE_URL" --format=custom --no-owner --no-privileges --file "$DIR/database.dump"
else
  docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges' > "$DIR/database.dump"
fi

echo "Backing up media…"
if [[ -n "${SLTOURS_MEDIA_DIR:-}" ]]; then
  tar -C "$SLTOURS_MEDIA_DIR" -czf "$DIR/media.tar.gz" .
else
  docker compose exec -T api sh -c 'tar -C /var/lib/sltours/media -czf - .' > "$DIR/media.tar.gz"
fi

{
  echo "created_utc=$STAMP"
  echo "flyway_version=$(psql_cmd -c "select max(version::int) from flyway_schema_history where success")"
  echo "database_bytes=$(stat -c %s "$DIR/database.dump")"
  echo "media_bytes=$(stat -c %s "$DIR/media.tar.gz")"
  echo "media_files_db=$(psql_cmd -c "select count(*) from media_files where deleted_at is null")"
  for t in user_accounts customers enquiries bookings billing_documents billing_payments payments assignments vehicle_inspections media_files admin_audit_events; do
    echo "rows.$t=$(psql_cmd -c "select count(*) from $t" 2>/dev/null || echo missing)"
  done
  echo "numbers=$(psql_cmd -c "select string_agg(kind || ':' || next_number, ',' order by kind) from document_sequences")"
} > "$DIR/manifest.txt"
(cd "$DIR" && sha256sum database.dump media.tar.gz manifest.txt > SHA256SUMS)

# Retention: newest KEEP_DAILY backups, plus the newest backup of each of the last KEEP_MONTHLY months.
mapfile -t ALL < <(ls -1d "$ROOT"/20*Z 2>/dev/null | sort -r)
declare -A KEEP=()
for d in "${ALL[@]:0:$KEEP_DAILY}"; do KEEP["$d"]=1; done
declare -A MONTH=()
for d in "${ALL[@]}"; do
  m="$(basename "$d" | cut -c1-6)"
  if [[ -z "${MONTH[$m]:-}" && ${#MONTH[@]} -lt $KEEP_MONTHLY ]]; then MONTH[$m]=1; KEEP["$d"]=1; fi
done
for d in "${ALL[@]}"; do [[ -n "${KEEP[$d]:-}" ]] || { echo "Pruning $d"; rm -rf -- "$d"; }; done

echo "Backup written to $DIR"
cat "$DIR/manifest.txt"
