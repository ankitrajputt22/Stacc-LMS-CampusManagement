# Access Tokens and Sessions

Signing in starts a **session**: a row in `user_sessions` that lasts at most `STACC_SESSION_MAX_DURATION` (12 hours by default) and ends sooner after `STACC_SESSION_IDLE_TIMEOUT` (2 hours) without a request. The session's **token** is a signed JWT that names it. The session decides whether the token is still accepted, so signing out, signing out everywhere, going idle, or the college disabling the account all take effect on the very next request.

## The login answer

`POST /api/v1/auth/login` answers with who signed in and when the session ends at the latest:

```json
{
  "accountId": 12,
  "loginId": "2408400100011",
  "roles": ["STUDENT"],
  "expiresAt": "2026-11-01T22:00:00Z"
}
```

The token is never in a response body. It comes in the `Set-Cookie` header as the `STACC_SESSION` cookie: **HttpOnly** (page scripts cannot read it), **SameSite=Lax** (other sites cannot send it with their requests), **Path=/api** (only API requests carry it), and **Secure** wherever `STACC_SESSION_SECURE_COOKIE=true` (required in production). A failed login gets no session and no cookie.

## What a token contains

| Claim | Meaning |
| --- | --- |
| `iss` | Always `stacc`. |
| `sub` | The account's `loginId`. |
| `accountId` | The account's ID. |
| `sid` | The session's ID. |
| `authorities` | The account's roles as `ROLE_*` names, plus the permission codes of those roles. |
| `iat`, `exp` | When the token was issued and when its session ends at the latest. |

The authorities travel in the token, so a request is checked without loading the account's roles. Roles that change take effect at the next sign-in; the college's account tools end the account's sessions when its access changes, so that is at once.

## How a request is accepted

1. The token is taken from the `Authorization: Bearer` header (API clients and tests) or, failing that, from the `STACC_SESSION` cookie (browsers). Never from a URL.
2. Its signature (HS256 with `JWT_SECRET`), issuer, expiry, account, and session claims are checked.
3. Its session must be open: not signed out, not idle, not past its end, belonging to the token's account, and the account `ACTIVE`. Each check moves "last seen" forward at most once a minute.
4. A request signed in by the cookie that changes something must also pass CSRF (see `authentication.md`).

Anything that fails answers 401 with `Invalid or expired access token.`

## Rules

- Tokens are created only by `AccessTokenService`, only for a session `SessionService` has just started.
- Never log a token, put it in a response body, a URL, or browser storage, and never put a password, a hash, or private data in its claims.
- `JWT_SECRET` must be Base64 for at least 32 random bytes; the backend refuses to start otherwise. Changing it signs everyone out.
- There are no refresh tokens: a session simply ends.
