# Student Dashboard

The student dashboard is a read-only overview of the signed-in student's academic activity. It owns no data and stores nothing: no table, cache, or metric is kept for it. Every value comes from the feature that owns it, under that feature's own visibility rules. The code is in `com.stacc.backend.dashboard` (`StudentDashboardController`, `StudentDashboardQueryService`, `StudentDashboardResponse`), a small module for read-only overviews composed from other modules.

```text
GET /api/dashboard/student
        |
        +-- courses          <- LmsStudentCourseQueryService (the student's course list)
        +-- upcoming work     <- LmsAssignment, only for those courses
        +-- announcements     <- LmsAnnouncement, only for those courses
        +-- attendance        <- StudentAttendanceQueryService (own finalized attendance)
        +-- results           <- StudentResultQueryService (own published results)
```

## Access

`GET /api/dashboard/student` needs the `STUDENT` role and `STUDENT_DASHBOARD_VIEW` (`V38`, `STUDENT` only). It takes no parameters: the student is the account in the access token, and no profile, account, or enrollment ID from the client is read. Passing the rule only lets the student ask; what each section contains is still decided by the rules below. `FACULTY` and `ADMIN` get no pass, and an account with both `STUDENT` and another role gets its student dashboard.

## Sections

| Field | What it holds | Preview |
| --- | --- | --- |
| `courseCount`, `courses` | The LMS courses the student can use now, from `LmsStudentCourseQueryService.getAccessibleCourses`, the same rule as `GET /api/lms/my-courses`: active profile, both enrollments `ENROLLED`, an active membership, and a `PUBLISHED` course. Draft, archived, and membership-only courses are never counted. | First 6, in course code order; `courseCount` is the full number. |
| `upcomingAssignmentCount`, `upcomingAssignments` | `PUBLISHED` assignments of exactly those courses whose `dueAt` is now or later, compared as absolute UTC moments with the application `Clock`. A deadline that has passed is not upcoming. Nothing is said about whether the student has submitted: there is no completion, missing, or overdue model. | First 5, nearest deadline first, then assignment ID; the count covers all of them. |
| `recentAnnouncements` | `PUBLISHED` announcements of exactly those courses: title and publication time, without the message. There is no read or unread state. | First 5, newest `publishedAt` first, then announcement ID. |
| `attendanceCourseCount`, `attendance` | `StudentAttendanceQueryService.getMyAttendance`, the same as `GET /api/attendance/student/me`: the student's own finalized sessions only, per course offering, with the backend's own percentage. A course without finalized attendance is not listed, so 0% is never shown for attendance that was never taken. There is no overall percentage across courses and no threshold or warning. | First 4, in course code order; the count is the full number. |
| `resultCount`, `recentResults` | `StudentResultQueryService.getMyResults`, the same as `GET /api/results/student/me`: the student's own `PUBLISHED` results. Remarks are left out; the grade is null when none was entered. There is no GPA, SGPA, CGPA, rank, average, or pass or fail. | First 5, latest `publishedAt` first, then latest session, highest semester, and course code. |

Attendance and results follow the student's own course enrollments and need no LMS course, so a student without current LMS courses still sees their attendance and results while the course, assignment, and announcement sections are empty. A section without data is empty, never an error.

## Queries

The dashboard reads in a fixed number of queries, whatever the number of courses: the student's memberships with their courses, one page of upcoming assignments with its count, one page of recent announcements, the student's finalized attendance records, and the student's published results. Course names on assignments and announcements come from the course list, not from a query per row. When the student has no current course, the assignment and announcement queries are not made at all, so no empty `IN` list is ever sent.

## Frontend

The existing home page, `/`, shows "Student Dashboard" for any account holding `STUDENT`; an account that is also faculty sees it above its faculty dashboard (see `frontend-authentication.md` and `faculty-dashboard.md`). Its sections link to the full pages: `/courses`, `/courses/:lmsCourseId`, `/courses/:lmsCourseId/assignments/:assignmentId`, `/academic/attendance`, and `/academic/results`.

## Not built yet

- The admin dashboard (Phase 37).
- Notifications, unread counts, calendars, charts, progress, predictions, and recommendations.
