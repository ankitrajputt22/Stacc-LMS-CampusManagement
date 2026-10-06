# Permissions

A permission is one action that can later be granted to roles. Roles describe what kind of responsibility an account has; permissions describe what a role is allowed to do.

The code lives in the `auth` module under `com.stacc.backend.auth.permission`, and the table is `permissions`.

## Permission codes

Each permission has a unique text `code` of at most 100 characters, written in `UPPERCASE_WITH_UNDERSCORES`.

- Codes are stored as text, not as one large Java enum, so each module can add its own permissions without changing shared code.
- `Permission` removes surrounding spaces and rejects any other code that is not already in this style. It never changes the case for you.
- The database compares codes without case sensitivity, so two codes that differ only by case cannot both exist.

## Adding permissions

The `permissions` table starts empty. A permission is added by a new migration only when the real feature it protects is built, and the migration must write the code in uppercase. Do not add permissions for features that do not exist yet, and do not add a catch-all permission.

## Not built yet

- Permissions are not connected to roles yet.
- Nothing checks permissions yet. Authorization will be enforced on the backend in a later phase.
- There is no API for managing permissions. Users never assign permissions to themselves.
