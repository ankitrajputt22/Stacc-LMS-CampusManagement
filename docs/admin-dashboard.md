# Admin Dashboard

The admin dashboard is a read-only overview of academic setup and enrollment. It owns no data and stores nothing: every count is read at request time, and no table, cache, snapshot, or metric is kept for it. The code is in `com.stacc.backend.dashboard` (`AdminDashboardController`, `AdminDashboardQueryService`, `AdminDashboardResponse`), next to the student and faculty dashboards.

```text
GET /api/dashboard/admin
        |
        +-- academicMasters     <- row counts of departments, programs, academic sessions, semesters, courses
        +-- academicOperations  <- row count of course offerings; ENROLLED semester and course enrollments
        +-- courseOfferings     <- first page of the admin offering list (CourseOfferingRepository.findAllWithContext)
```

## Access

`GET /api/dashboard/admin` needs the `ADMIN` role and `ADMIN_DASHBOARD_VIEW` (`V40`, `ADMIN` only). It takes no parameters and only reads: there is no other method on it, and it gives no right to change masters, offerings, or enrollments, whose own permissions are unchanged. `STUDENT` and `FACULTY` get no pass; an account that also holds `ADMIN` gets this dashboard beside its others.

## What each value means

| Field | Meaning |
| --- | --- |
| `academicMasters.departmentCount`, `programCount`, `academicSessionCount`, `semesterCount`, `courseCount` | Every record of that master, whatever its status (active, inactive, planned, or closed). No "current" or "valid" filter is applied. |
| `academicOperations.courseOfferingCount` | Every course offering, whatever its status. Catalog courses and LMS courses are never counted here: `Course` and `CourseOffering` stay separate. |
| `academicOperations.activeSemesterEnrollmentCount` | Semester enrollments whose status is `ENROLLED`. Completed and withdrawn ones are not counted. |
| `academicOperations.activeCourseEnrollmentCount` | Course enrollments whose status is `ENROLLED`. Completed and withdrawn ones are not counted. |
| `courseOfferings` | Up to 5 offerings (`OFFERING_PREVIEW_LIMIT`) with their ID, course code, name, and credits, semester number, program code, and session code. They are the first page of the admin offering list, read by the same query and in the same order: the latest session first, then program code, semester number, course code, and offering ID. It is not a "recent" list. |

The answer holds no student, faculty, account, or LMS data, and nothing is derived from the counts: no rates, averages, trends, rankings, or activity. With no academic data every count is 0 and the preview is empty, with 200.

## Queries

Eight count queries and one bounded preview query that joins the course, semester, program, and session, so no query is made per offering.

## Frontend

The home page, `/`, shows "Admin Dashboard" for an account holding `ADMIN` (see `frontend-authentication.md`). It links to the existing Academic Admin pages: `/admin/academic`, `/admin/academic/offerings`, `/admin/academic/enrollments`, and each offering's roster at `/admin/academic/offerings/:courseOfferingId/enrollments`.

## Not built yet

- User management, reports, notifications, audit or activity history, and system monitoring.
- Pass rates, averages, rankings, performance or engagement analytics, predictions, recommendations, and charts.
