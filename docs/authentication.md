# Authentication

Every official Stacc account signs in through one central college login. There is no separate login for students, faculty, or admins, and there is no public signup: the college creates the accounts.

## Login

```text
POST /api/auth/login
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
| Missing, blank, or over-long field | 400, with `fieldErrors` |

The three 401 cases look identical on purpose, so nobody can use the login to find out which login IDs exist. A failed login never receives a token. All errors use the common format described in `api-error-handling.md`.

## How the check works

The login ID and password are checked by Spring Security through the shared `AuthenticationManager`, which loads the `UserAccount` and compares the password with the shared password encoder. Nothing compares passwords by hand. See `authentication-foundation.md` for the details.

Passwords and login requests are never logged.

## After login

```text
login  ->  receive the access token  ->  send "Authorization: Bearer <token>"  ->  reach protected /api routes
```

A request that carries a valid access token is recognised as that account, and every `/api/` route other than the login requires one. See `access-tokens.md` and `api-security.md`. Login does not create a server session or set a cookie.

Not built yet: refresh tokens, logout, and password change or reset.
