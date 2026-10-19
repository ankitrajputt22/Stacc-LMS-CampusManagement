# Admin Academic

Admins manage academic data in `com.stacc.backend.academic.admin`:

- the reusable masters: departments, programs, academic sessions, semesters, and courses (one controller and one management service per master, with records in `AcademicMasterRequests` and `AcademicMasterResponses`);
- course offerings, one course taught in one semester (`AdminCourseOfferingController`, `CourseOfferingManagementService`);
- official student enrollments in semesters and course offerings (`AdminEnrollmentController`, `AdminEnrollmentQueryService`, `AcademicEnrollmentManagementService`, `CourseEnrollmentWorkflow`), with records in `OfferingEnrollmentRequests` and `OfferingEnrollmentResponses`.

All of it works on the existing tables; no table was changed.

```text
Department ------< Program ------< Semester >------ AcademicSession
    |
    +------< Course        (the permanent catalog)
```

## Masters and offerings

The masters are reusable definitions. A `CourseOffering`, one course taught in one semester, is semester-specific delivery data with its own section below. A course stays a permanent catalog entry: it has an owning department and no semester, session, teacher, or LMS course. Student and faculty profiles, teaching assignments, LMS courses, and accounts are not managed here.

## What can change

Each master keeps the fields and lifecycle it already had. What identifies a record, its code and what it belongs to, is set on creation and never changes, because other records are identified by it.

| Master | Created with | Editable | Fixed after creation | Status |
| --- | --- | --- | --- | --- |
| Department | `code`, `name` | `name`, `status` | `code` | `ACTIVE` or `INACTIVE` |
| Program | `departmentId`, `code`, `name`, `durationSemesters` | `name`, `durationSemesters`, `status` | department, `code` | `ACTIVE` or `INACTIVE` |
| Academic session | `code`, `startDate`, `endDate` | `startDate`, `endDate`, `status` | `code` | `PLANNED`, `ACTIVE`, `CLOSED` |
| Semester | `programId`, `academicSessionId`, `semesterNumber` | `status` | program, session, number | `PLANNED`, `ACTIVE`, `CLOSED` |
| Course | `departmentId`, `code`, `name`, `credits` | `name`, `credits`, `status` | department, `code` | `ACTIVE` or `INACTIVE` |

- Codes are stored in uppercase without surrounding spaces. New departments, programs, and courses are `ACTIVE`; new sessions and semesters are `PLANNED`.
- Sessions and semesters move forward only: `PLANNED` to `ACTIVE` or `CLOSED`, `ACTIVE` to `CLOSED`. Going back to `PLANNED`, or making a closed one active again, answers 409.
- A session must end after it starts. Overlapping sessions, and several active ones, are allowed: no such rule has been decided.
- A semester number is from 1 up to its program's `durationSemesters`. A program's duration cannot drop below the highest semester it already has (409).
- Credits are an exact decimal above 0 and at most 20, with at most two decimal places (`DECIMAL(4,2)`); `1.5` is kept as `1.50`, never rounded.
- There is no delete for any master, and no `id` in any request body: the record being edited comes from the route. Editing never touches the records that refer to a master: offerings, students, results, attendance, and LMS data stay as they are.

## Relationships

A parent is named by its ID (`departmentId`, `programId`, `academicSessionId`) and loaded on the server; nothing else about it is taken from the client, and a missing parent is never created. A parent that does not exist answers 400, such as "The selected department does not exist.", and nothing is saved. Answers carry the parent's ID, code, and name for context; no entity is sent as it is.

## Uniqueness

The database's unique constraints decide: department code and name, a program's code and name within its department, session code, a semester's program, session, and number together, and course code. They compare as MySQL's collation does, ignoring case. A friendly check before saving answers 409 with a clear message, such as "A department with this code already exists."; when two requests race, the constraint refuses the second and it gets the same safe 409. No SQL message, constraint name, or stack trace reaches a client.

## Routes and permissions

Every route is under `/api/admin/academic`, which `SecurityConfig` keeps to accounts with the `ADMIN` role before a request body is even read. Reading also needs `ACADEMIC_MASTER_VIEW`; creating and editing need `ACADEMIC_MASTER_VIEW` and `ACADEMIC_MASTER_MANAGE`. Both permissions belong to `ADMIN` only (`V35`). There is no admin pass anywhere else.

