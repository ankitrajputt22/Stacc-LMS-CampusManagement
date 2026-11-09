# Password Security

Stacc never stores a plain password. A `UserAccount` holds only `passwordHash`.

## The shared password encoder

All password hashing goes through one Spring Security `PasswordEncoder` bean, defined in `SecurityConfig`. Inject that bean wherever a password is handled.

- To store a password, save the result of `passwordEncoder.encode(plainPassword)` as the `passwordHash`.
- To check a password, call `passwordEncoder.matches(plainPassword, passwordHash)`. Never compare the strings yourself.

## How passwords are hashed

- New passwords are hashed with BCrypt, a slow, adaptive algorithm made for passwords.
- Every hash gets its own random salt, so the same password produces a different hash each time.
- Each hash starts with the name of its algorithm, for example `{bcrypt}`. This lets Stacc move to a stronger algorithm later without breaking existing passwords.
- A hash is about 68 characters long, well within the 255 characters of the `password_hash` column.

## Rules

- Never log a plain password or a password hash, and never return either from an API.
- Do not write custom hashing code, and do not use fast hashes such as MD5 or SHA-256 for passwords.
- Use obviously fake passwords in tests, and keep them in test code only.

## Password policy

`auth.password.PasswordPolicy` decides whether a new password is acceptable. Length matters most, so there are no rules about capitals, digits, or symbols.

- At least 10 and at most 128 characters, counted as characters, not bytes. Spaces are allowed and kept: a short phrase is a good password.
- It must not contain the login ID, whatever the case.
- It must not be one of a short built-in list of the most common passwords (compared without case or spaces), or use only one or two different characters.
- It must differ from the current password.

The browser checks the length first for a quicker answer, but the backend's check is the one that counts.

## Changing a password

`POST /api/v1/me/password` with `currentPassword` and `newPassword` changes the signed-in person's own password (`PasswordService.changeOwnPassword`).

- It needs a signed-in session and nothing else, so it also works while a change is required (below).
- A wrong current password or an unacceptable new one answers 400 with a plain message. The message never repeats a password.
- On success it answers 204, stores only the new hash, records `password_changed_at`, and ends every other session of the account (end reason `PASSWORD_CHANGED`). The session that made the change stays signed in.

The request type hides both passwords from its text form, so they cannot reach a log by accident.

## Temporary passwords and forced changes

`PasswordService.issueTemporaryPassword` gives an account a temporary password for the college's account tools (roadmap 0.2.6, which adds the admin route). It:

- stores the temporary password's hash and sets `password_change_required`,
- sets `temporary_password_expires_at`, three days later unless told otherwise,
- ends every session of the account (end reason `ENDED_BY_COLLEGE`).

While a change is required:

- Signing in works with the temporary password until it expires. After that the login answers the same generic 401 as a wrong password; Spring Security treats it as expired credentials, checked only after the password matched.
- The login and `GET /api/v1/auth/session` answer `passwordChangeRequired: true`.
- The session check gives the request no roles and no permissions, so every feature answers 403. Only routes that need nothing more than a signed-in session work: the session itself, changing the password, and signing out.
- The frontend shows only the Change password page until the change is saved.

Changing the password clears the requirement and the expiry at once, and the next request has the account's roles again.

## Not built yet

Self-service reset by email or SMS, password history, and multi-factor sign-in are not planned for v0.2.
