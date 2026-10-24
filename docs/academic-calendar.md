# Academic Calendar

The academic calendar has two sources, and neither is copied into the other:

- **Official academic events** (`AcademicCalendarEvent`) are academic data, managed by admins. They are dated holidays, examination weeks, registration windows, and other academic dates.
- **Assignment deadlines** stay on `LmsAssignment.dueAt`. They are read from the assignment every time the calendar is loaded and are never stored as calendar events.

```text
AcademicSession
      |
      +-- AcademicCalendarEvent  (date-based, admin-managed)
                  |
                  +-- optional Semester of that same session

LmsCourse
      |
      +-- LmsAssignment.dueAt    (absolute moment, faculty-managed)

GET /api/calendar/me  ->  official events that apply to the account
                          + each role's assignment deadlines, read at request time
```

The event model is in `com.stacc.backend.academic.calendar`, admin management in `academic.admin`, and the shared read model in `com.stacc.backend.calendar`, a read-only module like `dashboard` that composes the academic and LMS query services and stores nothing. Academic code never refers to LMS code: the calendar module is the only place where the two meet.

## AcademicCalendarEvent

Table `academic_calendar_events` (`V41`).

| Field | Rules |
| --- | --- |
| `academicSession` | Required. Every event belongs to one academic session. |
| `semester` | Optional. Without one, the event applies across the whole session; with one, only to that semester, which must belong to the same session. The server checks this; there is no database rule across the two tables. |
| `title` | Required plain text, at most 200 characters. Blank titles are refused. |
| `description` | Optional plain text, at most 5,000 characters, shown as text and never as HTML or Markdown. Blank becomes empty. |
| `category` | `ACADEMIC`, `HOLIDAY`, `EXAM`, `REGISTRATION`, or `OTHER`, stored as text. There is no `ASSIGNMENT` category. |
| `startDate`, `endDate` | Required plain dates (`LocalDate`, `DATE`) with no time or time zone. The end date is the last day and is included: a one-day event ends on the day it starts, and the end is never before the start (also a database `CHECK`). Dates are between 2000-01-01 and 9999-12-31. |
| `status` | `DRAFT`, `PUBLISHED`, or `CANCELLED`, stored as text. |
| `publishedAt`, `cancelledAt` | Set by the server's clock on the first publication and on cancellation. |

Foreign keys go to `academic_sessions` and `semesters`, with no cascade. Indexes cover the session, the semester, and the status, each with the dates.

## Lifecycle

```text
DRAFT --publish--> PUBLISHED --cancel--> CANCELLED
  |                                          ^
  +------------------cancel------------------+
```

- A new event is always `DRAFT`, with no publication or cancellation time. A request cannot set the status or either time.
- Publishing a draft records `publishedAt`. Publishing a published event changes nothing, and a cancelled event cannot be published again (409).
- Cancelling a draft or a published event records `cancelledAt` and keeps `publishedAt`. Cancelling again changes nothing.
- There is no restore and no delete route. Events are kept as history.

### Editing

- A draft can change everything: title, description, category, dates, session, and semester.
- A published event can be corrected: title, description, category, and dates. It stays published and keeps its `publishedAt`. Its session and semester are fixed after the first publication; sending a different one is refused with 409. A wrongly scoped published event is cancelled and created again.
- A cancelled event is read-only (409).

### Who sees cancelled events

Students, faculty, and the admin calendar see `PUBLISHED` events and `CANCELLED` events that were published first (`publishedAt` set), marked as cancelled, so an event people have seen never silently disappears. A draft never shows, and neither does a draft that was cancelled before it was published. The rule is `AcademicCalendarEvent.isVisibleToUsers()`, and the visible-event queries narrow the same way.

## Who sees which official events

`AcademicCalendarEventQueryService` decides this from academic records alone; no LMS course is needed. The account always comes from the access token.

| Role | Events |
| --- | --- |
| `STUDENT` | Events of each semester the student has a semester enrollment for with status `ENROLLED` or `COMPLETED`, and the session-wide events of those semesters' sessions. Completed enrollments count so history stays visible; `WITHDRAWN` ones do not. The student profile's status does not matter, as with attendance and results. |
| `FACULTY` | Events of each semester the faculty member teaches in through a current teaching assignment (`FacultyTeachingAccessService.isCurrentTeaching`: an active faculty profile and an active assignment), and the session-wide events of those sessions. An offering needs no LMS course. |
| `ADMIN` | Every visible event in the range, whatever its session or semester. |

An account with several roles gets the union: for example a student who also teaches sees the events of both sets of semesters. An admin sees every visible event.

An event belongs to a range when it starts on or before the range's last day and ends on or after its first day, so an event running across either edge is included.

## Assignment deadlines

Deadlines are never stored for the calendar. Changing an assignment's `dueAt` shows on the calendar the next time it is loaded, and reading the calendar writes nothing.

- **Student deadlines**: published assignments of the LMS courses `LmsStudentCourseQueryService` lists for the student, the same rule as the course list and the student dashboard. Drafts, archived assignments, and assignments of courses the student may not use never appear.
- **Faculty deadlines**: draft and published assignments of the LMS courses `LmsFacultyCourseQueryService` lists for the faculty member, the same rule as the faculty assignment overview: an active teaching assignment and a `DRAFT` or `PUBLISHED` LMS course. Archived assignments, assignments of archived LMS courses, and other faculty members' courses never appear.

