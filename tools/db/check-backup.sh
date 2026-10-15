#!/usr/bin/env bash
# Monitoring check: exits non-zero (and prints why) when the newest backup is missing, older than MAX_AGE_HOURS
# (default 26), fails its checksums, or is suspiciously small. Wire it to the scheduler's alerting or an uptime monitor.
#
#   tools/db/check-backup.sh [backup-root]
set -euo pipefail
ROOT="${1:-${SLTOURS_BACKUP_ROOT:-./backups}}"
MAX_AGE_HOURS="${MAX_AGE_HOURS:-26}"
latest="$(ls -1d "$ROOT"/20*Z 2>/dev/null | sort | tail -1 || true)"
[[ -n "$latest" ]] || { echo "CRITICAL: no backups in $ROOT"; exit 2; }
age=$(( ( $(date -u +%s) - $(stat -c %Y "$latest/manifest.txt") ) / 3600 ))
(( age <= MAX_AGE_HOURS )) || { echo "CRITICAL: newest backup $latest is ${age}h old"; exit 2; }
(cd "$latest" && sha256sum --quiet -c SHA256SUMS) || { echo "CRITICAL: checksum failure in $latest"; exit 2; }
size=$(stat -c %s "$latest/database.dump")
(( size > 10000 )) || { echo "CRITICAL: database dump in $latest is only $size bytes"; exit 2; }
echo "OK: $latest (${age}h old, database ${size} bytes)"
