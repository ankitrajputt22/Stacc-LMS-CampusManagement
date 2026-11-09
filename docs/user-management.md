# User Management

Admins find, create, and manage accounts at `/admin/users` (v0.2.6). The code is `com.stacc.backend.identity.admin`. There is no public signup: the college creates every account.

## Routes and permissions

Every route is under `/api/v1/admin/users`. Reading needs `ADMIN` with `USER_VIEW`; every change also needs `USER_MANAGE` (both `ADMIN` only, from `V74`). The account changed is always named by the route, and the admin always comes from the access token. No answer ever carries a password, a hash, or a phone number.

| Route | What it does |
| --- | --- |
| `GET /admin/users?role=&status=&q=&page=&size=` | One page (size 1 to 100, default 20) of accounts by login ID, filtered by role and status and searched (at most 100 characters, lower-cased, `LIKE` escaped with `!`) in the login ID and the person's full name, preferred name, or email. |
| `GET /admin/users/{accountId}` | The account: login ID, official name and email, preferred name, roles, status, whether a password change is required and when the temporary password expires, and its student and faculty profiles. |
| `POST /admin/users` | Creates the account, its person profile, its roles, and the student profile (program, admission session, optional roll number) or faculty profile (primary department) its roles need, all in one transaction. 201. |
| `PUT /admin/users/{accountId}/details` | Corrects the official full name and email, recording a person profile when there is none. |
| `PUT /admin/users/{accountId}/status` | `ACTIVE` or `DISABLED`. Disabling ends every session at once. |
| `PUT /admin/users/{accountId}/roles` | Sets the roles to exactly the ones sent. A real change ends the account's sessions, so the new roles apply at the next sign-in. |
| `POST /admin/users/{accountId}/temporary-password` | Issues a new temporary password and ends every session. |
| `POST /admin/users/{accountId}/student-profile` | Creates the student profile of an account holding `STUDENT` without one. |
| `POST /admin/users/{accountId}/faculty-profile` | Creates the faculty profile of an account holding `FACULTY` without one. |
| `GET`, `POST /admin/users/{accountId}/photo` | Reads or sets the official photo (see `person-profiles.md`). |

## Bulk import (v0.2.7)

`POST /api/v1/admin/users/import` (`ADMIN` with `USER_VIEW` and `USER_MANAGE`) takes a multipart `file` and `temporaryPassword`, and `dryRun` (default `true`).

- The file is UTF-8 CSV (RFC 4180 quoting, a byte-order mark ignored), at most 1 MB and 500 accounts, whose first line is exactly `login_id,full_name,email,roles,department,program,admission_session,roll_number`. Roles are separated by `;`. Codes are matched in uppercase. Students need the department, program (of that department, active), and admission session; faculty need the department (active, their primary department).
- Every row is checked as one account would be, plus repeats inside the file: login IDs, emails, and roll numbers already taken or repeated, unknown roles or codes, inactive programs or departments, missing fields, and the temporary password's policy against each login ID. The answer lists every row with its problems by line (the header is line 1).
- With `dryRun` true, or while any row has a problem, nothing is saved (`created: 0`). Otherwise every account is created in one transaction through the normal account creation (person, roles, profiles, audit record each), so a failure creates none.
- All imported accounts get the one temporary password typed by the admin, hashed once with the shared `PasswordEncoder`; each person must replace it at first sign-in. No password is ever read from the file. The import itself is recorded as `USERS_IMPORTED`.
- A file that cannot be used at all (no file, too large, not UTF-8, a wrong header, no rows, too many rows, an unusable password) answers 400.

**Import from CSV** on the Users page opens `/admin/users/import`: the column guide with an example, the file, the shared temporary password, **Check file**, a table of rows marked Ready or with their problems, and **Import N accounts** only after a clean check, confirmed first. Changing the file or password asks for a new check.

## Rules

- The login ID is the student or employee ID: unique, stripped of spaces, and never changed afterwards (409 when taken). Emails are optional and unique among people (409).
- A new account always starts with a temporary password chosen by the admin. It follows `PasswordPolicy` (10 to 128 characters, not common, without the login ID; 400 otherwise), is hashed with the shared `PasswordEncoder`, expires after three days, and only lets the person choose a new password (`password_change_required`). It is never returned, logged, or shown again; no one can see an existing password.
- Roles are `STUDENT`, `FACULTY`, `ADMIN`, and the staff roles `HOD`, `WARDEN`, `SECURITY`, `MAINTENANCE`, `LIBRARIAN`, and `PLACEMENT_OFFICER`; an account may hold several. Staff roles need no profile. A student account needs its program (active) and admission session, a faculty account its primary department (active); 400 when missing, 409 when inactive. Roll numbers are stored in uppercase and unique (409).
- Accounts are disabled, never deleted, and every record that refers to them stays. Removing a role keeps the profile: it is academic history and grants nothing by itself.
- An admin cannot disable their own account or remove their own `ADMIN` role (409), so the college cannot lock itself out.
- Status and role changes lock the account row. The same status or the same set of roles changes nothing and ends no session.
- Every change is recorded in the audit log (`docs/audit-log.md`) in the same transaction, by the admin from the token, without names, contact details, or passwords.
- Academic corrections (roll number, profile status, primary department) stay under Academic Admin → People; enrollment stays under Enrollments.

## Frontend

- **Users** in the Manage tray opens `/admin/users`: filters by role and status, a search sent only on **Find**, a paged table (name linked to the account, login ID, email, roles, status, "Must change password"), and **Add user**.
- `/admin/users/new` has the account fields, a **Show password** switch, role checkboxes, and the student or faculty profile fields when those roles are chosen. Obvious mistakes are caught before sending; the backend checks everything again. After creating, the account page opens and says to give the person the temporary password.
- `/admin/users/:accountId` has **Status** (Disable or Activate account, confirmed first; not offered for one's own active account), **Roles** (checkboxes, Save roles confirmed first, one's own Admin role locked), **Official details**, **Official photo** (preview and upload), **Temporary password**, and **Academic profiles** with Create student or faculty profile when a role needs one. Refusals show the backend's message; a 403 never signs the user out.

## Not built yet

Self-service password recovery by email, and account deletion (never).