Both lists are ordered by `dueAt`, then assignment ID. A student deadline has the assignment and LMS course IDs, course code and name, title, `dueAt`, and maximum marks; a faculty deadline adds its status. No submission, grade, or offering data is included.

## Common calendar API

`GET /api/calendar/me?from=2026-10-01&to=2026-10-31&timeZone=Asia/Kolkata` needs `ACADEMIC_CALENDAR_VIEW` and one of `STUDENT`, `FACULTY`, or `ADMIN`. No account, profile, or enrollment ID is accepted.

| Parameter | Rules |
| --- | --- |
| `from`, `to` | Required ISO dates. `from` must not be after `to`, the range is at most 366 days with both ends included, and both are between 2000-01-01 and 9999-12-31. Otherwise 400. |
| `timeZone` | Required IANA region name, such as `Asia/Kolkata` or `UTC`, checked with Java's `ZoneId` list. Older names that browsers still report, such as `Asia/Calcutta`, are in that list and accepted. Offsets such as `+05:30` and abbreviations such as `IST` are refused with 400. |

The time zone only turns the requested days into the moments deadlines are compared with. It decides nothing about access. Deadlines use a half-open range: from the first moment of `from` in that zone, up to but not including the first moment of the day after `to`. A deadline at 23:59 on 20 October in India belongs to 20 October there, and one a minute after midnight belongs to 21 October. Days that are 23 or 25 hours long because of daylight saving time are handled by the zone rules.

```json
{
  "fromDate": "2026-10-01",
  "toDate": "2026-10-31",
  "timeZone": "Asia/Kolkata",
  "academicEvents": [],
  "studentAssignmentDeadlines": [],
  "facultyAssignmentDeadlines": []
}
```

Each deadline list is filled only for a role the account holds and is empty otherwise; an admin-only account gets two empty lists. Official events carry their ID, title, description, category, dates, status (`PUBLISHED` or `CANCELLED`), session code, and, for a semester event, the semester number and program code.

The answer comes from a fixed number of queries whatever the data: the role's semesters and sessions, one event query, the role's course list, and one deadline query per role. An empty set of courses or semesters sends no query.

## Admin management API

All routes are under `/api/admin/academic/calendar/events`. Reading needs `ADMIN` and `ACADEMIC_CALENDAR_VIEW`; changing also needs `ACADEMIC_CALENDAR_MANAGE`. There is no admin pass outside these rules.

| Route | What it does |
| --- | --- |
| `GET /api/admin/academic/calendar/events` | Events in every status, the earliest start first, then by ID. Optional filters: `from`, `to` (events overlapping them; `from` after `to` is 400), `academicSessionId`, `semesterId` (events of exactly that semester), and `status`. |
| `GET /api/admin/academic/calendar/events/{eventId}` | One event with its session, semester, status, and times. 404 if it does not exist. |
| `POST /api/admin/academic/calendar/events` | Creates a draft (201). The session and semester are loaded on the server: a missing one is 404, a semester of another session is 400, and an end date before the start date is 400. |
| `PUT /api/admin/academic/calendar/events/{eventId}` | Replaces the details, as described under "Editing". Moving a published event or editing a cancelled one is 409. |
| `POST /api/admin/academic/calendar/events/{eventId}/publish` | Publishes a draft; already published changes nothing; cancelled is 409. |
| `POST /api/admin/academic/calendar/events/{eventId}/cancel` | Cancels a draft or a published event; already cancelled changes nothing. |

Every change locks the event's own row first, so an edit and a cancellation of the same event never overwrite each other. Assignment deadlines are never listed or edited here.

Publishing an event, and cancelling a published one, tells the users who see it in their Inbox, once each; cancelling a draft tells nobody. Nothing is sent by email or push (see `notifications-inbox.md`).

## Permissions

| Code | Held by | What it allows |
| --- | --- | --- |
| `ACADEMIC_CALENDAR_VIEW` | `STUDENT`, `FACULTY`, `ADMIN` | Reading one's own calendar, and for admins also the event management list. |
| `ACADEMIC_CALENDAR_MANAGE` | `ADMIN` | Creating, editing, publishing, and cancelling official events. |

Both are added by `V42`. Accounts signed in before it must sign in again to get them; until then the calendar says that access is missing.

## Frontend

- `/calendar` is the shared calendar, the Calendar item of the Common navigation section, shown to accounts holding `STUDENT`, `FACULTY`, or `ADMIN`. It shows one month at a time as an agenda grouped by day, with Previous month, Today, and Next month; each change loads that month only. The month is kept in the address as `?month=2026-10`. An event over several days is listed once, on its first day in the month, with its whole date range. Deadlines are placed on the day they fall on in the browser's time zone. Student deadlines open `/courses/:lmsCourseId/assignments/:assignmentId`; teaching deadlines show Draft or Published and open `/teaching/:lmsCourseId`. Admins get a "Manage academic calendar" link.
- `/admin/academic/calendar` is the Academic Calendar section of the Academic Admin page, with filters, Add calendar event (saved as a draft), Edit, Publish, and Cancel event, each change confirmed. There is no delete.

See `frontend-authentication.md` for the page details.

## Not built yet

Recurring events, personal calendars, reminders, email or push notifications, room or venue booking, timetables and exam timetables, attachments, iCal or Google Calendar import and export, drag and drop, comments, and events created by students or faculty.
