# Account Roles

Stacc accounts receive their roles from the college. Users never choose their own role: there is no role selector at signup, at login, or anywhere in the frontend. Role information always comes from the account stored in the backend.

## Initial roles

- `STUDENT`
- `FACULTY`
- `ADMIN`

Other roles, such as HOD or hostel warden, will be added by later migrations when their features are built.

## How roles are stored

The code lives in the `auth` module under `com.stacc.backend.auth.role`.

- `roles` holds one row per role, with a unique name stored as text.
- `user_account_roles` links accounts to roles. Each account and role pair can appear only once, and both sides are protected by foreign keys.

An account can hold more than one role, for example `FACULTY` and later `HOD`, so code must not assume a single role.

Role rows are shared by many accounts. Changing or deleting an account never creates or deletes a role, and a role that is still assigned cannot be deleted.

## What roles are not

- Roles are separate from profile information such as name, department, or semester.
- Roles are not permissions. Permission rules and authorization checks will be added in a later phase.
- There is no API for creating roles or assigning them yet.
