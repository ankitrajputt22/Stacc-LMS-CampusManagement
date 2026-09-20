# Database Migrations

Stacc uses Flyway for database schema changes. Migration files live in `backend/src/main/resources/db/migration` and run in version order.

Use simple versioned names with two underscores between the version and description. For example:

- `V1__baseline.sql`
- `V2__create_users.sql`
- `V3__create_roles.sql`

Only `V1__baseline.sql` exists now; the other names are examples for later phases.

Do not make schema changes manually. Hibernate automatic schema updates remain disabled so that Flyway owns the schema history.

Never edit a migration that has already been applied to a shared database. Create a new migration for every later change.
