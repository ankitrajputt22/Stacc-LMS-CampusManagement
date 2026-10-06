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

Spring Security is set up in `auth.security.SecurityConfig`. Everything under `/api/` requires a valid access token, except `POST /api/auth/login`; the Swagger documentation is public during development. Spring's built-in login page and HTTP Basic are switched off, and the API is stateless. See `../docs/api-security.md`. Operations can require a role with `@PreAuthorize("hasRole('ADMIN')")` or a permission with `hasAuthority(...)`; a signed-in account without it gets HTTP 403. No real endpoint has such a rule yet. See `../docs/authorization.md`.

Passwords are hashed with the shared `PasswordEncoder` bean from `SecurityConfig`. See `../docs/password-security.md`.

Accounts are connected to Spring Security through `StaccUserDetailsService` and `StaccUserPrincipal`, and `SecurityConfig` provides the `AuthenticationManager`. See `../docs/authentication-foundation.md`.

The college login is `POST /api/auth/login` in `auth.api.AuthController`. It checks a login ID and password and returns a short-lived JWT access token. See `../docs/authentication.md` and `../docs/access-tokens.md`.

The backend needs two more environment variables for tokens:

- `JWT_SECRET` — required. Base64 text for at least 32 random bytes, for example from `openssl rand -base64 48`. The backend does not start without it.
- `JWT_ACCESS_TOKEN_MINUTES` (defaults to `15`)

Clients send the token on later requests as `Authorization: Bearer <access-token>`.

## Academic structure

The `academic` module holds the college's academic master data. So far it contains `Department`, `Program` (each program belongs to one department), the college-wide `AcademicSession`, `Semester` (one numbered semester of a program in a session), and `Course` (the permanent course catalog). There is no API for them yet. See `../docs/academic-structure.md`.

## API validation and errors

The `common.error` package provides shared exception handling and a consistent API error response. Request DTOs should use Jakarta Validation annotations for input validation.

## API documentation

OpenAPI documentation is configured for the backend. During local development, Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`, and the OpenAPI JSON is available at `http://localhost:8080/v3/api-docs`.

Run the backend tests with:

```bash
mvn test
```
