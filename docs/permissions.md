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

The `permissions` table starts empty. A permission is added by a new migration only when the real feature it protects is built, and the migration must write the code in uppercase. Do not add permissions for features that do not exist yet, and do not add a catch-all permission.

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

No mappings exist yet. When a real feature introduces a permission, the same migration should assign it to the right roles. Do not guess mappings for features that do not exist.

## Not built yet

- Nothing checks permissions yet. Authorization will be enforced on the backend in a later phase.
- There is no API for managing permissions. Users never assign permissions to themselves.
