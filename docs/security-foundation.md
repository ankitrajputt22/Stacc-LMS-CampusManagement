# Security Foundation

Spring Security is part of the Stacc backend. All of its settings live in one place: `SecurityConfig` in `com.stacc.backend.auth.security`. Every request passes through its filter chain before it reaches a controller.

The college login returns an access token, and a request that sends it as `Authorization: Bearer <token>` is recognised as that account (see `authentication.md` and `access-tokens.md`). Everything under `/api/` now requires one (see `api-security.md`).

## Current behaviour

- **API routes are protected by default.** Everything under `/api/` needs a valid access token. Only the login and the API documentation are public.
- **A bad token is refused.** A request that sends an invalid or expired access token gets HTTP 401, even on a public route.
- **No Spring sign-in page.** Spring's generated login page and its default `/logout` handling are switched off. Stacc will have its own sign-in screen.
- **No HTTP Basic.** The browser's username and password prompt is not used.
- **No generated user.** Spring Boot normally creates a `user` account with a random password printed in the log. That is switched off in `StaccApplication`. Stacc has no built-in or demo users, and accounts come only from `UserAccount`.
- **Stateless.** The backend does not create server-side login sessions. It is being prepared for token-based authentication.
- **Default security headers stay on**, such as `X-Content-Type-Options` and `X-Frame-Options`.

## CSRF

CSRF protection is switched off. It guards against attacks on cookie-based sessions, and Stacc is a stateless REST API that will use tokens rather than session cookies. If authentication ever moves to cookies, this decision must be reviewed.

## Not built yet

- Sign-out and refresh tokens.

Swagger and the OpenAPI document still load without a token. Unknown URLs still return 404 and unsupported methods 405, except that inside `/api/` a caller must be signed in first.
