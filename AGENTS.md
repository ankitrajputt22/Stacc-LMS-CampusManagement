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
- Do not use Spring's generated login page or HTTP Basic, and never add built-in, demo, or hard-coded users. Authentication must use `UserAccount`.
- Sign-in goes through the shared `AuthenticationManager`, with `loginId` as the identifier. Roles map to `ROLE_*` authorities, and permission codes are used as authorities unchanged.
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
- Keep commit messages simple and human-readable.

## Git Workflow

- Codex may create phase branches, commit completed phase work, push phase branches, and merge completed and checked phases into `development`.
- Codex may delete a phase branch after its merge and the updated `development` branch are successfully pushed.
- Do not merge `development` into `main` unless the current phase explicitly allows it.
- Report Git failures instead of bypassing them, and keep commit messages simple and human-readable.
