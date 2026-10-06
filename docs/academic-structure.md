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

**Rule for the future enrollment feature:** before creating an enrollment, it must check that the student's program is the same as the semester's program. The stored model does not check this itself, and there is no database trigger for it.

Deleting an enrollment never deletes the student profile or the semester, and neither can be deleted while an enrollment refers to it.

## CourseEnrollment

A course enrollment is the official ERP record that a student takes part in one course offering. The code is in `com.stacc.backend.academic.enrollment` and the table is `course_enrollments`.

- A course enrollment belongs to one semester enrollment and one course offering. They cannot be changed after creation, and the same pair can exist only once.
- One semester enrollment can have many course enrollments, and one course offering can have many students.
- The student profile and the account are reached through the semester enrollment. The course is reached through the course offering. The semester, program, session, and department are reached through those in turn. None of them is stored on the course enrollment again.
- Course enrollment is always explicit. A student is never enrolled automatically in every offering of a semester, because of electives, optional courses, and backlogs.
- `status` is `ENROLLED`, `COMPLETED`, or `WITHDRAWN`. New course enrollments are `ENROLLED`. Once completed or withdrawn, a course enrollment cannot change again.
- The status describes the enrollment only. It is not a grade or a result, and `COMPLETED` does not mean passed. Marks, grades, attendance, and results belong to later models.

**Rule for the future course-enrollment feature:** before creating a course enrollment, it must check that the course offering belongs to the same semester as the semester enrollment. The stored model does not check this itself, the semester is not stored a second time to force it, and there is no database trigger for it.

Deleting a course enrollment never deletes the semester enrollment or the course offering, and neither can be deleted while a course enrollment refers to it.

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

The ERP course enrollment is the source for LMS membership provisioning. An explicit LMS service creates a membership from a course enrollment that is still `ENROLLED`. It only reads ERP data and never changes it.

Future access to an LMS course should be granted from the official ERP course enrollment, not merely from a student profile or a semester enrollment. A withdrawn course enrollment should later remove or disable that access, as the LMS integration decides. Nothing creates memberships automatically, and nothing grants LMS access today.

## Keeping history

A department, program, course, or student profile that is no longer used is made `INACTIVE`, a finished session, semester, or offering is `CLOSED`, and an enrollment ends as `COMPLETED` or `WITHDRAWN`. None of them is deleted. Later records may still refer to them, so academic history is kept.

## Not built yet

- There is no API, service, or screen for any of these models. Only the stored models exist.
- No rows are seeded for any of them. The college's real data will be entered through a later administrative feature.
- No permissions for this data exist yet. They will be added together with the API they protect.
