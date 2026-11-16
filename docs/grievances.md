# Grievances

Phase 0.12.3. Grievances live in the `grievance` module. They are confidential complaints answered by a committee, kept apart from the shared helpdesk, whose tickets the support team reads.

## Categories and committees

- Admins (`ADMIN` with `GRIEVANCE_MANAGE`, at `/admin/grievances`, Grievance committees in the Manage tray) add categories. A category has an uppercase code that never changes, a name, and a response time of 1 to 90 days (`V118`). Categories stop taking new cases instead of being deleted.
- Each category has a committee of named accounts: active accounts holding `FACULTY` or `ADMIN`. Adding a member records `GRIEVANCE_COMMITTEE_MEMBER_ADDED` in the audit log, and removing one records `GRIEVANCE_COMMITTEE_MEMBER_ENDED`. Removed members keep their row with an end time.
- Admins never read cases, here or anywhere else. An admin reads a category's cases only by being named on its committee, like anyone else.

## Cases

- Students and faculty (`GRIEVANCE_SUBMIT`, at `/grievances`) raise a case in an open category, with a subject and plain-text details. The server numbers it (`GRV-000001`) and sets its respond-by time from the category's response time.
- A case opens only for its complainant and the active committee members of its category. A member never sees their own complaint as a committee case. Anyone else, admins included, gets the same 404 as for a missing case. The committee sees the complainant's login ID; nothing about the person is shown or sent anywhere else.
- **Flow.** A case moves forward only, under the case row lock:
  - The committee (`GRIEVANCE_COMMITTEE_WORK` with `FACULTY` or `ADMIN`) takes a submitted case under review, then resolves it with a written resolution.
  - The complainant then accepts the resolution, which closes the case, or appeals once within 14 days with a reason. The appeal gets its own respond-by time from the same response time.
  - The committee's appeal decision closes the case.
  - Cases are never deleted or reopened (`GrievanceAccessMySqlIT`).
- **Overdue** is worked out on read and never stored: a submitted case past its respond-by time, or an appealed case past its appeal's respond-by time.
- There are no notifications, attachments, or messages on cases yet. A conversation about anything else belongs in Support.

## Routes

| Route | Who |
| --- | --- |
| `GET /api/v1/grievances/categories`, `POST /api/v1/grievances`, `GET /api/v1/grievances/me`, `GET …/me/{id}`, `POST …/me/{id}/appeal`, `POST …/me/{id}/accept` | `STUDENT` or `FACULTY` with `GRIEVANCE_SUBMIT`; the complainant comes from the token |
| `GET /api/v1/grievances/committee`, `GET …/committee/{id}`, `POST …/committee/{id}/review`, `…/resolve`, `…/appeal-decision` | `FACULTY` or `ADMIN` with `GRIEVANCE_COMMITTEE_WORK`, and on the case's committee |
| `GET`/`POST /api/v1/admin/grievances/categories`, `PUT …/categories/{id}`, `POST …/categories/{id}/committee`, `POST /api/v1/admin/grievances/committee/{id}/end` | `ADMIN` with `GRIEVANCE_MANAGE` |

The demo data adds the `ACADEMIC` category (14 days) with `faculty1` on its committee.
