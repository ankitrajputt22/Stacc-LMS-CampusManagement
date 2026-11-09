# Security Review (v1.0.2)

What the v1.0 security sweep checked, and where each answer lives.

| Area | Result |
| --- | --- |
| Authorization matrix | `docs/authorization-matrix.md`, generated from the controllers by `scripts/authorization-matrix.py`, lists all 384 routes with their rules. Only `POST /api/v1/auth/login`, the calendar subscription feed, and the document verification check are public, each listed in `SecurityConfig` with its reason. Every other route needs a session, and all but the session and own-profile routes also need a role and a permission. |
| Wrong-record checks | Each service answers 404 for another person's or another course's record. Tests cover this per area, for example `LmsMessageServiceTest`, `TodoServiceTest`, helpdesk team access, and outings. `RouteGateApiTest` checks that the v0.8 to v0.16 areas refuse anonymous callers and roles without the area's permission before any service runs. |
| Formatting and XSS | Text is rendered as plain text, or through `FormattedText` only, with no raw HTML and links through `safeUrl`. Exports write cells as text, so nothing runs as a formula. Search results are plain text. |
| Security headers | Nginx sends `Content-Security-Policy` (self only; inline styles for maths; images from self, `data:`, and `blob:`), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, and a `Permissions-Policy` allowing only this site's camera (for the gate). The API adds `Referrer-Policy: no-referrer` beside Spring's defaults. |
| Codes and secrets | Pass codes, ID codes, and subscription links store only SHA-256 fingerprints. Document verification codes are HMACs of the document ID. Sign-in throttling stores hashes only. No secret is tracked in the repository, and `.env` is ignored. |
| Dependencies | `npm audit --omit=dev` found 0 vulnerabilities (2026-11-11). Backend dependencies are managed by the Spring Boot parent and the explicit versions in `pom.xml`. |
| Files | Every download goes through the backend with an access check (decision 8). Storage keys are server-made and never sent to a client. |

Re-run `python3 scripts/authorization-matrix.py` after adding routes, and check the new rows.
