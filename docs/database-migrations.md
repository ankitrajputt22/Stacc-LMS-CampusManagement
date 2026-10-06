# Database Migrations

Stacc uses Flyway for database schema changes. Migration files live in `backend/src/main/resources/db/migration` and run in version order.

Use simple versioned names with two underscores between the version and description. For example:

- `V1__baseline.sql`
- `V2__create_user_accounts.sql`
- `V3__create_roles.sql`

`V1__baseline.sql` and `V2__create_user_accounts.sql` exist now; the last name is only an example for a later phase.

Do not make schema changes manually. Hibernate automatic schema updates remain disabled so that Flyway owns the schema history.

Never edit a migration that has already been applied to a shared database. Create a new migration for every later change.
