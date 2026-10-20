# Academic Structure

The academic (ERP) side of Stacc is the official source of truth for the college's academic structure and for enrollment. Its code lives in the `academic` module.

The structure is being built one model at a time:

```text
Department
    |
    +------< Program
    |           |
    |           +------< Semester >------ AcademicSession
    |                       |
    |                       +------< CourseOffering
    |                                     |
    +------< Course >---------------------+
```

`Department`, `Program`, `AcademicSession`, `Semester`, `Course`, and `CourseOffering` exist so far. Read `A ------< B` as "one A has many B".

`Department`, `Program`, `AcademicSession`, `Semester`, and `Course` are the reusable academic masters; admins manage them (see `admin-academic.md`). A `CourseOffering` is separate: it is semester-specific delivery data, one course taught in one semester, and is not part of the masters. Admins also create offerings and the official enrollments described below.

```text
Course        = permanent catalog data
CourseOffering = that course in one semester
StudentProfile ---> SemesterEnrollment ---> CourseEnrollment ---> CourseOffering
```

Students are linked to this structure through their profile and their enrollments:

```text
UserAccount
     |
     v
StudentProfile
     |
     +------> Program ------> Department
     |
     +------> admission AcademicSession
     |
     +------< SemesterEnrollment >------ Semester
                     |
                     +------< CourseEnrollment >------ CourseOffering ------> Course
```

Faculty members are linked through their own profile, and to what they teach through teaching assignments:

```text
UserAccount
     |
     v
FacultyProfile ------> primary Department
     |
     +------< TeachingAssignment >------ CourseOffering ------> Course, Semester
```

## Department

A department is an official academic department of the college. The code is in `com.stacc.backend.academic.department` and the table is `departments`.

- `code` is the short department code. It is required, unique, at most 20 characters, and stored in uppercase without surrounding spaces, so `cse` becomes `CSE`.
- `name` is the full name. It is required, unique, and at most 150 characters.
- `status` is `ACTIVE` or `INACTIVE`. New departments are `ACTIVE`.

## Program

A program is an official academic programme offered by a department. The code is in `com.stacc.backend.academic.program` and the table is `programs`.

- A program belongs to exactly one department, and a department may offer several programs.
- `code` is required, at most 30 characters, and stored in uppercase without surrounding spaces.
- `name` is required and at most 150 characters.
- `code` and `name` are unique within their department, not across the college. Two departments can both offer a program with the same code.
- `durationSemesters` is the normal length of the program in semesters, from 1 to 20. Length is stored only in semesters, never in years as well.
- `status` is `ACTIVE` or `INACTIVE`. New programs are `ACTIVE`.

Deleting a program never deletes its department, and a department that still has programs cannot be deleted.

## AcademicSession

An academic session is an official academic period for the whole college, such as `2026-27`. The code is in `com.stacc.backend.academic.session` and the table is `academic_sessions`.

- A session is college-wide. It does not belong to a department or a program, and neither of them points to a session. One session is used by many departments and programs.
- `code` is the college's own name for the session. It is required, unique, at most 20 characters, and stored as given (trimmed, with any letters in uppercase). It is never worked out from the dates.
- `startDate` and `endDate` are calendar dates. The session must end after it starts. It does not have to be exactly one year long.
- `status` is `PLANNED`, `ACTIVE`, or `CLOSED`. New sessions are `PLANNED`. A closed session cannot be made active again.

Nothing limits the college to one active session yet. That rule belongs to the future feature that manages sessions.

## Semester

A semester is one numbered semester of one program during one academic session, for example semester 3 of a program in `2026-27`. The code is in `com.stacc.backend.academic.semester` and the table is `semesters`.

- A semester belongs to one program and to one academic session. It is the place where the two meet. Program and session stay independent: neither refers to the other directly.
- `semesterNumber` says which semester of the program it is. It is at least 1, at most 20, and no greater than the program's `durationSemesters` when the semester is created.
- The same program, session, and number can exist only once. The number alone is not unique: many programs and many sessions have a semester 1.
- Odd and even semesters are not tied to a particular session. The record simply states the official number.
- The program, session, and number cannot be changed after creation, because together they say what the semester is.
- `status` is `PLANNED`, `ACTIVE`, or `CLOSED`. New semesters are `PLANNED`. A closed semester cannot be made active again. Several semesters of one program may be active at once, for different student cohorts.

Deleting a semester never deletes its program or session, and neither can be deleted while a semester refers to it.

## Course

A course is a permanent entry in the college's course catalog, such as a subject with its code and credits. The code is in `com.stacc.backend.academic.course` and the table is `courses`.

