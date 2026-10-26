# Reports and Basic Analytics

Phase 46 adds read-only reports for faculty and admins. Every report is counted live by MySQL with `COUNT` and `GROUP BY` from the records the other modules already own. Nothing is stored: there are no report tables, snapshots, caches, saved reports, or scheduled jobs.

The backend lives in the `report` module (`com.stacc.backend.report`). It owns no data and no access rule of its own; it reuses each domain's repositories and access services.

## What reports never show

Reports are counts, nothing more. They never calculate or show:

- GPA, SGPA, CGPA, ranks, toppers, pass or fail, or pass percentages;
- averages, attendance percentages, performance or engagement scores;
- trends, predictions, AI insights, or charts.

There is no PDF export, no report storage, and no data warehouse.

## Permissions

| Permission | Role | Allows |
| --- | --- | --- |
| `FACULTY_REPORT_VIEW` | `FACULTY` | The faculty report routes. |
| `ADMIN_REPORT_VIEW` | `ADMIN` | The admin report routes, including the CSV export. |

Both come from migration `V60`, which looks roles and permissions up by name. Every route needs the role and the permission together; there is no admin pass, and an account signed in before `V60` must sign in again.

## Faculty reports

Faculty routes need `FACULTY` and `FACULTY_REPORT_VIEW`. The faculty member always comes from the access token.

### `GET /api/reports/faculty/my-courses`

One row per offering the faculty member teaches through an `ACTIVE` teaching assignment, from `LmsFacultyManagedCourseQueryService`. Offerings without an LMS course and with an archived one stay listed. Each row has the course and its context, the LMS course and status (if any), and four counts:

- active (`ENROLLED`) course enrollments;
- assignments (`DRAFT` and `PUBLISHED`), or `null` when there is no open LMS course;
- finalized attendance sessions;
- published results.

### `GET /api/reports/faculty/course-offerings/{courseOfferingId}`

The report for one offering. `FacultyTeachingAccessService` must find an `ACTIVE` faculty profile with an `ACTIVE` teaching assignment for it; anything else, including a missing offering, answers the same 404 "Course report not found."

| Section | Counts |
| --- | --- |
| Course | Code, name, credits, program, semester, session, LMS course and status. |
| Enrollment | Course enrollments `ENROLLED`, `COMPLETED`, and `WITHDRAWN`. |
| Assignments | `DRAFT` and `PUBLISHED` assignments (archived ones are left out), submissions with at least one attempt for published assignments, and submissions needing grading. Marked unavailable, with zeros, when the offering has no LMS course or it is archived. |
| Attendance | `OPEN` and `FINALIZED` sessions, and `PRESENT` and `ABSENT` records of finalized sessions only. `UNMARKED` records and open sessions are never counted as absent. No percentage. |
| Results | `DRAFT` and `PUBLISHED` official results. |

"Needs grading" uses the faculty dashboard rule: a submission of a `PUBLISHED` assignment whose latest attempt (`latestAttemptNumber`) has no grade. Older ungraded attempts never count.

## Admin reports

Admin routes need `ADMIN` and `ADMIN_REPORT_VIEW`. They take three optional filters: `academicSessionId`, `programId`, and `semesterId`.

A filter that names no record, or a semester outside the chosen program or session, answers 400 with a plain message, such as "The semester does not belong to the selected program." Filters only narrow the offerings and enrollments counted.

### `GET /api/reports/admin/overview`

- **Academic:** departments, programs, and courses (always college-wide), and course offerings (filtered).
- **Enrollment:** semester enrollments and course enrollments by `ENROLLED`, `COMPLETED`, and `WITHDRAWN` (filtered).
- **Operations:** college-wide counts, each section only when the caller also holds its domain permission:
  - **Helpdesk** (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`) with `HELPDESK_TICKET_VIEW_ALL`, decided by `HelpdeskAccessService.canViewAll`;
  - **Hostel** (active and vacated allocations) with `HOSTEL_MANAGE`;
  - **Campus services** (`SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED`) with `CAMPUS_SERVICE_MANAGE`. A submitted request is counted by the status its approval gives it, exactly as Campus shows it.

A section the caller may not see is `null` and left out of the page, never shown as zero.

### `GET /api/reports/admin/enrollments`

One row per course offering, in the admin offering list's order (newest session first, then program, semester, and course code), with `ENROLLED`, `COMPLETED`, and `WITHDRAWN` course-enrollment counts. It is paged with `page` (from 0) and `size` (1 to 100, default 20).

### `GET /api/reports/admin/enrollments/export.csv`

The same rows, under the same filters, as a CSV file:

- columns: Academic Session, Program, Semester, Course Code, Course Name, Enrolled, Completed, Withdrawn;
- no record IDs;
- every cell is quoted, with quotes doubled;
- a cell starting with `=`, `+`, `-`, `@`, a tab, or a carriage return gets a leading `'`, so spreadsheets never run it as a formula;
- `text/csv` as an attachment named `stacc-enrollment-report.csv`.

The file is built in memory for each request and never written to disk. Clients must accept `text/csv`.

## Frontend

The **Reports** item opens `/reports` in the Faculty section for accounts holding `FACULTY` and in the Admin section for accounts holding `ADMIN`. There is no Student item. An account holding both sees one page with the faculty part first, then the admin part; nothing uses `roles[0]`.

- **Faculty part** ("Teaching and course activity reports."): one card per offering taught, with **View report** opening `/reports/faculty/course-offerings/:courseOfferingId` (**Course Report**). A 404 there shows "Course report unavailable".
- **Admin part** ("Academic and operational reports."): Academic Session, Program, and Semester filters with **Apply filters** and **Clear filters**, the overview sections, and the **Course Enrollment Report** (a table on wide screens, stacked records on phones, with paging).
- The filters and page live in the address (`session`, `program`, `semester`, `page`), so refresh, Back, and Forward keep them.
- **Export CSV** downloads through `apiClient` as a file, so the token stays in the header and never in the address.
- Loading, empty, and error states use the shared page states with **Retry**. A 403 shows access denied and never signs out.

Requests go through `src/api/reportsApi.ts`; types are in `src/types/reports.ts`, and the page parts in `src/reports/`.

## Performance

The reports use grouped counts over the existing foreign-key indexes (course offering, semester, session, and LMS course columns). Phase 46 adds no index; add one only when a measured query needs it. Lists of IDs are never empty in a query, and the CSV counts offerings in batches of 500.

## Not built yet

- Student reports, user or account reports, and people directories.
- Saved, scheduled, emailed, or PDF reports, and report history.
- Charts, trends, comparisons over time, and any analytics beyond counts.
