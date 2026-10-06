# Academic Structure

The academic (ERP) side of Stacc is the official source of truth for the college's academic structure and, later, for enrollment. Its code lives in the `academic` module.

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

## Enrollment and the LMS, later

A course offering is the official academic offering in the ERP. A later phase will add official enrollment of students in offerings, and that ERP enrollment will decide who gets access to the matching LMS course. Neither enrollment nor the LMS link exists yet.

## Keeping history

A department, program, or course that is no longer used is made `INACTIVE`, and a finished session, semester, or offering is `CLOSED`. None of them is deleted. Older enrollments and student records may still refer to them, so academic history is kept.

## Not built yet

- There is no API, service, or screen for any of these models. Only the stored models exist.
- No rows are seeded for any of them. The college's real data will be entered through a later administrative feature.
- No permissions for this data exist yet. They will be added together with the API they protect.
