# Analytics and Exports

## Rules (decision 7)

`AnalyticsRules` holds the rules in one place:

- Percentages and averages are allowed for a person's own view, and for class-level views only when the group has at least five students. Otherwise the figure is `null`, the page shows a dash with the reason, and only counts remain.
- Never rankings, never GPA, and never a student named in a class view.
- Figures are rounded to one decimal place.
- Everything is worked out live from the other modules' records. Analytics are never stored.

## Student view

`GET /api/v1/analytics/student/me` (`STUDENT` with `ANALYTICS_VIEW_OWN`), shown at `/academic/analytics` ("My progress"), covers:

- **Attendance:** own finalized attendance per course and overall.
- **Assignments:** per current course, published assignments due, handed in, and late, plus the average of their own graded latest attempts.
- **Results:** the average of their own published results.

## Course analytics

`GET /api/v1/analytics/faculty/courses/{lmsCourseId}` (`FACULTY`, `FACULTY_COURSE_VIEW`, `COURSE_ANALYTICS_VIEW`, under the faculty access policy; anything else is 404). It is shown at `/teaching/:id/analytics` and covers:

- **Attendance:** per finalized session and overall.
- **Assignments:** per published assignment, how many were handed in, late, and graded, and the average mark.
- **Quizzes:** per published quiz, how many were handed in and the average score, plus each question's correct rate.
- **Required pages:** completion counts.

## Department analytics (v0.16.3)

`GET /api/v1/analytics/department` (`HOD` with `DEPARTMENT_ANALYTICS_VIEW`, added by `V126`), shown at `/department` ("Department" for accounts holding `HOD`), lists each department the account heads now. Which departments those are comes only from active head terms that `DepartmentHeadResolver` counts (an `ACTIVE` account still holding `HOD`); someone who heads none gets an empty list. For the current academic session it shows:

- **Programs:** the students enrolled in a semester of each program.
- **Offerings:** per offering of the department's courses, the enrolled students, the present rate of finalized attendance (only for groups of at least five), and the number of published results.

Counts only otherwise: no student is named, nothing is ranked, and nothing is stored.

## Area analytics (v0.16.4)

`GET /api/v1/analytics/areas` (`ADMIN`, `LIBRARIAN`, or `PLACEMENT_OFFICER` with `AREA_ANALYTICS_VIEW`, added by `V127`), shown at `/areas` ("Area analytics"), gives live counts per campus area. Each section appears only with that area's own permission; a missing permission leaves it out, never shows zero:

| Section | Needs | Figures |
| --- | --- | --- |
| Hostel | `ADMIN` and `HOSTEL_MANAGE` | beds in service and under maintenance, occupied beds, occupancy |
| Helpdesk | `ADMIN` and `HELPDESK_TICKET_VIEW_ALL` | tickets by status, rated tickets, the average rating (only with at least five ratings) |
| Campus engagement | `ADMIN` and `CAMPUS_ENGAGEMENT_MANAGE` | active groups and memberships, coming and past events, registrations and check-ins at past events, attendance (at least five registrations) |
| Placement | `ADMIN` or `PLACEMENT_OFFICER` with `CAREER_MANAGE` | open opportunities, applications and offers by status |
| Library | `LIBRARIAN` and `LIBRARY_MANAGE` | titles, copies in service, open and overdue loans, holds waiting |
| AI | `ADMIN` | course knowledge indexes by status; AI questions and answers are never stored, so nothing else is counted |

Each figure is one bounded aggregate query (`AreaAnalyticsQueries`); nothing is stored, and no person is named.

## Background exports (report service)

- **Starting one:** `POST /api/v1/exports` (`FACULTY` or `ADMIN` with `REPORT_EXPORT`) starts an XLSX export of a report the caller can already read:
  - `ADMIN_ENROLLMENTS` needs `ADMIN_REPORT_VIEW`.
  - `COURSE_ANALYTICS` needs a course the caller teaches.
- **Running:** the request is recorded and audited (`REPORT_EXPORT_REQUESTED`). It runs after commit on the application task executor, writes the file to private storage, and becomes `READY`.
- **Downloading:** only the person who asked can download it (`REPORT_EXPORT_DOWNLOADED` is audited), until it expires after 24 hours. Expired files are removed when new exports are requested.
- **The file:** cells are always text, so nothing in an export runs as a formula.

## Performance review (v0.16.6)

- **Checked:** the queries every page load runs: the dashboard, course lists, the To-Do list, the calendar, unread counts, and enrollment lists.
- **Fixed:** `V101` adds the two indexes that were missing: course enrollments by offering and status, and attendance sessions by offering and status. The notification and assignment lookups already had theirs.
- **Already in place:**
  - Lists are paged or bounded in the query.
  - No `IN` list is ever sent empty.
  - Per-course loops batch their lookups (grades by attempt IDs, counts grouped in one query).

## Load tests (v0.16.8)

`load/` holds the k6 suite: busy reads, an event registration burst, and a quiz start burst. Its README explains how to run it against a throwaway stack. The first baseline has not been recorded yet.
