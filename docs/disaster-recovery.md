# Backups and Disaster Recovery

Stacc keeps its data in two places: the MySQL database (every record) and the private object storage bucket (uploaded
files, issued certificates, resumes, and exports). Back up both.

## Database

```bash
MYSQL_HOST=... MYSQL_PORT=3306 MYSQL_DATABASE=stacc MYSQL_USER=... MYSQL_PASSWORD=... scripts/backup.sh /secure/backups
```

- **What it makes:** `stacc-<UTC time>.sql.gz`, readable only by its owner. It is one consistent snapshot (`--single-transaction`) taken without locking tables, with no GTID or tablespace statements.
- **The password:** it travels through a temporary private option file and never appears on the command line.
- **How often:** run it daily (for example from cron), keep at least 14 days, and copy the files off the machine.

## Files

Mirror the bucket named in `STACC_STORAGE_BUCKET` with the MinIO client, for example
`mc mirror --overwrite stacc/stacc-materials /secure/backups/files`. Keys are server-made and stable, so a mirror
restores files exactly.

## Restoring

1. Create a **new, empty** database and give the application user rights on it.
2. Restore into it with `scripts/restore.sh <backup.sql.gz> <new-database>`. The script refuses a database that already has tables, so live data is never overwritten.
3. Mirror the files back into the bucket.
4. Point `MYSQL_DATABASE` at the restored database and start the backend. Flyway sees the schema version and adds only the migrations made after the backup.

Tested on 2026-11-11 with the full demo data: the backup restored into a new database with every row and all 101 migrations recorded. A second restore into the same database was refused.

## If the server is lost

1. Bring up a new host with the production setup (`docs/docker.md`, `docs/configuration.md`).
2. Restore the latest database backup and file mirror as above.
3. Set the same `JWT_SECRET`, or everyone signs in again (sessions do not survive a new secret).
4. Check the health endpoint (`/actuator/health/readiness`) and sign in with an admin account.
