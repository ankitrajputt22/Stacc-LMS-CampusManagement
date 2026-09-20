# Stacc Backend

The Stacc backend uses Java 21, Spring Boot, and Maven. It follows modular-monolith principles so that future business domains remain clearly separated within one application.

## Module structure

The single Spring Boot application is organized into logical modules under `com.stacc.backend`, including `auth`, `identity`, `academic`, `lms`, and the other domains described in `../docs/backend-architecture.md`. Each module owns its business logic, while the `common` module is reserved for small, genuinely shared technical code.

## MySQL connection

The backend includes Spring Data JPA and MySQL Connector/J. Its connection configuration reads these environment variables:

- `MYSQL_HOST` (defaults to `localhost`)
- `MYSQL_PORT` (defaults to `3306`)
- `MYSQL_DATABASE` (defaults to `stacc`)
- `MYSQL_USER`
- `MYSQL_PASSWORD`

Set the username and password in the local environment before running the application. Never commit real credentials.

Flyway manages database schema changes. Versioned migrations live in `src/main/resources/db/migration` and must use names such as `V1__baseline.sql`. Do not make schema changes manually or edit migrations already applied to a shared database; add a new migration instead.

Hibernate automatic schema management remains disabled.

Run the backend tests with:

```bash
mvn test
```
