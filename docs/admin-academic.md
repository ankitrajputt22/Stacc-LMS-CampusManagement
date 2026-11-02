# Admin Academic

Admins manage academic data in `com.stacc.backend.academic.admin`:

- the reusable masters: departments, programs, academic sessions, semesters, and courses (one controller and one management service per master, with records in `AcademicMasterRequests` and `AcademicMasterResponses`);
- course offerings, one course taught in one semester (`AdminCourseOfferingController`, `CourseOfferingManagementService`);
- official student enrollments in semesters and course offerings (`AdminEnrollmentController`, `AdminEnrollmentQueryService`, `AcademicEnrollmentManagementService`, `CourseEnrollmentWorkflow`), with records in `OfferingEnrollmentRequests` and `OfferingEnrollmentResponses`;
- official academic calendar events (`AdminAcademicCalendarController`, `AcademicCalendarManagementService`, with records in `AcademicCalendarRequests` and `AdminCalendarEventResponse`), described in `academic-calendar.md`.

The masters, offerings, and enrollments work on their existing tables; calendar events have their own table, `academic_calendar_events`.

```text
Department ------< Program ------< Semester >------ AcademicSession
    |
    +------< Course        (the permanent catalog)
```

## Masters and offerings

The masters are reusable definitions. A `CourseOffering`, one course taught in one semester, is semester-specific delivery data with its own section below. A course stays a permanent catalog entry: it has an owning department and no semester, session, teacher, or LMS course. LMS courses and accounts are not managed here; teaching assignments are, under Faculty Assignments, and the academic mapping of student and faculty profiles under Students and Faculty.

## What can change

What identifies a record, its code and what it belongs to, is set on creation and never changes, because other records are identified by it.

| Master | Created with | Editable | Fixed after creation | Status |
| --- | --- | --- | --- | --- |
| Department | `code`, `name`, optional `description` | `name`, `description`, `status` | `code` | `ACTIVE` or `INACTIVE` |
| Program | `departmentId`, `code`, `name`, `durationSemesters`, `degreeType`, optional `maxCreditsPerSemester` | `name`, `degreeType`, `durationSemesters`, `maxCreditsPerSemester`, `status` | department, `code` | `ACTIVE` or `INACTIVE` |
| Academic session | `code`, `startDate`, `endDate` | `startDate`, `endDate`, `status`; "current" through its own route | `code` | `PLANNED`, `ACTIVE`, `CLOSED` |
| Semester | `programId`, `academicSessionId`, `semesterNumber` | `status`; the registration period through its own route | program, session, number | `PLANNED`, `ACTIVE`, `CLOSED` |
| Course | `departmentId`, `code`, `name`, `credits`, optional `shortName`, `description`, `courseType` | all but the fixed ones | department, `code` | `ACTIVE` or `INACTIVE` |

- `description` (department up to 1000 characters, course up to 2000) and a course's `shortName` (up to 20, such as DBMS) are optional plain text; blank stores nothing.
- `degreeType` is `UNDERGRADUATE`, `POSTGRADUATE`, `DIPLOMA`, `DOCTORATE`, or `CERTIFICATE`; `courseType` is `CORE`, `ELECTIVE`, `LAB`, `PROJECT`, or `AUDIT`. Left out on creation they are `UNDERGRADUATE` and `CORE`, and left out on an edit they stay as they were. They describe the record and decide nothing yet.
- **Inactive or closed parents take no new children** (409 with a plain message): an inactive department takes no new programs or courses, an inactive program no new semesters, a closed session no new semesters, an inactive course no new offerings, and a closed semester no new offerings. What already exists stays as it is.
- **One current session.** `PATCH /api/v1/admin/academic/sessions/{academicSessionId}/current` makes a session the college's current one, and the previous current session an ordinary one, in one step. A closed session cannot be current (409), and closing the current session also stops it being current. The current session is locked while it changes, and a unique generated column (`current_marker`) lets the database hold only one current session; a race answers 409 "Another session was just made current." The migration marks the active session that started last.