| Master | Collection | One record |
| --- | --- | --- |
| Departments | `GET`, `POST /api/admin/academic/departments` | `GET`, `PUT /api/admin/academic/departments/{departmentId}` |
| Programs | `GET`, `POST /api/admin/academic/programs` | `GET`, `PUT /api/admin/academic/programs/{programId}` |
| Academic sessions | `GET`, `POST /api/admin/academic/sessions` | `GET`, `PUT /api/admin/academic/sessions/{academicSessionId}` |
| Semesters | `GET`, `POST /api/admin/academic/semesters` | `GET`, `PUT /api/admin/academic/semesters/{semesterId}` |
| Courses | `GET`, `POST /api/admin/academic/courses` | `GET`, `PUT /api/admin/academic/courses/{courseId}` |

- Lists: departments and courses by code; programs by department code, then code; sessions by start date, latest first; semesters by session start date (latest first), then program code, then number. Each list is read in one query.
- `POST` answers 201, `PUT` 200. Invalid fields answer 400 with field errors, a missing record 404, a clash 409. No token answers 401; a student, a faculty member, or an admin without the permission answers 403.

## Course offerings

A course offering joins one course and one semester, at most once per pair (`uk_course_offerings_course_semester`). It has only those two and a status; no section, room, capacity, schedule, teacher, or LMS state.

- `POST /api/admin/academic/offerings` takes `courseId` and `semesterId`, loads both on the server, and answers 201 with a `PLANNED` offering. A course or semester that does not exist answers 404, a pair that already exists 409 ("This course offering already exists."), also when two requests race.
- The course and semester do not have to share a program or department: the model has no such rule, so none is invented.
- `PUT /api/admin/academic/offerings/{courseOfferingId}` changes the status only: `PLANNED` to `ACTIVE` or `CLOSED`, `ACTIVE` to `CLOSED`, never back. The course and semester are structural identity: enrollments, teaching assignments, attendance, results, and an LMS course hang off the offering, and the columns are mapped `updatable = false`. A request may send `courseId` and `semesterId`, but a value different from the stored one answers 409 ("The course and semester of an offering cannot be changed after it is created. Create a new offering instead."), whether or not dependent records exist yet.
- `GET /api/admin/academic/offerings` lists offerings with course code, name, and credits, semester number, program code, and session code, by session start date (latest first), program code, semester number, course code, and ID. The optional filters `semesterId`, `courseId`, `academicSessionId`, and `programId` narrow it.
- There is no delete.

## Student lookup

`GET /api/admin/academic/enrollments/students?q=` finds existing student profiles for enrollment. It matches part of the account's login ID, ignoring case; `%` and `_` match themselves. It answers at most 50, by login ID, and an empty `q` lists the first 50. Text over 100 characters answers 400. Each entry has only `studentProfileId`, `loginId`, `programId`, `programCode`, `programName`, `admissionSessionCode`, and `studentProfileStatus`: never a password hash, role, permission, account ID, or account status. It is read-only and not a user directory; nothing here creates or edits accounts or profiles.

## Semester enrollments

- `GET /api/admin/academic/enrollments/students/{studentProfileId}/semesters` answers the student and every semester enrollment they have had, latest session first, then the highest semester number.
- `POST` on the same route takes only `semesterId`; the route names the student. It answers 201 with an `ENROLLED` enrollment. A missing student or semester answers 404. An `INACTIVE` student profile, a semester of another program than the student's, and a second enrollment in the same semester answer 409 ("This student is already enrolled in this semester."). The status is never taken from the request.
- `PUT /api/admin/academic/enrollments/semester-enrollments/{semesterEnrollmentId}/status` takes `status`. The row is locked while it changes.

## Course enrollments

- `GET /api/admin/academic/enrollments/semester-enrollments/{semesterEnrollmentId}/courses` answers the student, the semester enrollment, and its course enrollments by course code, with course code, name, and credits.
- `POST` on the same route takes only `courseOfferingId` and answers 201 with an `ENROLLED` course enrollment and an `lmsProvisioning` outcome. The semester enrollment must be `ENROLLED` (409 otherwise) and locked while the course is added; a missing semester enrollment or offering answers 404. The offering must belong to the semester enrollment's own semester: an offering of another semester answers 409 ("This course offering belongs to a different semester than the semester enrollment."). A second enrollment in the same offering answers 409 ("This student is already enrolled in this course offering."). No other program rule is applied, because the model has none.
- `PUT /api/admin/academic/enrollments/course-enrollments/{courseEnrollmentId}/status` takes `status`. The row is locked while it changes.

## Status transitions

Semester and course enrollments share one conservative rule: `ENROLLED` may become `COMPLETED` or `WITHDRAWN`; sending the current status changes nothing and answers 200; every other change, such as reopening or moving between `COMPLETED` and `WITHDRAWN`, answers 409. An ended enrollment is history and never changes again.

