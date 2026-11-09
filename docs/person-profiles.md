# Person Profiles

A **person profile** holds the person behind an account: the name the college records, an optional preferred name, contact details, and later an official photo. It lives in the `identity` module (`com.stacc.backend.identity.person`, table `person_profiles`), apart from the login account (`UserAccount`, which only signs in) and from the academic student and faculty profiles.

## What it stores

| Field | Set by | Rules |
| --- | --- | --- |
| Full name | The college | Required, at most 150 characters, plain text; spaces tidied. |
| Preferred name | The person | Optional, at most 80 characters. When set, it is the name shown. |
| Email | The college | Optional, unique, stored in lower case. |
| Phone | The person | Optional, 7 to 15 digits with an optional leading `+`, and spaces, dashes, or brackets. |
| Official photo | The college | A JPEG or PNG image up to 2 MB, checked by its first bytes, kept in private object storage under a server-made key (`people/{accountId}/photo/{uuid}`); only the key, type, and time are in the database. Uploaded through admin user management; a replaced photo's file is kept. |

An account has at most one profile. Until the college records one, the login ID stands in for the name everywhere.

## API

| Route | Needs | Does |
| --- | --- | --- |
| `GET /api/v1/me/profile` | `PERSON_PROFILE_VIEW_OWN` (every role holds it) | The signed-in person's own profile, or the login ID alone (`recorded: false`). |
| `PUT /api/v1/me/profile` | `PERSON_PROFILE_EDIT_OWN` (every role holds it) | Replaces the preferred name and the phone number; blank clears. 409 before the college records the profile. |
| `GET /api/v1/me/profile/photo` | as reading the profile | The person's own official photo, inline, `Cache-Control: no-store, private`, `nosniff`; 404 when there is none. |
| `GET`, `POST /api/v1/admin/users/{accountId}/photo` | `ADMIN` with `USER_VIEW`; uploading also `USER_MANAGE` | Reads or sets an account's official photo (multipart `file`). Setting it needs a recorded profile (409) and is recorded in the audit log (`USER_PHOTO_CHANGED`). |

The person always comes from the access token; no route takes an account or profile ID for "my" profile. A request that also sends a full name or an email has those parts ignored.

## Privacy

- Contact details are shown only to the person themselves. Later phases may show them to college staff who need them, each with its own permission; other students and faculty never see them.
- Names will appear where people need to recognise each other (rosters, discussions, grading) as those pages are redesigned. Never put a name, email, or phone number into an access token, a URL, or a log line.

## In the interface

The Account tray in the navigation rail, and the account part of the phone drawer, show the display name with the login ID beneath it, and the avatar shows its initials. **Account settings** in the Account tray (`/account`, v0.2.10) shows the official photo, login ID, full name, and email as recorded, lets the person change the preferred name and phone, links to Change password, and lists where the account is signed in (see `authentication.md`). Photos are loaded through the API as image data and shown from a temporary object URL; no storage address ever reaches the browser.
