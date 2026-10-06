# Authorization

Authentication answers "who is this account?". Authorization answers "is this account allowed to do this?".

| Situation | Response |
| --- | --- |
| No token, or an invalid or expired token | 401 Unauthorized |
| Valid token, but the account lacks the required role | 403 Forbidden |
| Valid token with a required role | The request continues. |

A signed-in account with the wrong role always gets 403, never 401.

## Roles

The current roles are `STUDENT`, `FACULTY`, and `ADMIN`. In Spring Security each one is the authority `ROLE_` plus its name, such as `ROLE_STUDENT`. They travel in the access token, so a role check never asks the database.

Method security is switched on, so an operation can state the role it needs:

```java
@PreAuthorize("hasRole('ADMIN')")
```

```java
@PreAuthorize("hasAnyRole('STUDENT', 'FACULTY')")
```

- Use `hasRole` and `hasAnyRole` for roles. Write the plain name: `hasRole('ADMIN')`, not `hasRole('ROLE_ADMIN')`, because Spring adds the prefix itself.
- An account can hold several roles. A rule passes if the account has any role the rule allows.
- There is no role hierarchy. `ADMIN` does not include `FACULTY`, and `FACULTY` does not include `STUDENT`.
- `ADMIN` is not an automatic pass. `hasRole('STUDENT')` means a student. If an admin should also be allowed, the rule must say so.

No real endpoint has a role rule yet. Add one only when a real feature needs it, and enforce it on the backend even if the frontend hides the action.

## Refused requests

A refused request gets HTTP 403 in the common error format:

```json
{
  "timestamp": "2026-10-07T10:30:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to access this resource.",
  "path": "/api/example",
  "fieldErrors": []
}
```

The response never says which role or rule was required.

## Permissions

Permissions are separate from roles. They will be checked with `hasAuthority` and their code, in a later phase. A permission code can never start with `ROLE_`, so a permission cannot be mistaken for a role.
