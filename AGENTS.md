# Stacc Repository Instructions

- Stacc uses MySQL. Treat older references to PostgreSQL as outdated unless a current phase explicitly changes this decision.
- Use a modular-monolith architecture with clear domain boundaries.
- Place backend code inside the correct Stacc module. Keep `common` small, and avoid cross-module dependencies without a clear need.
- The frontend uses React and TypeScript; the backend uses Java and Spring Boot.
- Work only on the currently requested development phase. Do not implement future features early.
- Inspect the repository before editing and preserve existing working functionality.
- Do not refactor unrelated code without a clear phase-related reason.
- Never hard-code credentials or secrets. Use environment configuration instead.
- Enforce permissions on the backend; frontend visibility is not a security boundary.
- Never store plaintext passwords. Store only password hashes, and never log or expose raw passwords or hashes.
- Hash and check passwords only with the shared `PasswordEncoder`. Do not compare password strings yourself or write custom hashing code.
- Keep `UserAccount` separate from student and faculty profile data.
- Do not expose JPA entities such as `UserAccount` directly through APIs.
- Disable an account through its status. Do not delete accounts just to remove access.
- Roles are assigned by the college through the backend. Users never choose their own role.
- An account can hold several roles. Do not assume it has only one.
- `Role` rows are shared reference data. Never create or delete them as a side effect of changing an account.
- Permission codes use `UPPERCASE_WITH_UNDERSCORES`. Add a permission only when a real feature needs it, through a migration.
- A role can hold several permissions, and a permission can be shared by several roles. Assign a permission to roles only when its protected feature exists.
- Roles and permissions are shared reference data. Do not use cascading deletes on their relationships.
- Users never grant themselves roles or permissions.
- Keep all Spring Security settings in the shared `SecurityConfig`. Do not add separate security filter chains for individual modules without a real need.
- Every route under `/api/` requires authentication by default. Make a route public only deliberately, with a clear reason, by listing it in `SecurityConfig`. The login stays public.
- Check roles with `hasRole` or `hasAnyRole` and permissions with `hasAuthority` or `hasAnyAuthority`. Add a rule only when a real feature needs it.
- A permission's code is its authority name. Never add `ROLE_`, `SCOPE_`, or any other prefix to it, and never start a permission code with `ROLE_`.
- There is no role hierarchy and no automatic admin pass, for roles or for permissions. Do not add either without an explicit design decision.
- Do not build a custom permission engine or guess permission constants. Use Spring Security's checks.
- Do not use Spring's generated login page or HTTP Basic, and never add built-in, demo, or hard-coded users. Authentication must use `UserAccount`.
- Sign-in goes through the shared `AuthenticationManager`, with `loginId` as the identifier. Roles map to `ROLE_*` authorities, and permission codes are used as authorities unchanged.
- There is one central college login (`POST /api/auth/login`) and no public signup. The login request never carries a role.
- Failed logins always return the same generic 401 message. Never reveal whether a login ID exists, and never log passwords or login requests.
- For an expected failure with a specific status, throw `ApiException` with a client-safe message.
- Create access tokens only through the shared `AccessTokenService`. Keep them short-lived, and never put passwords, hashes, or private data in token claims.
- Never hard-code, log, or expose the JWT signing secret, and never log access tokens. Do not add refresh tokens until their own phase.
- Access tokens are accepted only in the `Authorization: Bearer` header. Never put tokens in URLs.
- Token authorities are used unchanged, with no `SCOPE_` prefix. Do not query the database on every token request unless a later phase explicitly requires it.
- Keep frontend and backend API contracts consistent.
- Frontend API calls use the shared `apiClient` in `frontend/src/api/apiClient.ts`. Do not hard-code backend URLs in components or pages.
- Do not put secrets in `VITE_` variables; they are public in the built frontend.
- Add feature API logic only when that feature is implemented.
- Use the shared API error format and Jakarta Validation for request DTOs. Do not expose stack traces or internal database errors, and do not create module-specific error formats.
- Keep new REST APIs visible in OpenAPI with simple, useful descriptions. Do not create fake endpoints for Swagger, and do not treat API documentation as an authorization control.
- Use Canvas LMS as a UX reference without copying its branding, assets, or exact design.
- Maintain a coherent Stacc identity across the UI. Google Stitch may provide approved design references.
- Frontend pages reuse the shared `AppLayout`. Do not create separate global navigation for each module.
- Reuse `LoadingState`, `EmptyState`, and `ErrorState` for page states.
- Avoid generic dashboard-template styling such as heavy gradients, glass effects, glows, and decorative animation.
- App-wide backend tests extend `DatabaseFreeApiTest` and run without a database. When a new service needs repositories, list them there as stand-ins, or those tests cannot start.
- Keep commit messages simple and human-readable.

## Academic Data

