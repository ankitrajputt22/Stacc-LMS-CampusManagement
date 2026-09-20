# MySQL Setup

Stacc uses MySQL as its primary database. The local development defaults are:

- **Host:** `localhost`
- **Port:** `3306`
- **Database:** `stacc`
- **Character set:** `utf8mb4`

Keep real database credentials in local environment configuration. Never commit usernames, passwords, or other secrets. The repository's `.env.example` lists the supported placeholders, while a real `.env` file remains ignored by Git.

Phase 6 will connect the Spring Boot application to MySQL. Phase 7 will introduce Flyway migrations. This phase does not create application tables or migrations.

## Useful commands

Check the installed MySQL version:

```bash
mysql --version
```

Open MySQL with a valid local account:

```bash
mysql -u <username> -p
```

Create the development database from the MySQL prompt:

```sql
CREATE DATABASE IF NOT EXISTS stacc
CHARACTER SET utf8mb4;
```

List available databases:

```sql
SHOW DATABASES;
```

Select the Stacc database:

```sql
USE stacc;
```

The MySQL server must be running before these commands can connect. Use the service-management method provided by the local MySQL installation, then authenticate with a valid account. Do not place the password directly in a command or documentation.
