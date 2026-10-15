#!/usr/bin/env bash
# Restores a backup made by backup.sh into an EMPTY database, and its media into an empty directory.
#
#   TARGET_DATABASE_URL=postgresql://owner:pass@host:5432/sltours_restore tools/db/restore.sh <backup-dir> [media-dir]
#
# Refuses to run when the checksums do not match or the target database already has tables. It never restores over a
# live database: restore into a new database, check it (verify-restore.sh does this automatically), then switch the
# application's connection to it.
set -euo pipefail
DIR="${1:?backup directory}"
MEDIA_DIR="${2:-}"
: "${TARGET_DATABASE_URL:?set TARGET_DATABASE_URL to an empty database}"
(cd "$DIR" && sha256sum --quiet -c SHA256SUMS) || { echo "Checksum mismatch in $DIR; not restoring" >&2; exit 2; }
existing="$(psql "$TARGET_DATABASE_URL" -tA -c "select count(*) from information_schema.tables where table_schema = 'public'")"
[[ "$existing" == "0" ]] || { echo "Target database is not empty ($existing tables); not restoring" >&2; exit 3; }
pg_restore --dbname "$TARGET_DATABASE_URL" --no-owner --no-privileges --exit-on-error "$DIR/database.dump"
if [[ -n "$MEDIA_DIR" ]]; then
  mkdir -p "$MEDIA_DIR"
  [[ -z "$(ls -A "$MEDIA_DIR")" ]] || { echo "Media directory $MEDIA_DIR is not empty; database restored, media not" >&2; exit 4; }
  tar -C "$MEDIA_DIR" -xzf "$DIR/media.tar.gz"
fi
echo "Restored $DIR"