- Codes are stored in uppercase without surrounding spaces. New departments, programs, and courses are `ACTIVE`; new sessions and semesters are `PLANNED`.
- Sessions and semesters move forward only: `PLANNED` to `ACTIVE` or `CLOSED`, `ACTIVE` to `CLOSED`. Going back to `PLANNED`, or making a closed one active again, answers 409.
- A session must end after it starts. Overlapping sessions, and several active ones, are allowed: no such rule has been decided.
- A semester number is from 1 up to its program's `durationSemesters`. A program's duration cannot drop below the highest semester it already has (409).
- **Registration period (v0.4.1).** `PUT /api/v1/admin/academic/semesters/{semesterId}/registration` sets or clears `registrationOpensAt`, `registrationClosesAt`, and `dropDeadline`. They are moments with a zone or offset (a zone-less time is refused), stored in UTC (`DATETIME(6)`), between the years 2000 and 9999. Both ends or neither (400), closing after opening (400), and a drop deadline only with a period and not before it opens (400); a database check holds the same rule. A closed semester keeps its period (409). Registration is open from the opening moment until just before the closing one; without a drop deadline students cannot drop. Sending all three empty clears the period. Student registration itself comes in a later phase.
- **Credit limit.** A program's optional `maxCreditsPerSemester` is an exact decimal above 0 and at most 60 (`DECIMAL(5,2)`); empty means no limit, and an edit always writes what is sent.
- Credits are an exact decimal above 0 and at most 20, with at most two decimal places (`DECIMAL(4,2)`); `1.5` is kept as `1.50`, never rounded.
- There is no delete for any master, and no `id` in any request body: the record being edited comes from the route. Editing never touches the records that refer to a master: offerings, students, results, attendance, and LMS data stay as they are.

## Relationships

A parent is named by its ID (`departmentId`, `programId`, `academicSessionId`) and loaded on the server; nothing else about it is taken from the client, and a missing parent is never created. A parent that does not exist answers 400, such as "The selected department does not exist.", and nothing is saved. Answers carry the parent's ID, code, and name for context; no entity is sent as it is.

## Uniqueness

The database's unique constraints decide: department code and name, a program's code and name within its department, session code, a semester's program, session, and number together, and course code. They compare as MySQL's collation does, ignoring case. A friendly check before saving answers 409 with a clear message, such as "A department with this code already exists."; when two requests race, the constraint refuses the second and it gets the same safe 409. No SQL message, constraint name, or stack trace reaches a client.

## Routes and permissions

Every route is under `/api/v1/admin/academic`, which `SecurityConfig` keeps to accounts with the `ADMIN` role before a request body is even read. Reading also needs `ACADEMIC_MASTER_VIEW`; creating and editing need `ACADEMIC_MASTER_VIEW` and `ACADEMIC_MASTER_MANAGE`. Both permissions belong to `ADMIN` only (`V35`). There is no admin pass anywhere else.

| Master | Collection | One record |
| --- | --- | --- |
| Departments | `GET`, `POST /api/v1/admin/academic/departments` | `GET`, `PUT /api/v1/admin/academic/departments/{departmentId}` |
| Programs | `GET`, `POST /api/v1/admin/academic/programs` | `GET`, `PUT /api/v1/admin/academic/programs/{programId}` |
| Academic sessions | `GET`, `POST /api/v1/admin/academic/sessions` | `GET`, `PUT /api/v1/admin/academic/sessions/{academicSessionId}` |
| Semesters | `GET`, `POST /api/v1/admin/academic/semesters` | `GET`, `PUT /api/v1/admin/academic/semesters/{semesterId}`; `PUT .../{semesterId}/registration` |
| Courses | `GET`, `POST /api/v1/admin/academic/courses` | `GET`, `PUT /api/v1/admin/academic/courses/{courseId}` |

- Lists: departments and courses by code; programs by department code, then code; sessions by start date, latest first; semesters by session start date (latest first), then program code, then number. Each list is read in one query.
- Setting the current session is `PATCH /api/v1/admin/academic/sessions/{academicSessionId}/current`, with no body.
- `POST` answers 201, `PUT` 200. Invalid fields answer 400 with field errors, a missing record 404, a clash 409. No token answers 401; a student, a faculty member, or an admin without the permission answers 403.

