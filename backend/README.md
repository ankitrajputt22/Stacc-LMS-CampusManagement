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

## User accounts

The `auth.account` package holds the first persistent model: `UserAccount`, stored in the `user_accounts` table. It keeps a unique login ID, a password hash, and an account status. See `../docs/user-accounts.md`.

The `auth.role` package holds the `Role` model. An account can hold several roles through the `user_account_roles` table, and the initial roles are `STUDENT`, `FACULTY`, and `ADMIN`. See `../docs/account-roles.md`.

The `auth.permission` package holds the `Permission` model, stored in the `permissions` table. A role can hold several permissions through the `role_permissions` table, but no permissions or mappings exist yet. See `../docs/permissions.md`.

## Security

Spring Security is set up in `auth.security.SecurityConfig`. For now it is a foundation only: all routes are temporarily open, Spring's built-in login page and HTTP Basic are switched off, and the API is stateless. Login and authorization checks are not implemented yet. See `../docs/security-foundation.md`.

Passwords are hashed with the shared `PasswordEncoder` bean from `SecurityConfig`. See `../docs/password-security.md`.

Accounts are connected to Spring Security through `StaccUserDetailsService` and `StaccUserPrincipal`, and `SecurityConfig` provides an `AuthenticationManager` for the future sign-in endpoint. See `../docs/authentication-foundation.md`.

## API validation and errors

The `common.error` package provides shared exception handling and a consistent API error response. Request DTOs should use Jakarta Validation annotations for input validation.

## API documentation

OpenAPI documentation is configured for the backend. During local development, Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`, and the OpenAPI JSON is available at `http://localhost:8080/v3/api-docs`.

Run the backend tests with:

```bash
mvn test
```
