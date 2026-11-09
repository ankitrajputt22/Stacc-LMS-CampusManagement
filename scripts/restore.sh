#!/usr/bin/env bash
# Restores a backup made by scripts/backup.sh into an EMPTY database. It refuses a database that already has tables, so
# it can never overwrite live data. Reads MYSQL_HOST, MYSQL_PORT, MYSQL_USER, and MYSQL_PASSWORD from the environment.
# Usage: scripts/restore.sh <backup.sql.gz> <empty-database-name>
set -euo pipefail

FILE="${1:?Give the backup file}"
TARGET="${2:?Give the name of an empty database to restore into}"
: "${MYSQL_USER:?Set MYSQL_USER}" "${MYSQL_PASSWORD:?Set MYSQL_PASSWORD}"
HOST="${MYSQL_HOST:-localhost}"
PORT="${MYSQL_PORT:-3306}"

OPTIONS="$(mktemp)"
trap 'rm -f "$OPTIONS"' EXIT
chmod 600 "$OPTIONS"
printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s\n' "$HOST" "$PORT" "$MYSQL_USER" "$MYSQL_PASSWORD" > "$OPTIONS"

TABLES="$(mysql --defaults-extra-file="$OPTIONS" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$TARGET'")"
if [ "$TABLES" != "0" ]; then
  echo "Database $TARGET is not empty ($TABLES tables). Restore only into a new, empty database." >&2
  exit 1
fi
gunzip -c "$FILE" | mysql --defaults-extra-file="$OPTIONS" "$TARGET"
echo "Restored $FILE into $TARGET. Point MYSQL_DATABASE at it and start the backend; Flyway checks the schema version."
