# Authorization

Authentication answers "who is this account?". Authorization answers "is this account allowed to do this?".

| Situation | Response |
| --- | --- |
| No token, or an invalid or expired token | 401 Unauthorized |
| Valid token, but the account lacks the required role or permission | 403 Forbidden |
| Valid token with what the operation requires | The request continues. |

A signed-in account that is not allowed always gets 403, never 401.

Roles and permissions answer different questions. A role says what responsibility an account has, such as `FACULTY`. A permission says what action it may perform. Both are checked on the backend, which is the only source of truth. Hiding a button in the frontend is never a security check.

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

## Permissions

A permission's stored code is its authority name, exactly as written. Nothing is added in front: no `ROLE_`, no `SCOPE_`, and no other prefix. An operation states the permission it needs like this, where `PERMISSION_CODE` stands for a real code:

```java
@PreAuthorize("hasAuthority('PERMISSION_CODE')")
```

```java
@PreAuthorize("hasAnyAuthority('FIRST_CODE', 'SECOND_CODE')")
```

- Use `hasAuthority` and `hasAnyAuthority` for permissions, and `hasRole` and `hasAnyRole` for roles. Do not mix them up: a role never satisfies a permission check, and a permission never satisfies a role check.
- An account gets its permissions through its roles. It may get the same permission from two roles, which changes nothing.
- A rule may require both, for example `hasRole('FACULTY') and hasAuthority('PERMISSION_CODE')`. Keep rules as simple as the feature needs.
- `ADMIN` is not an automatic pass here either. An admin has a permission only if a role actually grants it.
- Permissions travel in the access token, so a check never asks the database. A change to a role's permissions takes effect in tokens issued after the change.

No real permission codes exist yet, so no real endpoint has a permission rule. A permission is introduced by a migration together with the real feature it protects (see `permissions.md`).

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

The response is the same for a missing role and a missing permission. It never says which role, permission, or rule was required.

A permission code can never start with `ROLE_`, so a permission cannot be mistaken for a role.
