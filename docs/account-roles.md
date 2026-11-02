# Account Roles

Stacc accounts receive their roles from the college. Users never choose their own role: there is no role selector at signup, at login, or anywhere in the frontend. Role information always comes from the account stored in the backend.

## Roles

- `STUDENT`
- `FACULTY`
- `ADMIN`
- Staff roles (`V77`): `HOD` (head of department), `WARDEN`, `SECURITY`, `MAINTENANCE`, `LIBRARIAN`, and `PLACEMENT_OFFICER`.

Admins assign every role on the Users pages and in CSV imports. A staff role holds only `PERSON_PROFILE_VIEW_OWN` and `PERSON_PROFILE_EDIT_OWN`, so its holders can sign in and use Account settings; each gets the permissions of its work only when that feature is built. A staff role needs no student or faculty profile. The same name list is `RoleName` in the backend and `ACCOUNT_ROLES` in the frontend, always shown in this order.

## How roles are stored

The code lives in the `auth` module under `com.stacc.backend.auth.role`.

- `roles` holds one row per role, with a unique name stored as text.
- `user_account_roles` links accounts to roles. Each account and role pair can appear only once, and both sides are protected by foreign keys.

An account can hold more than one role, for example `FACULTY` and later `HOD`, so code must not assume a single role.

Role rows are shared by many accounts. Changing or deleting an account never creates or deletes a role, and a role that is still assigned cannot be deleted.

## What roles are not

- Roles are separate from profile information such as name, department, or semester.
- Roles are not permissions. A role holds permissions (see `permissions.md`), and operations check them.
- Operations can now require a role. See `authorization.md`.
- Roles are created only by migrations. Admins assign them through `/api/v1/admin/users` (see `user-management.md`).
