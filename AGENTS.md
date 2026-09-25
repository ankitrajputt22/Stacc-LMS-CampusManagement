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
- Keep frontend and backend API contracts consistent.
- Use the shared API error format and Jakarta Validation for request DTOs. Do not expose stack traces or internal database errors, and do not create module-specific error formats.
- Use Canvas LMS as a UX reference without copying its branding, assets, or exact design.
- Maintain a coherent Stacc identity across the UI. Google Stitch may provide approved design references.
- Keep commit messages simple and human-readable.

## Git Workflow

- Codex may create phase branches, commit completed phase work, push phase branches, and merge completed and checked phases into `development`.
- Codex may delete a phase branch after its merge and the updated `development` branch are successfully pushed.
- Do not merge `development` into `main` unless the current phase explicitly allows it.
- Report Git failures instead of bypassing them, and keep commit messages simple and human-readable.
