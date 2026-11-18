#!/usr/bin/env bash
set -euo pipefail

: "${MYSQL_DATABASE:?Set MYSQL_DATABASE}" "${MYSQL_USER:?Set MYSQL_USER}" "${MYSQL_PASSWORD:?Set MYSQL_PASSWORD}"
HOST="${MYSQL_HOST:-localhost}"
PORT="${MYSQL_PORT:-3306}"
OUT_DIR="${1:-./backups}"
mkdir -p "$OUT_DIR"

OPTIONS="$(mktemp)"
trap 'rm -f "$OPTIONS"' EXIT
chmod 600 "$OPTIONS"
printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s\n' "$HOST" "$PORT" "$MYSQL_USER" "$MYSQL_PASSWORD" > "$OPTIONS"

FILE="$OUT_DIR/stacc-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
mysqldump --defaults-extra-file="$OPTIONS" --single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE" | gzip > "$FILE"
chmod 600 "$FILE"
echo "Backup written to $FILE"
