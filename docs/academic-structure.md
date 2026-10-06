# Academic Structure

The academic (ERP) side of Stacc is the official source of truth for the college's academic structure and, later, for enrollment. Its code lives in the `academic` module.

The structure is being built one model at a time:

```text
Department
    |
    +------< Program
                |
                +------< Semester >------ AcademicSession
```

`Department`, `Program`, `AcademicSession`, and `Semester` exist so far. `Course` and `CourseOffering` are not built yet.

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

## Keeping history

A department or program that is no longer used is made `INACTIVE`, and a finished session or semester is `CLOSED`. None of them is deleted. Older courses, enrollments, and student records may still refer to them, so academic history is kept.

## Next

`Course` will be permanent academic data, such as a subject with its code and credits. `CourseOffering` will be one course offered in one specific semester. They are separate models and neither exists yet.

## Not built yet

- There is no API, service, or screen for any of these models. Only the stored models exist.
- No department, program, session, or semester rows are seeded. The college's real data will be entered through a later administrative feature.
- No permissions for this data exist yet. They will be added together with the API they protect.
