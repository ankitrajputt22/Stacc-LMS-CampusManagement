# Academic Structure

The academic (ERP) side of Stacc is the official source of truth for the college's academic structure and, later, for enrollment. Its code lives in the `academic` module.

The structure is being built one model at a time:

```text
Department
    |
    | 1
    |
    +------< Program
                |
                v
        later academic structure   (not built yet)
```

`Department` and `Program` exist so far.

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

## Keeping history

A department or program that is no longer used is made `INACTIVE`, not deleted. Older courses, enrollments, and student records may still refer to it, so academic history is kept.

## Not built yet

- There is no API, service, or screen for departments or programs. Only the stored models exist.
- No department or program rows are seeded. The college's real data will be entered through a later administrative feature.
- No permissions for this data exist yet. They will be added together with the API they protect.
- Academic sessions, semesters, courses, and enrollment will build on these models in later phases.
