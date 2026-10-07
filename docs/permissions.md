# Permissions

A permission is one action that can later be granted to roles. Roles describe what kind of responsibility an account has; permissions describe what a role is allowed to do.

The code lives in the `auth` module under `com.stacc.backend.auth.permission`, and the table is `permissions`.

## Permission codes

Each permission has a unique text `code` of at most 100 characters, written in `UPPERCASE_WITH_UNDERSCORES`.

- Codes are stored as text, not as one large Java enum, so each module can add its own permissions without changing shared code.
- `Permission` removes surrounding spaces and rejects any other code that is not already in this style. It never changes the case for you.
- A code may not start with `ROLE_`. Spring Security reads such names as roles, so a permission must never look like one.
- The database compares codes without case sensitivity, so two codes that differ only by case cannot both exist.

## Adding permissions

A permission is added by a new migration only when the real feature it protects is built, and the migration must write the code in uppercase. Do not add permissions for features that do not exist yet, and do not add a catch-all permission.

## Current permissions

| Code | Held by | What it allows |
| --- | --- | --- |
| `LMS_COURSE_VIEW` | `STUDENT` | Using the student LMS course-view feature, starting with `GET /api/lms/my-courses`. |

`LMS_COURSE_VIEW` opens the feature, not the courses. Which LMS courses a student actually gets is still decided by official enrollment and LMS membership (see `lms-foundation.md`). `FACULTY` and `ADMIN` do not hold it. The authority name is exactly `LMS_COURSE_VIEW`, with no `ROLE_` or `SCOPE_` in front.

Add a row to this table whenever a migration adds a real permission. It is a record of what exists, not a plan.

## Roles and permissions

Permissions can now be linked to roles:

```text
UserAccount
    |
    v
  Role
    |
    v
Permission
```

The link is many-to-many and is stored in the `role_permissions` table. A role can hold many permissions, and the same permission can belong to several roles. Each role and permission pair can appear only once.

Permission rows are shared. Changing a role never creates or deletes a permission, and a permission that is still assigned to a role cannot be deleted.

When a real feature introduces a permission, the same migration assigns it to the right roles, as `V17` does for `LMS_COURSE_VIEW` and `STUDENT`. Roles and permissions are looked up by name in the migration, never by ID. Do not guess mappings for features that do not exist.

A permission reaches an account through its access token, which is issued at login. After a mapping changes, a token issued earlier keeps its old permissions until it expires, which is at most 15 minutes by default. Signing in again gives a token with the new ones.

## Checking permissions

A permission code can be used directly in a method rule: `@PreAuthorize("hasAuthority('PERMISSION_CODE')")`. The code is the authority name as stored, with no prefix. See `authorization.md`.

The first real rule is on `GET /api/lms/my-courses`, which requires the `STUDENT` role and `LMS_COURSE_VIEW` together.

## Not built yet

- There is no API for managing permissions. Users never assign permissions to themselves.
