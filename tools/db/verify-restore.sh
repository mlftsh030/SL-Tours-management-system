#!/usr/bin/env bash
# Proves a backup can be restored: starts a throwaway PostgreSQL 16 container (no published port, removed afterwards),
# restores the dump and media into it, and compares row counts and the Flyway version with the backup's manifest.
# Exit code 0 means the restore matched. Run it on a schedule (monthly) and after any change to the backup process.
#
#   tools/db/verify-restore.sh <backup-dir>
set -euo pipefail
DIR="$(cd "${1:?backup directory}" && pwd)"
NAME="sltours-restore-check-$$"
WORK="$(mktemp -d)"
cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; rm -rf "$WORK"; }
trap cleanup EXIT
(cd "$DIR" && sha256sum --quiet -c SHA256SUMS)
docker run -d --name "$NAME" -e POSTGRES_PASSWORD=restore-check -e POSTGRES_DB=sltours_restore -v "$DIR:/backup:ro" postgres:16-alpine >/dev/null
for i in $(seq 1 30); do docker exec "$NAME" pg_isready -U postgres -d sltours_restore >/dev/null 2>&1 && break; sleep 1; done
sleep 2
docker exec "$NAME" pg_restore -U postgres -d sltours_restore --no-owner --no-privileges --exit-on-error /backup/database.dump
q() { docker exec "$NAME" psql -U postgres -d sltours_restore -tA -c "$1"; }
status=0
expect() { local key="$1" actual="$2"; local want; want="$(grep "^$key=" "$DIR/manifest.txt" | cut -d= -f2-)";
  if [[ "$want" == "$actual" ]]; then echo "ok    $key=$actual"; else echo "FAIL  $key: backup $want, restored $actual"; status=1; fi; }
expect flyway_version "$(q "select max(version::int) from flyway_schema_history where success")"
for t in user_accounts customers enquiries bookings billing_documents billing_payments payments assignments vehicle_inspections media_files admin_audit_events; do
  expect "rows.$t" "$(q "select count(*) from $t" 2>/dev/null || echo missing)"
done
expect numbers "$(q "select string_agg(kind || ':' || next_number, ',' order by kind) from document_sequences")"
# Every stored file the database refers to must be in the media archive.
mkdir -p "$WORK/media" && tar -C "$WORK/media" -xzf "$DIR/media.tar.gz"
missing=0
while read -r key; do [[ -z "$key" || -f "$WORK/media/$key" ]] || { echo "missing file: $key"; missing=$((missing + 1)); }; done < <(q "select storage_key from media_files where deleted_at is null")
if [[ $missing -eq 0 ]]; then echo "ok    every media_files row has its file"; else echo "FAIL  $missing media files missing"; status=1; fi
[[ $status -eq 0 ]] && echo "Restore verified for $DIR" || echo "Restore check FAILED for $DIR"
exit $status
