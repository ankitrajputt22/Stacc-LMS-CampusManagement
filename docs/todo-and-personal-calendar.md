# To-Do and Personal Calendar

## To-Do (`todo` module)

`GET /api/v1/me/todo` (`STUDENT`, `FACULTY`, or `ADMIN` with `TODO_VIEW`) builds the list live on every request, one part per role and permission held:

| Part | Needs | Source |
| --- | --- | --- |
| Assignments to submit | `STUDENT` and `LMS_COURSE_VIEW` | Published assignments of the courses `LmsStudentCourseQueryService` lists, due within the last 7 days or later, with no submission yet. |
| Work to grade | `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_ASSIGNMENT_GRADE` | Latest attempts without a grade, per published assignment of the open courses taught. |
| Attendance to finish | `FACULTY`, `FACULTY_COURSE_VIEW`, and `ATTENDANCE_MANAGE` | `OPEN` attendance sessions of the offerings taught. |
| Approvals | `APPROVAL_TASK_VIEW` | The approval engine's own task list. |

- **Dismissals:** the only thing stored is a dismissal (`todo_dismissals`: account and item key). A grading key carries the waiting count, so new work shows up again.
- **Pages:** the list is at `/todo` (To-Do in the Common links), and its first five items are on the dashboard.
- **Access:** links lead to the existing pages, which check access again.

## Personal calendar (`calendar.personal`)

- **Personal entries:** `personal_calendar_events` holds a person's own entries: a title, optional notes, and a date range. Only the owner sees them, and the owner may delete them, because they are the person's own notes. Another person's entry answers 404. The routes are under `/api/v1/calendar/me/personal-events`.
- **Subscription link:** `calendar_feed_links` holds one active link per account.
  - Its secret is 32 random bytes, shown only once.
  - Only the secret's SHA-256 fingerprint is stored.
  - Making a new link revokes the old one, and revoking takes effect at once.
- **The feed:** `GET /api/v1/calendar/feeds/{secret}.ics` is public on purpose (listed in `SecurityConfig`), because calendar apps cannot sign in.
  - It returns iCalendar text covering 30 days back to 180 days ahead.
  - The calendar is built with exactly the signed-in calendar's rules, from the account's current roles and permissions.
  - A wrong or revoked link, a disabled account, or an account that lost `ACADEMIC_CALENDAR_VIEW` or `CALENDAR_PERSONAL_USE` gets the same 404.
- **Permission:** the personal routes need `CALENDAR_PERSONAL_USE` (`V93`) with `ACADEMIC_CALENDAR_VIEW`.
- **Calendar page:** `/calendar` now has Agenda and Month views (`?view=month`), My entries, and the subscription card.
