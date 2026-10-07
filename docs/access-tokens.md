# Access Tokens

A successful college login returns a JWT access token. It is the proof of sign-in that a client will send with later requests.

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

- `tokenType` is always `Bearer`.
- `expiresIn` is the token's lifetime in seconds. The default is 15 minutes (900 seconds).
- A failed login never receives a token.

## What a token contains

| Claim | Meaning |
| --- | --- |
| `iss` | Always `stacc`. |
| `sub` | The account's `loginId`. |
| `accountId` | The account's ID. |
| `authorities` | The account's roles as `ROLE_*` names, plus the permission codes of those roles. |
| `iat`, `exp` | When the token was issued and when it expires. |

The authorities are included so that later requests can be checked without asking the database every time. The trade-off is that a change to an account's roles or permissions only takes effect in tokens issued after the change. Keeping tokens short-lived limits how long an old token can be used.

A token is signed, not encrypted. Anyone holding it can read what is inside, so it never contains a password, a password hash, or profile details such as email or phone number.

## Signing and configuration

Tokens are signed with HMAC SHA-256 through Spring Security's JWT support. `AccessTokenService` in `com.stacc.backend.auth.token` is the only place that creates them.

| Environment variable | Meaning |
| --- | --- |
| `JWT_SECRET` | The signing secret, as Base64 text for at least 32 random bytes. Required. |
| `JWT_ACCESS_TOKEN_MINUTES` | Token lifetime in minutes, from 1 to 60. Defaults to 15. |

Create a secret with:

```bash
openssl rand -base64 48
```

There is no default secret. The backend refuses to start if `JWT_SECRET` is missing, is not Base64, or is too short. Never commit a real secret, never log it, and never give it to the frontend (there must be no `VITE_JWT_SECRET`). Tests use their own made-up secret that exists only in test code.

Access tokens must not be logged either.

## Using a token

A client sends the token in the standard header:

```text
Authorization: Bearer <access-token>
```

The header is the only place a token is accepted. Tokens in a URL or query parameter are ignored, and tokens must never be put in URLs.

Spring Security checks every token it receives:

- the signature, using the same secret and algorithm (HS256 only) that signed it;
- that it has not expired;
- that the issuer is `stacc`;
- that it names an account (`sub` and `accountId`).

A valid token signs the request in for that one request. The caller's name is the `loginId` from `sub`, the `accountId` claim stays available, and the authorities come straight from the `authorities` claim. Stacc adds no prefix to them: `ROLE_STUDENT` stays `ROLE_STUDENT` and a permission code stays as it is, with no `SCOPE_` in front. Spring Security also adds its own marker, `FACTOR_BEARER`, which only says the request was signed in with a token. It is not a Stacc role or permission.

A token that cannot be accepted (malformed, wrong signature, changed, expired, or from another issuer) is answered with HTTP 401, a `WWW-Authenticate: Bearer` header, and the message `Invalid or expired access token.` in the common error format. The reason is never revealed, and a bad token is never treated as if no token had been sent.

An endpoint that acts for the caller reads the account from the token with `AuthenticatedAccount.idFrom(...)`. It never takes the account from an ID in the request.

The database is not asked on each request. Everything comes from the token, so if an account is disabled or its roles change, a token issued before that keeps working until it expires. The short lifetime limits this.

## Not built yet

- There are no refresh tokens, no logout, and no way to cancel a token before it expires.