- Academic master data belongs in the `academic` module. The ERP side is the source of truth for academic structure and enrollment.
- Keep academic history. Make a record inactive instead of deleting it, and do not use cascading deletes on academic data.
- Department codes are unique and stored in uppercase.
- A program belongs to exactly one department. Its code and name are unique within that department, never across the college.
- An academic session is college-wide. Do not attach it directly to a department or a program, and do not assume it lasts exactly one year. Use `LocalDate` and `DATE` for its boundaries.
- A semester connects one program and one academic session. Its identity is program, session, and semester number together; the number alone is never unique.
- A course is permanent catalog data with a college-wide unique code and one owning department. It never belongs directly to a program, a semester, or a session.
- Use `BigDecimal` and `DECIMAL` for academic credits, never `double` or `float`.
- A course offering links one course to one semester, and a course appears at most once per semester. Do not copy the program, session, department, or the course's code, name, or credits onto it.
- Sections, teaching assignments, timetables, and enrollment are separate concepts. Do not fold them into the course offering.
- `StudentProfile` belongs in the `identity` module. The student ID is the account's `loginId`; do not store it, the student's name, or the department on the profile.
- Never store a current semester or current session on `StudentProfile`. `admissionSession` is the session of original admission only. Ongoing participation comes from ERP enrollment, and a profile alone must not grant LMS access.
- `SemesterEnrollment` is the official ERP record of semester participation. Do not copy the account, program, department, or session onto it, and keep its history instead of deleting it.
- Creating a semester enrollment must check that the student's program is the semester's program. Semester enrollment alone must not grant LMS course access, and course enrollment is a separate, explicit step.
- `CourseEnrollment` links a semester enrollment to a course offering. Do not copy the student, semester, program, session, or course onto it, and never auto-enroll a student in all offerings of a semester.
- Creating a course enrollment must check that the offering's semester is the semester enrollment's semester. ERP course enrollment is the source of truth for LMS course access.
- An enrollment status is not an academic result. Keep grades and results in their own models.
- Add each academic model only in its own phase, and add its permissions only together with the action they protect.

## LMS Data

- LMS data belongs in the `lms` module. An ERP `CourseOffering` and an LMS `LmsCourse` are different concepts; do not put LMS state on the course offering.
- One course offering maps to at most one `LmsCourse`.
- Do not copy the course, program, semester, academic session, or department onto `LmsCourse`.
- ERP `CourseEnrollment` remains the source of truth for official LMS student access. A `PUBLISHED` LMS course alone grants no student access.
- Archive an LMS course instead of deleting it, so its history is kept.
- The `lms` module may use academic data. Academic models must never refer to LMS models.
- ERP `CourseEnrollment` is the source of truth for official student course participation. `LmsStudentMembership` is only an LMS-side projection of it.
- One course enrollment has at most one `LmsStudentMembership`, and its course enrollment and LMS course must belong to the same course offering.
- Create LMS student memberships only through `LmsStudentMembershipProvisioningService`, from an official ERP course enrollment. Only an `ENROLLED` enrollment may get an active membership.
- Provisioning must stay idempotent. It must never modify ERP data, create a missing `LmsCourse`, or move a membership to another LMS course.
- Do not copy the student, account, course offering, course, or semester onto an LMS membership. Make a membership inactive instead of deleting it.
- An `ACTIVE` membership alone does not give access. The LMS course's status and the ERP enrollment still count.
- Student access to an LMS course is decided on the backend by `LmsStudentAccessService`, from the signed-in account's ID. Hiding things in the frontend is not security, and an ID sent by the client never says who the caller is.
- Current student access needs all of: an `ACTIVE` student profile, an `ENROLLED` semester enrollment and course enrollment, an `ACTIVE` membership, a `PUBLISHED` LMS course, and the same course offering on both sides.
- An access check only reads. It must never provision, reactivate, repair, or change data.
- The access rule is written once, in `LmsStudentAccessPolicy`. Every LMS check and list must use it instead of repeating or loosening the conditions.
- Self-service endpoints take the caller from the access token with `AuthenticatedAccount.idFrom(...)`. Never accept an account, student, profile, or enrollment ID from the client to decide whose data is returned.
- `GET /api/lms/my-courses` requires the `STUDENT` role and the `LMS_COURSE_VIEW` permission together. Passing that rule only lets the caller ask; the enrollment and membership policy still decides what is returned.
- LMS read endpoints must be side-effect free and must never provision a missing membership.
- `ADMIN` gets no implicit student LMS access. Design any admin or faculty LMS access explicitly, with its own permission.
- A migration that adds a permission also lists it in `docs/permissions.md` and looks roles and permissions up by name, never by ID.
- Do not add historical or read-only LMS access rules without an explicit feature phase.
- Provisioning is an explicit call. Do not add automatic ERP-to-LMS synchronization (listeners, schedulers, startup scans, or bulk jobs) without a dedicated phase.
- Faculty access must not be modelled from student course enrollment or added to the student membership.
- Add LMS content models only in their own feature phases.

## Git Workflow

- Codex may create phase branches, commit completed phase work, push phase branches, and merge completed and checked phases into `development`.
- Codex may delete a phase branch after its merge and the updated `development` branch are successfully pushed.
- Do not merge `development` into `main` unless the current phase explicitly allows it.
- Report Git failures instead of bypassing them, and keep commit messages simple and human-readable.
