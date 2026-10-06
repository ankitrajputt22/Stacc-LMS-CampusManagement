# API Security

Stacc's API is protected by default. Everything under `/api/` needs a signed-in account unless it has been deliberately made public. A new endpoint under `/api/` is therefore protected the moment it is added, without anyone having to remember to secure it.

The rules live in one place: `SecurityConfig` in `com.stacc.backend.auth.security`.

## Public routes

| Route | Why it is public |
| --- | --- |
| `POST /api/auth/login` | Nobody has a token before signing in. Only `POST` is public. |
| `/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**` | The API documentation, public during development. It only describes the API. Calling a protected endpoint from Swagger still needs a token. |

Keep this list small. Make a route public only for a clear reason.

## Protected routes

Every other route under `/api/` requires a valid access token, sent as `Authorization: Bearer <access-token>` (see `access-tokens.md`).

```text
login  ->  receive the access token  ->  send "Authorization: Bearer <token>"  ->  reach protected /api routes
```

| Request to a protected route | Response |
| --- | --- |
| No token | 401, `Authentication is required.` |
| Invalid, changed, or expired token | 401, `Invalid or expired access token.` |
| Valid token | The request continues as that account. |
| Valid token, but the operation needs a role the account lacks | 403, `You do not have permission to access this resource.` |

Both 401 responses use the common error format and carry a `WWW-Authenticate: Bearer` header.

Any signed-in account may use a protected route unless the operation itself requires a role. Role rules are described in `authorization.md`. No real endpoint has one yet, and permission rules are not active yet.

## Unknown routes

The token is checked before the route is looked up, so:

- an unknown path under `/api/` returns 401 without a token and 404 with a valid one;
- `GET /api/auth/login` returns 401 without a token and 405 with a valid one, because only `POST` is public;
- an unknown path outside `/api/` returns the normal 404.

This is intended. It also means an anonymous caller cannot probe which API paths exist.

## Still to come

- Permission-based rules.
- CORS for the frontend, with known origins only.
- Refresh tokens and sign-out.