- A course has one owning department, which is responsible for its definition. That does not limit which programs can later be taught the course.
- `code` is required, at most 30 characters, stored in uppercase without surrounding spaces, and unique across the whole college. No particular code format is assumed.
- `name` is required and at most 200 characters. It does not have to be unique: the code is the official identifier.
- `credits` is the official credit value. It is stored as an exact decimal with two places, so fractional values such as `1.50` are supported. It must be greater than zero and at most `20.00`. A value with more than two decimal places is rejected, never rounded.
- `status` is `ACTIVE` or `INACTIVE`. New courses are `ACTIVE`.
- A course does not belong to a program, a semester, or an academic session, and it has no teacher, schedule, or learning material. Those belong to later models.

Deleting a course never deletes its department, and a department that still has courses cannot be deleted.

## CourseOffering

A course offering is one course offered in one semester. The code is in `com.stacc.backend.academic.offering` and the table is `course_offerings`.

```text
Course          = the permanent definition
CourseOffering  = one course offered in one semester
```

The two are separate on purpose. Everything that depends on a particular semester belongs to the offering, not to the course.

- An offering belongs to exactly one course and exactly one semester. They cannot be changed after creation, because together they say what the offering is.
- The same course can be offered in many semesters, and a semester can have many offerings, but one course can appear only once in a given semester.
- The program and the academic session are reached through the semester. The owning department is reached through the course. None of them is stored on the offering again, so they can never disagree.
- The course's code, name, and credits are not copied onto the offering either.
- `status` is `PLANNED`, `ACTIVE`, or `CLOSED`. New offerings are `PLANNED`. A closed offering cannot be made active again.
- An offering has no teacher, section, timetable, or learning material yet. Those are separate concepts for later phases. Sections must not be modelled by duplicating offerings.
- Admins create offerings and move them through their status (see `admin-academic.md`). The course and semester are never reassigned, even before anything depends on the offering, and there is no delete.

Deleting an offering never deletes its course or semester, and neither can be deleted while an offering refers to it.

## StudentProfile

A student profile is the academic side of a student's account. It lives in the `identity` module, in `com.stacc.backend.identity.student`, and refers to academic data from the `academic` module. The table is `student_profiles`.

- A profile belongs to exactly one `UserAccount`, and an account can have at most one student profile.
- The student's ID is the account's `loginId`. It is not stored again on the profile, and neither is the student's name or any other personal detail.
- A profile belongs to one program. The department is reached through the program and is not stored on the profile, so the two can never disagree.
- `admissionSession` is the academic session in which the student originally joined the program. It is history. It is not the student's current session and must never be used as one.
- The profile holds no current semester. Which semester a student is in will come from enrollment records, which also keep the history.
- `status` is `ACTIVE` or `INACTIVE`. New profiles are `ACTIVE`.

A student profile is meant for an account with the `STUDENT` role. That rule is not checked by the model itself. The future feature that creates profiles must check it.

Deleting a profile never deletes the account, program, or session, and none of them can be deleted while a profile refers to them.

## SemesterEnrollment

A semester enrollment is the official ERP record that one student takes part in one semester. The code is in `com.stacc.backend.academic.enrollment` and the table is `semester_enrollments`.

- The student profile is the student's lasting academic identity. The semester enrollment says which semester the student is officially in.
- An enrollment belongs to one student profile and one semester. They cannot be changed after creation.
- A student has many enrollments over the years, one per semester, which keeps the academic history. The same student cannot be enrolled in the same semester twice.
- Repeating a semester needs no special field. Semester 3 in a later session is a different semester record, so it is simply another enrollment.
- The account, program, department, and academic session are reached through the student profile and the semester. None of them is stored on the enrollment again.
- The session of the enrollment comes from its semester. It is a different thing from the student's `admissionSession`.
- `status` is `ENROLLED`, `COMPLETED`, or `WITHDRAWN`. New enrollments are `ENROLLED`. Once completed or withdrawn, an enrollment cannot change again. These statuses say nothing about results: passing, failing, and promotion belong to later features.

Admins create semester enrollments and end them (see `admin-academic.md`). Before creating one, that feature checks that the student profile is `ACTIVE` and that the student's program is the semester's program; the stored model does not check this itself, and there is no database trigger for it. The unique `(student_profile_id, semester_id)` constraint refuses a second enrollment in the same semester. A semester enrollment ends only after each of its course enrollments has ended on its own: nothing cascades from one to the other.

Deleting an enrollment never deletes the student profile or the semester, and neither can be deleted while an enrollment refers to it.

## CourseEnrollment

A course enrollment is the official ERP record that a student takes part in one course offering. The code is in `com.stacc.backend.academic.enrollment` and the table is `course_enrollments`.

