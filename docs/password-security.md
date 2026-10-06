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

## Not built yet

The sign-in endpoint, account creation, and password change or reset are not implemented yet. The authentication manager already uses the encoder to check passwords (see `authentication-foundation.md`), but nothing calls it over HTTP so far.