A semester enrollment cannot end while any of its course enrollments is still `ENROLLED`: that answers 409 ("Active course enrollments must be resolved before closing the semester enrollment.") and nothing changes. Nothing cascades: each course enrollment is completed or withdrawn on its own first. Because enrollments never return to `ENROLLED`, a course enrollment is never `ENROLLED` under a semester enrollment that has ended. Nothing is ever deleted.

## LMS provisioning

The official course enrollment is saved and committed first. Only then does `CourseEnrollmentWorkflow`, which is not transactional, ask the LMS through `CourseEnrollmentLmsPort`. The academic module defines that port; `lms.membership.AcademicEnrollmentLmsAdapter` implements it with the existing `LmsStudentMembershipProvisioningService`, so academic code never refers to LMS models.

- If the offering has an LMS course, the student gets an active membership (or an inactive one is reactivated): `lmsProvisioning` is `PROVISIONED`. The LMS course does not have to be published; student access still follows the access policy.
- If the offering has no LMS course, the enrollment still succeeds: `NO_LMS_COURSE`. No LMS course is ever created here. The faculty who teach the offering create it later, and creating it gives the offering's current students their memberships (see `lms-foundation.md`).
- If provisioning fails, the enrollment still stands, the failure is logged without personal data, and the answer says `FAILED`. `POST /api/admin/academic/enrollments/course-enrollments/{courseEnrollmentId}/provision-lms` retries it: 200 when the membership exists, 409 when the enrollment is no longer `ENROLLED` or the offering has no LMS course, and 503 when it fails again. It is safe to repeat.
- When a course enrollment ends, its membership is made inactive through the same service, never deleted. The student access policy already refuses an enrollment that is not `ENROLLED`, so a failure there is only logged.
- Academic enrollment stays an admin responsibility. Faculty LMS management only derives LMS memberships from it and never creates, changes, or ends an enrollment.

## Roster

`GET /api/admin/academic/offerings/{courseOfferingId}/enrollments` answers the offering and every course enrollment of exactly that offering, whatever its status, by student login ID, then ID. Each entry has `courseEnrollmentId`, `semesterEnrollmentId`, `studentProfileId`, `studentLoginId`, and `status`, and nothing about the account. A missing offering answers 404. It needs the enrollment view permission, not the offering one.

## Offering and enrollment permissions

`V36` adds `ACADEMIC_OFFERING_VIEW`, `ACADEMIC_OFFERING_MANAGE`, `ACADEMIC_ENROLLMENT_VIEW`, and `ACADEMIC_ENROLLMENT_MANAGE`, all for `ADMIN` only. Every rule also needs the `ADMIN` role, and changing needs the view permission as well, as for the masters. Admins who were signed in before `V36` sign in again to get them.

| Route | Needs |
| --- | --- |
| `GET /api/admin/academic/offerings`, `GET .../offerings/{courseOfferingId}` | `ADMIN`, `ACADEMIC_OFFERING_VIEW` |
| `POST /api/admin/academic/offerings`, `PUT .../offerings/{courseOfferingId}` | `ADMIN`, `ACADEMIC_OFFERING_VIEW`, `ACADEMIC_OFFERING_MANAGE` |
| `GET .../offerings/{courseOfferingId}/enrollments` and the `GET` enrollment routes | `ADMIN`, `ACADEMIC_ENROLLMENT_VIEW` |
| The `POST` and `PUT` enrollment routes and `provision-lms` | `ADMIN`, `ACADEMIC_ENROLLMENT_VIEW`, `ACADEMIC_ENROLLMENT_MANAGE` |

The master permissions give no access to offerings or enrollments, and the reverse. No route accepts an account, student, or enrollment ID in a body to say whose record changes: the route names it, and the acting admin comes from the access token.

## Frontend

`/admin/academic` has one sub-navigation for Departments, Programs, Academic Sessions, Semesters, Courses, Course Offerings (`/admin/academic/offerings`), and Enrollments (`/admin/academic/enrollments`). An offering's roster is at `/admin/academic/offerings/:courseOfferingId/enrollments`. See `docs/frontend-layout.md`. The admin dashboard on `/` shows read-only counts of these records and links here (see `admin-dashboard.md`).

## Not built yet

- Teaching assignments, student and faculty profiles, and accounts (later phases). LMS courses are created by faculty, not here.
- Bulk or CSV enrollment, select-all actions, timetables, fees, and admissions.
- Deletion and change history.
