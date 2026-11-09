#!/usr/bin/env bash
# Backs up Stacc's MySQL database to a compressed, timestamped file. Reads MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE,
# MYSQL_USER, and MYSQL_PASSWORD from the environment; the password goes through a private option file, never the
# command line. Usage: scripts/backup.sh [output-directory]   (default: ./backups)
# Files in object storage (MinIO) are backed up separately: see docs/disaster-recovery.md.
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
# One consistent snapshot without locking the tables; routines and triggers included; no tablespace or GTID statements,
# so the dump restores into any server.
mysqldump --defaults-extra-file="$OPTIONS" --single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE" | gzip > "$FILE"
chmod 600 "$FILE"
echo "Backup written to $FILE"
