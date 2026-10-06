# Academic Structure

The academic (ERP) side of Stacc is the official source of truth for the college's academic structure and, later, for enrollment. Its code lives in the `academic` module.

The structure is being built one model at a time:

```text
Department
    |
    v
Program                    (not built yet)
    |
    v
later academic structure   (not built yet)
```

Only `Department` exists so far.

## Department

A department is an official academic department of the college. It is the first piece of academic master data. The code is in `com.stacc.backend.academic.department` and the table is `departments`.

- `code` is the short department code. It is required, unique, at most 20 characters, and stored in uppercase without surrounding spaces, so `cse` becomes `CSE`.
- `name` is the full name. It is required, unique, and at most 150 characters.
- `status` is `ACTIVE` or `INACTIVE`. New departments are `ACTIVE`.

A department that is no longer used is made `INACTIVE`, not deleted. Older programs, courses, and student records may still refer to it, so academic history is kept.

## Not built yet

- There is no Department API, service, or screen. Only the stored model exists.
- No department rows are seeded. The college's real departments will be entered through a later administrative feature.
- No permissions for departments exist yet. They will be added together with the API they protect.
