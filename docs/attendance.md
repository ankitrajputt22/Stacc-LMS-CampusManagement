# Attendance

Attendance is official academic data: faculty record who attended each class of a course offering they teach, and students read their own finalized attendance. The code is in `com.stacc.backend.academic.attendance` (models, repositories, `AttendanceManagementService`) and `com.stacc.backend.academic.attendance.api` (controllers, DTOs, query services). The tables are `attendance_sessions` and `attendance_records` (`V31`), and the permissions are added by `V32`.

```text
FacultyProfile ------< TeachingAssignment >------ CourseOffering
                                                       |
                                                       +------< AttendanceSession   (created by a FacultyProfile)
                                                                     |
                                                                     +------< AttendanceRecord >------ CourseEnrollment
```

## Why the course offering

A permanent `Course` does not say which semester's class it is, and an `LmsCourse` is only the LMS learning space and may not exist. The official teaching context is the `CourseOffering`, so a session belongs to one course offering. Attendance never refers to an LMS course or membership, and it works whether or not the offering has an LMS course.

## Sessions

`AttendanceSession` is one class meeting: the course offering, the faculty profile that created it, `heldAt` (an `Instant` in UTC, sent as ISO 8601 with a zone; a time without a zone is refused, and the year must be 2000 to 9999), an optional plain-text `topic` (up to 500 characters), its status, and `finalizedAt`. Nothing about the faculty member is copied onto it.

| Status | Meaning |
| --- | --- |
| `OPEN` | Being taken. Students never see it. New sessions start here. |
| `FINALIZED` | Every student is marked. Students see their own record. |

- Finalizing sets `finalizedAt` from the server's clock. Finalizing again changes nothing and keeps the first time.
- Only an open session's date and topic can change; a finalized one answers 409.
- There is no reopen and no delete. Sessions and records are kept for history; the database has no cascade.

## Records and the roster snapshot

`AttendanceRecord` is one student's attendance in one session, through their official `CourseEnrollment`, with a unique constraint on `(attendance_session_id, course_enrollment_id)`. No student, account, or course is stored again.

| Status | Meaning |
| --- | --- |
| `UNMARKED` | Not marked yet. Every record starts here; it never means absent. |
| `PRESENT` | Attended. |
| `ABSENT` | Did not attend. |

- **The roster is a snapshot.** Creating a session creates, in the same transaction, one `UNMARKED` record for every course enrollment of the offering that is `ENROLLED` at that moment. A failure leaves neither the session nor a partial roster.
- **History stays.** A student who withdraws or completes later keeps their records. A student who enrolls later appears only in sessions created after that. A faculty member can never add or remove students: the roster comes only from course enrollments.
- When a record changes to `PRESENT` or `ABSENT`, the acting faculty profile and the server's time are stored as `markedByFacultyProfile` and `markedAt`; a later change by another teacher of the offering replaces both. Saving the same status again changes nothing. Neither is ever taken from the client or sent to students.
- Database checks keep statuses to the listed words and keep the marker and time present exactly when a record is marked.

## Taking attendance

- Saves are sent by `attendanceRecordId`, the ID the server gave each roster row. A student, profile, enrollment, or account ID is never accepted. Every record must belong to that session and appear once; anything else answers 400 and saves nothing.
- An open session takes `UNMARKED`, `PRESENT`, or `ABSENT`, so attendance can be saved part of the way.
- Finalizing needs every record `PRESENT` or `ABSENT`; otherwise it answers 400 "All students must be marked before finalizing attendance."
- A finalized session can still be corrected between `PRESENT` and `ABSENT`; going back to `UNMARKED` answers 400. `finalizedAt` never changes, and students see a correction at once.
- Every change locks the session row first (`SELECT ... FOR UPDATE`), so a save and a finalization of the same session never overlap.

## Who may do what

- **Faculty** routes need `hasRole('FACULTY') and hasAuthority('ATTENDANCE_MANAGE')`, then `FacultyTeachingAccessService` (`academic.teaching`): the account's faculty profile is `ACTIVE` and has an `ACTIVE` teaching assignment for the course offering. No LMS course is needed or read. For a session, the offering is found from the stored session, never from the request. Anything else, including a guessed session ID, answers the same 404 as a missing one.
- **Students** use `hasRole('STUDENT') and hasAuthority('ATTENDANCE_VIEW')`, and see only finalized records reached through their own course enrollments, starting from the account in the access token. No ID from the client plays a part.
- `ADMIN` holds neither permission and has no bypass. Students cannot mark their own attendance.

| Route | What it does |
| --- | --- |
| `GET /api/attendance/faculty/my-courses` | The course offerings the faculty member teaches now: `courseOfferingId`, `courseCode`, `courseName`, `credits`, `semesterNumber`, `programCode`, `academicSessionCode`, in course code order. |
| `GET /api/attendance/faculty/course-offerings/{courseOfferingId}/sessions` | The sessions, latest class first: `id`, `heldAt`, `topic`, `status`, `finalizedAt`. |
| `POST /api/attendance/faculty/course-offerings/{courseOfferingId}/sessions` | Creates an open session from `{"heldAt", "topic"}` and snapshots the roster. `201`. |
| `GET /api/attendance/faculty/sessions/{sessionId}` | The session with its roster in student ID order: `attendanceRecordId`, `studentLoginId`, `status`, `markedAt`. |
| `PUT /api/attendance/faculty/sessions/{sessionId}` | Changes an open session's `heldAt` and `topic`. |
| `PUT /api/attendance/faculty/sessions/{sessionId}/records` | Saves `{"records": [{"attendanceRecordId", "status"}]}` and answers with the whole session. |
| `POST /api/attendance/faculty/sessions/{sessionId}/finalize` | Finalizes the session. |
| `GET /api/attendance/student/me` | The student's own finalized attendance by course offering. |

## Student summaries

`GET /api/attendance/student/me` answers `{"courses": [...]}` with one entry per course offering that has at least one finalized session the student was on the roster of, in course code order. Each has the course code and name, semester number, program, and academic session, `totalSessions`, `presentCount`, `absentCount`, `attendancePercentage`, and `sessions` (latest class first, each with `attendanceSessionId`, `heldAt`, `topic`, and `PRESENT` or `ABSENT`).

- `attendancePercentage` is `presentCount / totalSessions * 100`, calculated with `BigDecimal` and rounded half up to two decimal places on every request, such as `80.00` or `83.33`. It is never stored.
- A course without finalized attendance is simply not listed, so no percentage is ever shown for attendance that was never taken. With no courses the answer is `{"courses": []}`.
- The percentage is informational. There is no minimum, shortage, warning, or eligibility rule.

## Not built yet

- Reopening or deleting sessions, and admin attendance management or reports.
- Other statuses such as late or excused, attendance rules or thresholds, and notifications beyond corrections. Only a real correction of a finalized record tells its student in their Inbox (`ATTENDANCE_UPDATED`, see `notifications-inbox.md`); marking an open session and finalizing tell nobody.
- Self check-in, QR codes, location, or biometrics. Attendance is taken by faculty only.
- Importing or exporting attendance.