- A course enrollment belongs to one semester enrollment and one course offering. They cannot be changed after creation, and the same pair can exist only once.
- One semester enrollment can have many course enrollments, and one course offering can have many students.
- The student profile and the account are reached through the semester enrollment. The course is reached through the course offering. The semester, program, session, and department are reached through those in turn. None of them is stored on the course enrollment again.
- Course enrollment is always explicit. A student is never enrolled automatically in every offering of a semester, because of electives, optional courses, and backlogs.
- `status` is `ENROLLED`, `COMPLETED`, or `WITHDRAWN`. New course enrollments are `ENROLLED`. Once completed or withdrawn, a course enrollment cannot change again.
- The status describes the enrollment only. It is not a grade or a result, and `COMPLETED` does not mean passed. Marks, grades, attendance, and results belong to later models.

Admins create course enrollments and end them (see `admin-academic.md`). Before creating one, that feature checks that the semester enrollment is still `ENROLLED` and that the course offering belongs to the same semester as the semester enrollment, so a student is never enrolled across semesters. The stored model does not check this itself, the semester is not stored a second time to force it, and there is no database trigger for it. The unique `(semester_enrollment_id, course_offering_id)` constraint refuses a second enrollment in the same offering.

Deleting a course enrollment never deletes the semester enrollment or the course offering, and neither can be deleted while a course enrollment refers to it.

## FacultyProfile

A faculty profile is the academic side of a faculty member's account. Like the student profile, it lives in the `identity` module, in `com.stacc.backend.identity.faculty`, and refers to academic data from the `academic` module. The table is `faculty_profiles`.

- `UserAccount` stays the sign-in identity. The faculty profile is the faculty member's academic identity.
- A profile belongs to exactly one `UserAccount`, and an account can have at most one faculty profile. An account that really holds both roles may have a student profile and a faculty profile: each table allows one profile per account on its own.
- The employee ID is the account's `loginId`. It is not stored again on the profile, and neither is the person's name or any other personal detail.
- A profile has one primary `Department`. A faculty member has no program to reach a department through, so unlike a student's department it is stored on the profile.
- The primary department is where the faculty member belongs. It does not limit what they teach: they may teach a course owned by another department.
- The profile holds no courses. What a faculty member teaches comes from teaching assignments, described below.
- `status` is `ACTIVE` or `INACTIVE`. New profiles are `ACTIVE`.

A faculty profile is meant for an account with the `FACULTY` role. That rule is not checked by the model itself. The future feature that creates faculty profiles must check it.

Deleting a profile never deletes the account or the department, and neither can be deleted while a profile refers to it.

## TeachingAssignment

A teaching assignment is the official ERP record that a faculty member teaches one course offering. The faculty profile says who the faculty member is; the teaching assignment says what they actually teach. The code is in `com.stacc.backend.academic.teaching` and the table is `teaching_assignments`.

- An assignment belongs to one faculty profile and one course offering. Neither can be changed after creation.
- It targets the course offering, the course in one particular semester, never the permanent course. Who teaches a course can change from one semester to the next.
- One faculty member can teach many offerings, and one offering can have several faculty members. The same faculty member and offering can be paired only once.
- It is its own model, not a list of courses on the faculty profile or a teacher column on the offering, because it has its own status and history.
- The account and department are reached through the faculty profile, and the course, semester, program, and session through the course offering. None of them is stored on the assignment again.
- The faculty member's primary department does not have to own the course. Teaching across departments is allowed.
- There is no lead or co-teacher distinction yet. Every assignment counts the same.
- `status` is `ACTIVE` or `INACTIVE`. New assignments are `ACTIVE`. An assignment that has ended is made `INACTIVE` and kept for history; it can be made active again.

Faculty LMS access is decided from active teaching assignments (see `lms-foundation.md`). Academic features use `FacultyTeachingAccessService` in the same package instead, which needs no LMS course: the account's faculty profile is `ACTIVE` and has an `ACTIVE` teaching assignment for the course offering.

**Rule for the future assignment-management feature:** before creating an assignment or making one active again, it should check that the faculty profile is `ACTIVE`. The model itself does not check this. The course offering's status is not part of this rule: an assignment may be prepared before the offering starts.

Deleting an assignment never deletes the faculty profile or the course offering, and neither can be deleted while an assignment refers to it.

## Attendance

Attendance is academic data of a course offering, not LMS data (see `attendance.md`).

```text
CourseOffering ------< AttendanceSession ------< AttendanceRecord >------ CourseEnrollment
```

- A session is one class meeting of one course offering, never of a course or an LMS course, so attendance works whether or not an LMS course exists.
- Creating a session snapshots the offering's currently `ENROLLED` course enrollments as `UNMARKED` records. Later withdrawals keep their records, and later enrollments only join later sessions.
- Faculty who teach the offering mark students `PRESENT` or `ABSENT` and finalize the session once all are marked. Only finalized sessions are visible to students, and finalized records can still be corrected between `PRESENT` and `ABSENT`.
- Sessions are never reopened or deleted, and records are never deleted.