## Sections

A section (`academic.section.Section`) is a group of students within one semester, such as Section A of BTECH-CSE semester 3 in 2026-27. The semester already says the program and the session, so neither is stored again.

- `GET /api/v1/admin/academic/sections` lists every section, or one semester's with `?semesterId=`, the latest session first, each with `enrolledCount`: how many `ENROLLED` students are placed in it now. `POST` creates one with `semesterId`, `code` (uppercase, up to 10 characters, unique within the semester), an optional `name` (up to 60), and an optional `capacity` (1 to 1000; none means no limit). `PUT /{sectionId}` changes the name, capacity, and status. These routes use the academic master permissions.
- A closed semester takes no new sections (409). The semester and code never change. A capacity below the students already enrolled in it answers 409. Sections are deactivated, never deleted; an inactive section keeps its students but takes no more.
- A student is placed through their semester enrollment: `PUT /api/v1/admin/academic/enrollments/semester-enrollments/{semesterEnrollmentId}/section` with `sectionId`, or `null` to take them out. It needs the enrollment permissions. Only an `ENROLLED` semester enrollment can change section, and only to an active section of its own semester (409 otherwise). The semester enrollment is locked first and then the section, always in that order, and the section's enrolled students are counted with a locking read only under its lock, so two placements at once never take its last place twice (`SectionCapacityMySqlIT`). A full section answers 409 "Section A is full: all 60 places are taken."
- The section stays on an ended semester enrollment as history, and only `ENROLLED` students count towards a capacity. Semester enrollment answers carry `sectionId` and `sectionCode`.
- The demo data has sections A and B (60 places each) in BTECH-CSE semester 3, 2026-27, with student1 and studentfaculty1 in A and student2 in B.

## Course offerings

