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

The backend can also run in Docker together with MySQL and the frontend. `backend/Dockerfile` builds the image, and the root `compose.yaml` supplies these variables. See `../docs/docker.md`.

Flyway manages database schema changes. Versioned migrations live in `src/main/resources/db/migration` and must use names such as `V1__baseline.sql`. Do not make schema changes manually or edit migrations already applied to a shared database; add a new migration instead.

Hibernate automatic schema management remains disabled.

## User accounts

The `auth.account` package holds the first persistent model: `UserAccount`, stored in the `user_accounts` table. It keeps a unique login ID, a password hash, and an account status. See `../docs/user-accounts.md`.

The `auth.role` package holds the `Role` model. An account can hold several roles through the `user_account_roles` table, and the initial roles are `STUDENT`, `FACULTY`, and `ADMIN`. See `../docs/account-roles.md`.

The `auth.permission` package holds the `Permission` model, stored in the `permissions` table. A role can hold several permissions through the `role_permissions` table. The first real permissions are `LMS_COURSE_VIEW`, held by `STUDENT`, and `FACULTY_COURSE_VIEW`, held by `FACULTY`. See `../docs/permissions.md`.

## Security

Spring Security is set up in `auth.security.SecurityConfig`. Everything under `/api/` requires a valid access token, except `POST /api/auth/login`; the Swagger documentation is public during development. Spring's built-in login page and HTTP Basic are switched off, and the API is stateless. See `../docs/api-security.md`. Operations can require a role with `@PreAuthorize("hasRole('ADMIN')")` or a permission with `hasAuthority(...)`; a signed-in account without it gets HTTP 403. See `../docs/authorization.md`.

A browser page on another origin may call the API only if its origin is listed in `FRONTEND_ORIGIN` (default `http://localhost:5173`, the frontend dev server). Wildcards are refused at startup. See `../docs/frontend-authentication.md`.

Passwords are hashed with the shared `PasswordEncoder` bean from `SecurityConfig`. See `../docs/password-security.md`.

Accounts are connected to Spring Security through `StaccUserDetailsService` and `StaccUserPrincipal`, and `SecurityConfig` provides the `AuthenticationManager`. See `../docs/authentication-foundation.md`.

The college login is `POST /api/auth/login` in `auth.api.AuthController`. It checks a login ID and password and returns a short-lived JWT access token. See `../docs/authentication.md` and `../docs/access-tokens.md`.

The backend needs two more environment variables for tokens:

- `JWT_SECRET` — required. Base64 text for at least 32 random bytes, for example from `openssl rand -base64 48`. The backend does not start without it.
- `JWT_ACCESS_TOKEN_MINUTES` (defaults to `15`)

Clients send the token on later requests as `Authorization: Bearer <access-token>`.

## Academic structure

The `academic` module holds the college's academic master data. So far it contains `Department`, `Program` (each program belongs to one department), the college-wide `AcademicSession`, `Semester` (one numbered semester of a program in a session), `Course` (the permanent course catalog), and `CourseOffering` (one course offered in one semester). The `identity` module holds `StudentProfile`, which links a student's `UserAccount` to a program and to the session of admission, and `FacultyProfile`, which links a faculty member's `UserAccount` to their primary department. In the `academic` module, `SemesterEnrollment` records a student's official participation in a semester, `CourseEnrollment` records participation in a course offering, and `TeachingAssignment` records that a faculty member teaches a course offering. There is no API for any of them yet. See `../docs/academic-structure.md`.

## LMS

The `lms` module holds `LmsCourse`, the learning space for one official course offering, and `LmsStudentMembership`, the LMS-side membership for one official course enrollment. Later LMS features will build on them. `LmsStudentMembershipProvisioningService` creates a membership from an official course enrollment that is still enrolled, and can make one inactive. It is called explicitly; nothing runs it automatically. `LmsStudentAccessService` decides on the backend whether a signed-in student may currently use an LMS course, from official ERP enrollment and LMS state. `GET /api/lms/my-courses` and `GET /api/lms/courses/{lmsCourseId}` return the signed-in student's current LMS courses under that same policy; they need the `STUDENT` role and the `LMS_COURSE_VIEW` permission. `LmsFacultyAccessService` decides faculty access from official teaching assignments, with no separate faculty membership, and `GET /api/lms/faculty/my-courses` returns the courses a faculty member teaches; it needs the `FACULTY` role and the `FACULTY_COURSE_VIEW` permission. The `lms.content` package holds course content: `LmsModule` and `LmsTopic`, with a draft, published, and archived lifecycle and no deletion. Students read published content through `GET /api/lms/courses/{lmsCourseId}/content`; faculty manage it through the `/api/lms/faculty/...` content routes with the `LMS_CONTENT_MANAGE` permission, and `LmsContentManagementService` checks their access for every change. See `../docs/lms-foundation.md`.

Topics can hold study materials (`LmsStudyMaterial`). Their details are stored in MySQL and the files in private object storage, behind `ObjectStorageService` in `common.storage`; `MinioObjectStorageService` implements it for MinIO. Uploads are checked against an allow-list of academic file types and a 25 MB limit, and downloads go through `GET /api/lms/materials/{materialId}/download`, which checks access and always sends an attachment. Set `STACC_STORAGE_ENDPOINT`, `STACC_STORAGE_ACCESS_KEY`, `STACC_STORAGE_SECRET_KEY`, and `STACC_STORAGE_BUCKET` to use them; the backend starts without storage, but uploads and downloads then answer that storage is unavailable. See `../docs/lms-foundation.md` and `../docs/docker.md`.

The `lms.announcement` package holds `LmsAnnouncement`, a course-wide plain-text update with the same draft, published, and archived lifecycle and no deletion. Students read the published ones through `GET /api/lms/courses/{lmsCourseId}/announcements`; faculty manage them through the `/api/lms/faculty/...` announcement routes with the existing `LMS_CONTENT_MANAGE` permission, and `LmsAnnouncementManagementService` checks their access for every change. See `../docs/lms-foundation.md`.

The `lms.assignment` package holds `LmsAssignment`, the definition of a piece of work in one LMS course: a title, plain-text instructions, a UTC deadline (`Instant`), and maximum marks (`BigDecimal`), with the same lifecycle and no deletion. Published assignments stay visible after their deadline. Students read them through `GET /api/lms/courses/{lmsCourseId}/assignments` and `/assignments/{assignmentId}`; faculty read them per course and across their courses (`GET /api/lms/faculty/my-assignments`), and change them through `LmsAssignmentManagementService` with the new `LMS_ASSIGNMENT_MANAGE` permission. There are no submissions or grades yet. See `../docs/lms-foundation.md`.

## API validation and errors

The `common.error` package provides shared exception handling and a consistent API error response. Request DTOs should use Jakarta Validation annotations for input validation.

## API documentation

OpenAPI documentation is configured for the backend. During local development, Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`, and the OpenAPI JSON is available at `http://localhost:8080/v3/api-docs`.

Run the backend tests with:

```bash
mvn test
```
