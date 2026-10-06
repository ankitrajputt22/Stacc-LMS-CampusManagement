# User Accounts

A `UserAccount` is a college-issued Stacc account. It holds only what is needed to sign in. The code lives in the `auth` module under `com.stacc.backend.auth.account`, and the table is `user_accounts`.

## What an account stores

- `loginId` — the one unique login identifier for every kind of user, such as a student ID (`2408400100011`), an employee ID (`EMP1024`), or an admin-assigned ID. It is required, at most 100 characters, and unique in the database.
- `passwordHash` — only a password hash is stored. Stacc never stores a plain password. Hashes are created by the shared password encoder (see `password-security.md`).
- `status` — `ACTIVE` or `DISABLED`, stored as text. New accounts are `ACTIVE`.
- `createdAt` and `updatedAt` — set automatically.

## Rules

- There is no public signup. Accounts are created by the college.
- To stop access, set the status to `DISABLED`. Do not delete the account.
- Profile details such as name, email, department, or semester do not belong here. They will be stored in separate student and faculty profile models.
- An account can hold one or more roles, assigned by the college. Users never choose their own role. See `account-roles.md`.
- `UserAccount` is a database entity. Do not return it from an API, and never log or expose `passwordHash`.

## Not built yet

Login, logout, tokens, password reset, account creation, and permission checks all belong to later phases.
