# Faculty Dashboard

The faculty dashboard is a read-only overview of the signed-in faculty member's teaching work. It owns no data and stores nothing: no table, cache, snapshot, or metric is kept for it. Every value comes from the feature that owns it, under that feature's own rules. The code is in `com.stacc.backend.dashboard` (`FacultyDashboardController`, `FacultyDashboardQueryService`, `FacultyDashboardResponse`), next to the student dashboard.

```text
GET /api/dashboard/faculty
        |
        +-- teaching courses    <- LmsFacultyManagedCourseQueryService (the Teaching page's list)
        +-- upcoming work        <- LmsAssignment, for the LMS courses faculty can still open
        +-- needs grading        <- latest LmsSubmissionAttempt without an LmsSubmissionGrade
        +-- open attendance      <- AttendanceSession OPEN, with AttendanceRecord counts
```

## Access

`GET /api/dashboard/faculty` needs the `FACULTY` role and `FACULTY_DASHBOARD_VIEW` (`V39`, `FACULTY` only). It takes no parameters: the faculty member is the account in the access token, and no faculty, profile, account, or teaching-assignment ID from the client is read. The permission does not replace `FACULTY_COURSE_VIEW`, `LMS_ASSIGNMENT_GRADE`, or `ATTENDANCE_MANAGE` on the full pages. `STUDENT` and `ADMIN` get no pass; an account with both `STUDENT` and `FACULTY` gets this dashboard and the student one.

## Sections

| Field | What it holds | Preview |
| --- | --- | --- |
| `teachingCourseCount`, `teachingCourses` | Every course offering taught now, from `LmsFacultyManagedCourseQueryService`: an `ACTIVE` faculty profile and an `ACTIVE` teaching assignment, each offering once. An offering without an LMS course counts, with `lmsCourseId` and `lmsCourseStatus` null; an archived LMS course is shown as `ARCHIVED`, never as missing. | First 6, by course code, then offering ID. |
| `upcomingAssignmentCount`, `upcomingAssignments` | `DRAFT` or `PUBLISHED` assignments due now or later (absolute UTC times, the application `Clock`), in those offerings' LMS courses that faculty can still open (`LmsFacultyAccessPolicy`: draft or published). Drafts are shown because they may need publishing; students never see them. Archived assignments, past deadlines, and archived LMS courses are left out. | First 5, nearest deadline first, then assignment ID. |
| `needsGradingCount`, `needsGrading` | Submissions whose **latest** attempt has no grade: the attempt with `attemptNumber = latestAttemptNumber` of its submission and no `LmsSubmissionGrade`, for `PUBLISHED` assignments of the same open LMS courses, as the grading pages allow. An older ungraded attempt never puts a submission in the queue, and a graded older attempt never keeps one out of it. Each entry has the student's login ID, the assignment, the latest attempt's number and time, and whether it was late. | First 5, the longest-waiting latest attempt first (`submittedAt`), then submission ID. |
| `openAttendanceCount`, `openAttendanceSessions` | `OPEN` attendance sessions of the offerings taught, with or without an LMS course; finalized ones are left out. Each has `totalRecords` and `unmarkedCount`, counted from its real records. | First 5, the latest class first (`heldAt`), then session ID. |

A faculty member without current teaching assignments gets 200 with every section empty.

## Queries

The dashboard reads in a fixed number of queries, whatever the number of courses: the teaching assignments with their offerings, the LMS courses of those offerings, one page of upcoming assignments with its count, one page of the grading queue with its count, one page of open sessions with its count, and the record counts of the sessions shown, grouped in one query. Course names come from the teaching list, not from a query per row. When there is no open LMS course, the assignment and grading queries are not made; without offerings, the attendance queries are not made, so no empty `IN` list is sent.

## Frontend

The home page, `/`, shows "Faculty Dashboard" for an account holding `FACULTY` (see `frontend-authentication.md`). It links to the existing pages: `/teaching`, `/teaching/:lmsCourseId`, `/teaching/:lmsCourseId#assignments`, `/assignments`, the grading page with `?submission=`, `/attendance`, and `/attendance/:courseOfferingId?session=`. It adds no grading, assignment, or attendance editor of its own.

## Not built yet

- Class averages, ranks, pass rates, engagement or performance scores, predictions, recommendations, charts, calendars, and notifications.
