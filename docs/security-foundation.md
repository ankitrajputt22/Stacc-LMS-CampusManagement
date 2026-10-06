# Security Foundation

Spring Security is part of the Stacc backend. All of its settings live in one place: `SecurityConfig` in `com.stacc.backend.auth.security`. Every request passes through its filter chain before it reaches a controller.

The college login endpoint exists and returns an access token (see `authentication.md` and `access-tokens.md`), but the backend does not read tokens on incoming requests yet, so no route can require a signed-in user.

## Current behaviour

- **All routes are open, temporarily.** A login cannot be carried from one request to the next yet, so locking routes would make the backend unusable. This is not the final policy. Real access rules will replace it once incoming tokens are checked.
- **No Spring sign-in page.** Spring's generated login page and its default `/logout` handling are switched off. Stacc will have its own sign-in screen.
- **No HTTP Basic.** The browser's username and password prompt is not used.
- **No generated user.** Spring Boot normally creates a `user` account with a random password printed in the log. That is switched off in `StaccApplication`. Stacc has no built-in or demo users, and accounts come only from `UserAccount`.
- **Stateless.** The backend does not create server-side login sessions. It is being prepared for token-based authentication.
- **Default security headers stay on**, such as `X-Content-Type-Options` and `X-Frame-Options`.

## CSRF

CSRF protection is switched off. It guards against attacks on cookie-based sessions, and Stacc is a stateless REST API that will use tokens rather than session cookies. If authentication ever moves to cookies, this decision must be reviewed.

## Not built yet

- Checking access tokens on incoming requests, and sign-out.
- Enforcing roles and permissions. They exist in the database but nothing checks them yet.
- CORS for the frontend. It will be set up for known origins when the first real frontend and backend feature is connected.

Existing behaviour is unchanged: Swagger and the OpenAPI document still load, unknown URLs return 404, and unsupported methods return 405.