A course offering joins one course and one semester, for the whole semester or for one of its sections. A course is offered at most once per semester and section: the unique key `uk_course_offerings_course_semester_section` is on the course, the semester, and a generated `section_key` (the section's ID, or 0 for the whole semester). It has no room, schedule, or LMS state.

- `POST /api/v1/admin/academic/offerings` takes `courseId`, `semesterId`, an optional `sectionId` (an active section of that semester), an optional `capacity` (1 to 1000), and an optional `registrationStatus` (`DRAFT`, `OPEN`, or `CLOSED`; `DRAFT` when left out). It answers 201 with a `PLANNED` offering. A course or semester that does not exist answers 404; an inactive course, a closed semester, a section of another semester, an inactive section, or an offering that already exists answers 409, also when two requests race.
- A course is offered either to the whole semester or per section, never both in one semester (409 with a plain message), so no student could take it twice.
- The course and semester do not have to share a program or department: the model has no such rule, so none is invented.
- `PUT /api/v1/admin/academic/offerings/{courseOfferingId}` changes the status (`PLANNED` to `ACTIVE` or `CLOSED`, `ACTIVE` to `CLOSED`, never back), the `capacity` (null means no limit, so the page always sends it), and the `registrationStatus` (left out, it stays). The course, semester, and section are structural identity: enrollments, teaching assignments, attendance, results, and an LMS course hang off the offering. A `courseId`, `semesterId`, or `sectionId` different from the stored one answers 409.
- The capacity and registration status are recorded now and used by course registration in v0.4; nothing enforces them yet.
- `GET /api/v1/admin/academic/offerings` lists offerings with course code, name, and credits, semester number, program code, session code, section, capacity, registration status, and `faculty`: every teaching assignment with login ID, name from the person profile, department, role, and status. `GET` of one offering carries the same. Other answers that include an offering leave `faculty` null. The list is ordered by session start date (latest first), program code, semester number, course code, section code, and ID; the optional filters `semesterId`, `courseId`, `academicSessionId`, and `programId` narrow it.
- A course enrollment into a section's offering needs the student placed in that section (409 "This course offering is for section A. Place the student in section A first."), and a student enrolled in their section's offerings cannot move to another section, or out of it, until those course enrollments have ended.
- There is no delete.

## Faculty assignments

Who teaches an offering is its `TeachingAssignment`s (`academic.teaching`), one row per faculty profile and offering. Each has a role, `PRIMARY` or `CO_FACULTY`, and a status. An active assignment is what gives the faculty member attendance, results, and LMS access for the offering through `FacultyTeachingAccessService`, so every change takes effect at once.

- `GET /api/v1/admin/academic/offerings/{courseOfferingId}/faculty` lists an offering's assignments, active first. `GET /api/v1/admin/academic/faculty?q=` finds faculty profiles whose login ID contains the text, at most 50, with only the profile ID, login ID, name, department code, and profile status.
- `POST /api/v1/admin/academic/faculty-assignments` takes `courseOfferingId`, `facultyProfileId`, and `role`. The faculty profile and its account must be active and the account must hold `FACULTY`; the offering must not be closed. An active assignment of the same pair answers 409; an inactive one is made active again with the new role, so its history stays in one row.
- `PUT /api/v1/admin/academic/faculty-assignments/{teachingAssignmentId}` takes `role` and `status`. Making it inactive removes the access and keeps the row; making it active again checks the faculty member again. Nothing is deleted.
- An offering has at most one active `PRIMARY` assignment (409 otherwise). Every change locks the offering row first (`findByIdForUpdate`), so two admins at once never leave two primaries (`PrimaryFacultyMySqlIT`). `V69` made the earliest active assignment of each existing offering primary and the others co-faculty.
- The routes need `ADMIN` with `FACULTY_ASSIGNMENT_VIEW` and, to change, `FACULTY_ASSIGNMENT_MANAGE` (`V69`).
- The admin page is the Faculty Assignments tab (`/admin/academic/faculty-assignments`, with `?offering=` for one offering, which the offerings list links to): choose a session, semester, and offering, see who teaches it, change roles, remove or assign again after a confirmation, and assign a faculty member found by employee ID. The offerings list shows the active faculty, or Unassigned.

## Academic profiles

The official academic mapping of a student is their `StudentProfile` (program, which gives the department, the admission session, an optional roll number, and a status); of a faculty member, their `FacultyProfile` (primary department and status). Where a student studies now (session, semester, and section) is never stored: it is their `ENROLLED` semester enrollment of the latest session.

- `GET /api/v1/admin/academic/profiles/students?q=` finds students whose login ID or roll number contains the text, at most 50, each with name, roll number, program, department, admission session, status, and `current`. `PUT /profiles/students/{studentProfileId}` takes `rollNumber` (up to 30, stored in uppercase, unique, blank clears it; a clash is 409) and `status`. An inactive profile takes no new enrollments and opens no LMS course.
- `GET /api/v1/admin/academic/profiles/faculty?q=` finds faculty whose login ID contains the text, at most 50, with name, primary department, status, and `activeTeachingCount`. `PUT /profiles/faculty/{facultyProfileId}` takes `departmentId` (an active department; the primary department never limits what they teach) and `status`. An inactive profile cannot be assigned and loses teaching access.
- These routes need `ADMIN` with `ACADEMIC_PROFILE_VIEW` and, to change, `ACADEMIC_PROFILE_MANAGE` (`V70`). They never return contact details and never change accounts, names, a student's program, or enrollments.
- `GET /api/v1/academic/me` (`academic.profile`) gives the signed-in person their own profile: a `student` part for an account holding `STUDENT` with a student profile (identity, `current`, and every semester enrollment, latest first, with its course enrollments and only `PUBLISHED` results) and a `faculty` part for an account holding `FACULTY` with a faculty profile (department and the offerings they actively teach, with role). It needs `ACADEMIC_PROFILE_VIEW_OWN` with `STUDENT` or `FACULTY`, takes the account from the token, reads every role the account holds, and changes nothing. There is no GPA, percentage, rank, or pass or fail.
- The admin pages are the Students and Faculty tabs; the person's own page is `/academic/profile`, linked from the Account menu for students and faculty. The demo students have roll numbers 25CSE001 to 25CSE003.

## Student lookup

`GET /api/v1/admin/academic/enrollments/students?q=` finds existing student profiles for enrollment. It matches part of the account's login ID, ignoring case; `%` and `_` match themselves. It answers at most 50, by login ID, and an empty `q` lists the first 50. Text over 100 characters answers 400. Each entry has only `studentProfileId`, `loginId`, `programId`, `programCode`, `programName`, `admissionSessionCode`, and `studentProfileStatus`: never a password hash, role, permission, account ID, or account status. It is read-only and not a user directory; nothing here creates or edits accounts or profiles.

## Semester enrollments

- `GET /api/v1/admin/academic/enrollments/students/{studentProfileId}/semesters` answers the student and every semester enrollment they have had, latest session first, then the highest semester number.
- `POST` on the same route takes only `semesterId`; the route names the student. It answers 201 with an `ENROLLED` enrollment. A missing student or semester answers 404. An `INACTIVE` student profile, a semester of another program than the student's, and a second enrollment in the same semester answer 409 ("This student is already enrolled in this semester."). The status is never taken from the request.
- `PUT /api/v1/admin/academic/enrollments/semester-enrollments/{semesterEnrollmentId}/status` takes `status`. The row is locked while it changes.

## Course enrollments

- `GET /api/v1/admin/academic/enrollments/semester-enrollments/{semesterEnrollmentId}/courses` answers the student, the semester enrollment, and its course enrollments by course code, with course code, name, and credits.
- `POST` on the same route takes only `courseOfferingId` and answers 201 with the `ENROLLED` course enrollment. The semester enrollment must be `ENROLLED` (409 otherwise) and locked while the course is added; a missing semester enrollment or offering answers 404. The offering must belong to the semester enrollment's own semester: an offering of another semester answers 409 ("This course offering belongs to a different semester than the semester enrollment."). A second enrollment in the same offering answers 409 ("This student is already enrolled in this course offering."). The offering row is locked after the semester enrollment and its capacity counts `ENROLLED` students, as self-registration does (409 "CS301 is full: all 60 places are taken."). The registration status and period do not limit admins. No other program rule is applied, because the model has none.
- `GET /api/v1/admin/academic/enrollments/course-enrollments` searches every course enrollment with filters, one page at a time (the Course Enrollments page; see `course-registration.md`).
- `PUT /api/v1/admin/academic/enrollments/course-enrollments/{courseEnrollmentId}/status` takes `status`. The row is locked while it changes. `DROPPED` is set only by the student (409 from admins); a dropped enrollment is ended like a withdrawn one.

## Status transitions

Semester and course enrollments share one conservative rule: `ENROLLED` may become `COMPLETED` or `WITHDRAWN`; sending the current status changes nothing and answers 200; every other change, such as reopening or moving between `COMPLETED` and `WITHDRAWN`, answers 409. An ended enrollment is history and never changes again.

A semester enrollment cannot end while any of its course enrollments is still `ENROLLED`: that answers 409 ("Active course enrollments must be resolved before closing the semester enrollment.") and nothing changes. Nothing cascades: each course enrollment is completed or withdrawn on its own first. Because enrollments never return to `ENROLLED`, a course enrollment is never `ENROLLED` under a semester enrollment that has ended. Nothing is ever deleted.

## LMS access

Nothing on the LMS side is written when a course enrollment is made or ended. A student uses the offering's LMS course while it is published and their profile, semester enrollment, and course enrollment are active, worked out live on every request (see `lms-foundation.md`). Ending an enrollment ends access at once. No LMS course is ever created here; the faculty who teach the offering create it. Until v0.5.1 a membership copy was provisioned here with a retry route; both are gone.

## Roster

`GET /api/v1/admin/academic/offerings/{courseOfferingId}/enrollments` answers the offering and every course enrollment of exactly that offering, whatever its status, by student login ID, then ID. Each entry has `courseEnrollmentId`, `semesterEnrollmentId`, `studentProfileId`, `studentLoginId`, and `status`, and nothing about the account. A missing offering answers 404. It needs the enrollment view permission, not the offering one.

## Offering and enrollment permissions

`V36` adds `ACADEMIC_OFFERING_VIEW`, `ACADEMIC_OFFERING_MANAGE`, `ACADEMIC_ENROLLMENT_VIEW`, and `ACADEMIC_ENROLLMENT_MANAGE`, all for `ADMIN` only. Every rule also needs the `ADMIN` role, and changing needs the view permission as well, as for the masters. Admins who were signed in before `V36` sign in again to get them.

| Route | Needs |
| --- | --- |
| `GET /api/v1/admin/academic/offerings`, `GET .../offerings/{courseOfferingId}` | `ADMIN`, `ACADEMIC_OFFERING_VIEW` |
| `POST /api/v1/admin/academic/offerings`, `PUT .../offerings/{courseOfferingId}` | `ADMIN`, `ACADEMIC_OFFERING_VIEW`, `ACADEMIC_OFFERING_MANAGE` |
| `GET .../offerings/{courseOfferingId}/enrollments` and the `GET` enrollment routes | `ADMIN`, `ACADEMIC_ENROLLMENT_VIEW` |
| The `POST` and `PUT` enrollment routes | `ADMIN`, `ACADEMIC_ENROLLMENT_VIEW`, `ACADEMIC_ENROLLMENT_MANAGE` |

The master permissions give no access to offerings or enrollments, and the reverse. No route accepts an account, student, or enrollment ID in a body to say whose record changes: the route names it, and the acting admin comes from the access token.

## Frontend

`/admin/academic` has two rows of tabs (`LinkTabs`) under the page header: the areas Structure (Departments to Courses), Teaching (Course Offerings, Faculty Assignments), People (Enrollments, Course Enrollments, Students, Faculty), and Calendar, then the pages of the current area. The pages are Departments, Programs, Academic Sessions, Semesters, Sections, Courses, Course Offerings (`/admin/academic/offerings`), Faculty Assignments (`/admin/academic/faculty-assignments`), Students, Faculty, Enrollments (`/admin/academic/enrollments`), Course Enrollments (`/admin/academic/course-enrollments`), and Academic Calendar (`/admin/academic/calendar`). An offering's roster is at `/admin/academic/offerings/:courseOfferingId/enrollments`. See `docs/frontend-layout.md`.

Every section is built from the shared components in `src/ui` (see `ui-design.md`):

- Each master, and the course offerings, is a `Card` with its Add button, a search box, a status filter and its own filters (programs by department and degree, courses by department and type), and the records in a `DataTable`, 20 to a page (`Pagination`). Search, status, and pages work on the list the API already sent; the offerings' session and semester filters still ask the backend. Edit and the other row actions sit in the last column. Departments, programs, and courses change status with Activate and Deactivate, each confirmed first; their Edit forms no longer carry the status. Sessions show a Current badge and a confirmed Set as current action. Create forms list only parents that can take new children.
- Add and Edit open one form at a time in its own card above the list, with `TextField` and `SelectField` and an error `Alert`. After a save, every academic admin list in the cache (`queryKeys.admin.academic`) is asked again, because the lists fill each other's choices.
- Enrollments start with the student search; a student's semesters and courses are cards with their actions per row (each semester shows its section, with Place in section or Change section while enrolled, listing that semester's active sections with their places), and ending an enrollment is confirmed in an amber box first.
- Programs show their credit limit ("No limit" when empty). Semesters show their registration period and drop deadline in the browser's time zone, and the semester edit form has optional date-and-time fields for them (not for a closed semester), checked for order before sending.
- Statuses are `Badge`s: green for in use (`ACTIVE`, `ENROLLED`), blue for still to come (`PLANNED`), neutral for ended; the word always says it. The admin dashboard on `/` shows read-only counts of these records and links here (see `admin-dashboard.md`).

## Not built yet

- Teaching assignments, student and faculty profiles, and accounts (later phases). LMS courses are created by faculty, not here.
- Bulk or CSV enrollment, select-all actions, timetables, fees, and admissions.
- Deletion and change history.
