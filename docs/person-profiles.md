# Person Profiles

A **person profile** holds the person behind an account: the name the college records, an optional preferred name, contact details, and later an official photo. It lives in the `identity` module (`com.stacc.backend.identity.person`, table `person_profiles`), apart from the login account (`UserAccount`, which only signs in) and from the academic student and faculty profiles.

## What it stores

| Field | Set by | Rules |
| --- | --- | --- |
| Full name | The college | Required, at most 150 characters, plain text; spaces tidied. |
| Preferred name | The person | Optional, at most 80 characters. When set, it is the name shown. |
| Email | The college | Optional, unique, stored in lower case. |
| Phone | The person | Optional, 7 to 15 digits with an optional leading `+`, and spaces, dashes, or brackets. |
| Official photo | The college | Kept in private object storage; only its server-made key and type are in the database. Added through admin user management (roadmap phase 0.2.6). |

An account has at most one profile. Until the college records one, the login ID stands in for the name everywhere.

## API

| Route | Needs | Does |
| --- | --- | --- |
| `GET /api/v1/me/profile` | `STUDENT`, `FACULTY`, or `ADMIN` with `PERSON_PROFILE_VIEW_OWN` | The signed-in person's own profile, or the login ID alone (`recorded: false`). |
| `PUT /api/v1/me/profile` | `STUDENT`, `FACULTY`, or `ADMIN` with `PERSON_PROFILE_EDIT_OWN` | Replaces the preferred name and the phone number; blank clears. 409 before the college records the profile. |

The person always comes from the access token; no route takes an account or profile ID for "my" profile. A request that also sends a full name or an email has those parts ignored.

## Privacy

- Contact details are shown only to the person themselves. Later phases may show them to college staff who need them, each with its own permission; other students and faculty never see them.
- Names will appear where people need to recognise each other (rosters, discussions, grading) as those pages are redesigned. Never put a name, email, or phone number into an access token, a URL, or a log line.

## In the interface

The Account tray in the navigation rail, and the account part of the phone drawer, show the display name with the login ID beneath it, and the avatar shows its initials. Account settings, where the person changes the preferred name and phone, arrive in roadmap phase 0.2.10.