## Results

An official course result is academic data of a course enrollment, not LMS data (see `results.md`).

```text
CourseEnrollment ------ 0..1 CourseResult
```

- There is at most one official result per course enrollment, enforced by a unique constraint. The student, course, offering, semester, program, and session are reached through the enrollment and are not stored again.
- It is separate from LMS assignment grades: it is recorded explicitly by faculty and never calculated from assignment grades or attendance. It needs no LMS course.
- A result starts as a draft and is visible to the student once published; a published result can be corrected and stays published. A new result needs an enrolled or completed enrollment.
- Results are historical academic records: they are never deleted or unpublished, and a later change to the enrollment keeps them.

## Academic calendar

Official calendar events are academic data of an academic session, not LMS data (see `academic-calendar.md`).

```text
AcademicSession
      |
      +------< AcademicCalendarEvent
                      |
                      +------> optional Semester of the same session
```

- Every event belongs to one academic session. Without a semester it applies across the session; with one, only to that semester, which must belong to the same session.
- Events are plain dates with no time zone: a start date and an included end date, never before the start.
- An event starts as a draft, is published by an admin, and may be cancelled. A cancelled event that was published stays visible, marked as cancelled. Once published, its session and semester are fixed. Events are never deleted or restored.
- Students see the events of the semesters they are enrolled in or have completed, and faculty those of the semesters they actively teach in, with the session-wide events of those sessions. No LMS course is needed.
- Assignment deadlines are not calendar events: they stay on the LMS assignment and are only read alongside these events.

## The ERP is the source of truth for the LMS

```text
ERP SemesterEnrollment  = official participation in a semester
ERP CourseEnrollment    = official participation in a course offering
```

The LMS is built on this data, in the `lms` module (see `lms-foundation.md`). An `LmsCourse` is the learning space for one course offering, and an `LmsStudentMembership` is the LMS-side membership for one course enrollment.

```text
ERP:   UserAccount
            |
            v
       StudentProfile
            |
            v
       SemesterEnrollment
            |
            v
       CourseEnrollment
            |
            v
       CourseOffering ------> Course, Semester


LMS:   CourseEnrollment
            |
            v
       LmsStudentMembership
            |
            v
       LmsCourse
            |
            v
       CourseOffering
```

- The course offering and the course enrollment stay ERP academic data. The LMS records point to them, never the other way round.
- One course offering has at most one LMS course, and one course enrollment has at most one LMS membership.
- The student, course, program, semester, and academic session are not stored again on the LMS records.
- The ERP remains authoritative. An LMS membership is not an academic enrollment.

The ERP course enrollment is the source for LMS membership provisioning. An explicit LMS service creates a membership from a course enrollment that is still `ENROLLED`. It only reads ERP data and never changes it. Admin course enrollment calls it right after the official enrollment is saved, when the offering already has an LMS course, and makes the membership inactive when the enrollment ends. Academic enrollment succeeds without an LMS course and never creates one.

The LMS student access policy consults this official ERP state on every check. A student may use an LMS course only while their student profile is `ACTIVE` and both their semester enrollment and their course enrollment are `ENROLLED`, on top of the LMS-side conditions. A withdrawn or completed enrollment therefore ends normal access at once, without anything being copied. The student course list, `GET /api/lms/my-courses`, returns only courses that satisfy this policy. Nothing else creates memberships: there is no listener, scheduler, or bulk job.

## Keeping history

A department, program, course, student profile, faculty profile, or teaching assignment that is no longer used is made `INACTIVE`, a finished session, semester, or offering is `CLOSED`, and an enrollment ends as `COMPLETED` or `WITHDRAWN`. None of them is deleted, and neither are attendance sessions, attendance records, course results, or calendar events, which are cancelled instead. Later records may still refer to them, so academic history is kept.

## Not built yet

- Admins create and edit the five masters, create course offerings, create and end semester and course enrollments, and manage calendar events (see `admin-academic.md` and `academic-calendar.md`). Apart from that, attendance, and results, there is no API, service, or screen for these models yet; student and faculty profiles cannot be created through any API.
- Teaching assignments cannot be created or changed through any API yet.
- No rows are seeded for any of them. The college's real data will be entered through a later administrative feature.
- The permissions for this data are the academic master, offering, and enrollment permissions for admins, `ATTENDANCE_MANAGE`, `ATTENDANCE_VIEW`, `ACADEMIC_RESULT_MANAGE`, `ACADEMIC_RESULT_VIEW`, `ACADEMIC_CALENDAR_VIEW`, and `ACADEMIC_CALENDAR_MANAGE` (see `permissions.md`). Others will be added together with the API they protect.
