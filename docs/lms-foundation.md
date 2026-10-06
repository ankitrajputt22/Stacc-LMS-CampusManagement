# LMS Foundation

The LMS side of Stacc is where teaching and learning happen. It is built on the official academic (ERP) data and never replaces it. Its code lives in the `lms` module.

```text
Course          = permanent academic catalog data                  (ERP)
CourseOffering  = one course officially offered in one semester    (ERP)
LmsCourse       = the LMS learning space for one course offering   (LMS)
```

```text
ERP:   Course ------< CourseOffering >------ Semester
                            |
                            v
LMS:                    LmsCourse
```

## LmsCourse

An LMS course is the learning space for one official course offering. The code is in `com.stacc.backend.lms.course` and the table is `lms_courses`.

- An LMS course belongs to exactly one course offering, and the offering cannot be changed after creation.
- A course offering can have at most one LMS course. It may also have none: no LMS course is created automatically.
- The course offering stays ERP data in the `academic` module. It knows nothing about the LMS, and no LMS state is stored on it.
- The course, semester, program, academic session, and department are reached through the course offering. None of them is stored on the LMS course again, and neither are the course's code, name, or credits.
- An LMS course has no title of its own, no teacher, no students, and no content yet.

### Status

| Status | Meaning |
| --- | --- |
| `DRAFT` | The learning space exists but is not published. New LMS courses start here. |
| `PUBLISHED` | The learning space is available for normal LMS use, subject to the access rules. |
| `ARCHIVED` | The learning space is kept for history but is no longer an active teaching space. |

An archived LMS course cannot be published again.

The status of an LMS course is separate from the status of its course offering. The model does not require the offering, course, or semester to be active, because planned and historical learning spaces are both legitimate. The future feature that creates and publishes LMS courses decides when that is allowed.

An LMS course is archived, never deleted, because assignments, submissions, and grades will later depend on it. Deleting an LMS course never deletes its course offering, and an offering cannot be deleted while an LMS course refers to it.

## Student access, later

`PUBLISHED` does not by itself let any student in. The intended rule is:

```text
valid ERP CourseEnrollment  +  published LmsCourse   ->   the student may be given LMS access
```

The official ERP course enrollment stays the source of truth for who takes a course. **None of this is implemented yet.** There is no LMS membership, nothing copies enrollments into the LMS, and nothing grants access today.

## Not built yet

- No API, service, or screen for LMS courses. Only the stored model exists.
- No LMS course rows are seeded, and none is created automatically for existing course offerings.
- No student membership, no teacher assignment, and no content such as modules, assignments, or announcements.
- No permissions for LMS courses. They will be added together with the API they protect.
