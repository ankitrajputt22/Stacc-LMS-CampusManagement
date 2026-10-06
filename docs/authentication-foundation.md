# Authentication Foundation

Stacc accounts are now connected to Spring Security. Given a login ID and a password, the backend can decide whether they belong to a real, active account and what that account is allowed to do.

The college login endpoint uses this. See `authentication.md` for the API itself.

## How an account is loaded

```text
login ID
   |
   v
StaccUserDetailsService  ->  UserAccountRepository  ->  UserAccount
   |
   v
StaccUserPrincipal  ->  Spring Security
```

- `StaccUserDetailsService` looks up the account by `loginId`. Spring Security calls this value the "username".
- The account, its roles, and their permissions are loaded together in one query, so no extra database calls are needed later.
- Surrounding spaces in the login ID are ignored, the same as when an account is created. The database compares login IDs without case sensitivity.
- An unknown login ID is reported as "not found" without revealing anything about other accounts.

## What the principal holds

`StaccUserPrincipal` is the signed-in identity. It holds only what security needs:

| Spring Security | Stacc |
| --- | --- |
| username | `loginId` |
| password | `passwordHash` (never a plain password) |
| enabled | `true` for `ACTIVE`, `false` for `DISABLED` |
| authorities | roles and permissions |

It also carries the account ID. It holds no profile details such as name, department, or semester. Its text form never includes the password hash, and the hash is cleared from memory after a successful sign-in.

Stacc does not model locked or expired accounts, so those checks always pass.

## Authorities

- A role becomes `ROLE_` plus its name: `STUDENT` becomes `ROLE_STUDENT`, `FACULTY` becomes `ROLE_FACULTY`, `ADMIN` becomes `ROLE_ADMIN`.
- A permission keeps its code as it is, with no prefix.
- A permission shared by two of the account's roles appears only once.

## Checking a password

`SecurityConfig` provides an `AuthenticationManager`. It uses Spring Security's standard provider with `StaccUserDetailsService` and the shared `PasswordEncoder`, so passwords are never compared by hand. The login endpoint calls it.

- Correct login ID and password: authenticated.
- Wrong password or unknown login ID: rejected in the same way, so the two cannot be told apart.
- `DISABLED` account: rejected, even with the correct password. The password is checked before the account status, so a disabled account with a wrong password fails like any other wrong login.

## Not built yet

- Tokens (JWT) and sign-out. Public sign-up will never exist.
- Access rules. All routes are still temporarily open, and roles and permissions are not enforced yet.
