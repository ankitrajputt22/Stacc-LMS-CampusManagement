# Authentication

Every official Stacc account signs in through one central college login. There is no separate login for students, faculty, or admins, and there is no public signup: the college creates the accounts.

## Login

```text
POST /api/v1/auth/login
```

Request:

```json
{
  "loginId": "2408400100011",
  "password": "..."
}
```

- `loginId` is the ID the college issued, such as a student ID or an employee ID. It is required and at most 100 characters. Spaces around it are ignored.
- `password` is required and at most 128 characters. It is checked exactly as sent and is never trimmed.
- The request has no role field. Users never choose their own role. Anything extra in the request is ignored.

Successful response, HTTP 200:

```json
{
  "accountId": 12,
  "loginId": "2408400100011",
  "roles": ["STUDENT"],
  "accessToken": "<signed JWT>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

`roles` lists every role the college has assigned to the account, in alphabetical order. `accessToken` is a short-lived signed token; `expiresIn` is its lifetime in seconds. See `access-tokens.md`. The response never contains a password or a password hash.

## Failed logins

| Situation | Response |
| --- | --- |
| Wrong password | 401, `Invalid login ID or password.` |
| Unknown login ID | 401, the same message |
| Disabled account | 401, the same message |
| Temporary password that has expired | 401, the same message |
| Missing, blank, or over-long field | 400, with `fieldErrors` |
| Too many failures for this login ID or from this address | 429, `Too many sign-in attempts. Wait a few minutes and try again.` |

The 401 cases look identical on purpose, so nobody can use the login to find out which login IDs exist. A failed login never receives a token. All errors use the common format described in `api-error-handling.md`.

## Sign-in protection

Repeated failed sign-ins slow down further tries (v0.2.4, `auth.throttle.SignInThrottleService`); accounts are never locked, so nobody can lock someone else out.

- Failures are counted per login ID (lower-cased, without surrounding spaces) and per network address. After `STACC_SIGN_IN_ACCOUNT_THRESHOLD` failures (default 5) for one login ID, or `STACC_SIGN_IN_ADDRESS_THRESHOLD` (default 30) from one address, within `STACC_SIGN_IN_WINDOW` (15 minutes), each further failure blocks that key for `STACC_SIGN_IN_BASE_DELAY` (30 seconds), doubling each time up to `STACC_SIGN_IN_MAX_DELAY` (15 minutes). Failures older than the window are forgotten once nothing is blocked.
- While a key is blocked, the login answers 429 before the password is even checked. Unknown login IDs are counted and blocked exactly like real ones, so the answer never reveals whether an account exists.
- A successful sign-in forgets the login ID's failures; the address keeps its count until its window passes.
- The table `sign_in_throttles` stores only a SHA-256 hash of each key, the failure count, and the times; never a login ID or an address. Each failure is counted under the key's row lock (`insert ignore`, then `SELECT ... FOR UPDATE`), so simultaneous guesses are all counted.
- The address is the connection's, or Stacc's own proxy's `X-Real-IP` when `STACC_SIGN_IN_TRUST_PROXY=true`. Only set that when the backend can be reached through the proxy alone: the Docker stack does, because the backend's port is published on 127.0.0.1 only.
- Counting never changes a sign-in's answer: if it fails, a warning is logged without the login ID or address, and the sign-in result stands.
- The sign-in page shows "Too many sign-in attempts. Wait a few minutes and try again." for a 429.
- Sessions already end after `STACC_SESSION_IDLE_TIMEOUT` without a request and after `STACC_SESSION_MAX_DURATION` in any case (see Sessions below).

## How the check works

The login ID and password are checked by Spring Security through the shared `AuthenticationManager`, which loads the `UserAccount` and compares the password with the shared password encoder. Nothing compares passwords by hand. See `authentication-foundation.md` for the details.

Passwords and login requests are never logged.

## After login

```text
login  ->  receive the access token  ->  send "Authorization: Bearer <token>"  ->  reach protected /api routes
```

A request that carries a valid access token is recognised as that account, and every `/api/` route other than the login requires one. See `access-tokens.md` and `api-security.md`. Login does not create a server session or set a cookie.

Refresh tokens are not planned. Signing out is described below; changing a password and temporary passwords are in `password-security.md`.

## Sessions, signing out, and CSRF

| Route | Needs | Does |
| --- | --- | --- |
| `POST /api/v1/auth/login` | Nothing (public) | Checks the login ID and password, starts a session, and sets the `STACC_SESSION` cookie. Any old cookie is ignored here, so it never blocks signing in again. |
| `GET /api/v1/auth/session` | A signed-in account | Who is signed in and when the session ends at the latest; 401 when nobody is. The frontend asks once per page load. |
| `POST /api/v1/auth/logout` | A signed-in account | Ends this session and clears the cookie. |
| `POST /api/v1/auth/logout-everywhere` | A signed-in account | Ends every open session of the account, on every device, and clears this browser's cookie. |

Sessions live in `user_sessions` (`auth.session`) and are kept after they end, with the reason (`SIGNED_OUT`, `SIGNED_OUT_EVERYWHERE`, `IDLE`, `ENDED_BY_COLLEGE`) and a short device description such as "Chrome on Android". No IP address or full browser text is stored.

**CSRF.** A browser sends the session cookie on its own, so a change signed in by that cookie must also prove it came from Stacc's own page: Stacc sets an `XSRF-TOKEN` cookie that only its own pages can read, and the page copies it into the `X-XSRF-TOKEN` header (Axios does this). Requests with an `Authorization` header, requests without the session cookie, sign-in itself, and `GET`/`HEAD`/`OPTIONS` have nothing to forge and are not checked. A failed check answers 403. The cookie is read by `SessionCookieAuthenticationFilter` *after* the CSRF check, and Spring's per-sign-in XSRF rotation is switched off, because without servlet sessions it would delete the token on every request.
