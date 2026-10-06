# LMS Foundation

The LMS side of Stacc is where teaching and learning happen. It is built on the official academic (ERP) data and never replaces it. Its code lives in the `lms` module.

```text
Course                = permanent academic catalog data                      (ERP)
CourseOffering        = one course officially offered in one semester        (ERP)
CourseEnrollment      = a student's official place in a course offering      (ERP)
LmsCourse             = the LMS learning space for one course offering       (LMS)
LmsStudentMembership  = the LMS-side membership for one course enrollment    (LMS)
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
- An LMS course has no title of its own, no teacher, and no content yet. Its students are found through memberships, described below.

### Status

| Status | Meaning |
| --- | --- |
| `DRAFT` | The learning space exists but is not published. New LMS courses start here. |
| `PUBLISHED` | The learning space is available for normal LMS use, subject to the access rules. |
| `ARCHIVED` | The learning space is kept for history but is no longer an active teaching space. |

An archived LMS course cannot be published again.

The status of an LMS course is separate from the status of its course offering. The model does not require the offering, course, or semester to be active, because planned and historical learning spaces are both legitimate. The future feature that creates and publishes LMS courses decides when that is allowed.

An LMS course is archived, never deleted, because assignments, submissions, and grades will later depend on it. Deleting an LMS course never deletes its course offering, and an offering cannot be deleted while an LMS course refers to it.

## LmsStudentMembership

An LMS student membership is the LMS-side record that one student belongs to one LMS course. The code is in `com.stacc.backend.lms.membership` and the table is `lms_student_memberships`.

```text
CourseEnrollment          (ERP, official)
       |
       v
LmsStudentMembership      (LMS)
       |
       v
    LmsCourse             (LMS)
```

- The course enrollment stays the official ERP record of who takes a course. A membership is derived from it and never replaces it.
- A membership belongs to one course enrollment and one LMS course. Neither can be changed after creation.
- One course enrollment can have at most one membership. One LMS course can have many memberships, one for each student.
- The student profile and the account are reached through the course enrollment. The course offering is reached from both sides, and the course, semester, program, and session through it. None of them is stored on the membership again.
- This model is for students only. Teacher access will come from an official teaching assignment, not from a course enrollment, and will have its own design.
- `status` is `ACTIVE` or `INACTIVE`. New memberships are `ACTIVE`. An inactive membership is kept for history and can be made active again. Memberships are not deleted.

**Matching rule:** the course enrollment and the LMS course must belong to the same course offering. The provisioning service below checks this before it creates a membership, so memberships are created through that service. The stored model does not check it itself, the course offering is not stored a third time to force it, and there is no database trigger for it.

Deleting a membership never deletes the course enrollment or the LMS course, and neither can be deleted while a membership refers to it.

## Membership provisioning

`LmsStudentMembershipProvisioningService` in `com.stacc.backend.lms.membership` creates and switches off memberships, one official course enrollment at a time.

```text
CourseEnrollment
       |
       v
LmsStudentMembershipProvisioningService
       |
       v
LmsStudentMembership
       |
       v
    LmsCourse
```

It is an internal service. No API calls it yet, and nothing runs it automatically.

### `provision(courseEnrollmentId)`

Gives the student of a course enrollment an active membership in the LMS course of the same course offering, and returns it.

| Situation | Result |
| --- | --- |
| The course enrollment does not exist | Fails with `Course enrollment not found.` (404). |
| The course enrollment is `COMPLETED` or `WITHDRAWN` | Fails (409). Only an `ENROLLED` course enrollment can be provisioned. |
| The course offering has no LMS course | Fails with `This course offering has no LMS course yet.` (409). No LMS course is created. |
| No membership exists yet | An `ACTIVE` membership is created. |
| An `ACTIVE` membership exists | It is returned unchanged. |
| An `INACTIVE` membership exists | It is made `ACTIVE` again. |

- The LMS course is found through the course enrollment's course offering. Its ID is never assumed to be the offering's ID.
- The matching rule is checked by comparing course offering IDs, both for the LMS course that was found and for the LMS course of an existing membership. A mismatch means the stored data is inconsistent: the service stops with an error and changes nothing. It never moves a membership to another LMS course.
- Provisioning is safe to repeat. Calling it again for the same course enrollment never creates a second membership. The unique `course_enrollment_id` in the database is the final guard if two calls arrive at the same moment.
- The LMS course does not have to be `PUBLISHED`. Memberships can be prepared while it is still a `DRAFT`. No rule for archived LMS courses has been decided.

### `deactivate(courseEnrollmentId)`

Makes the membership of a course enrollment `INACTIVE`. If the enrollment has no membership, or the membership is already inactive, nothing happens and no error is raised. A membership is never created just to be made inactive.

### What the service does not do

- It never changes ERP data. The course enrollment, semester enrollment, course offering, and student profile are only read. Deactivating a membership does not withdraw the enrollment: that is the academic side's decision.
- It does not grant access. An active membership is one input to the access rule below, not the answer.
- It does not run automatically. Nothing provisions a membership when a course enrollment is created, nothing deactivates one when an enrollment is withdrawn, and there is no bulk synchronization.
- It does not decide what a student may still see after a course enrollment is `COMPLETED`. Such an enrollment cannot be newly provisioned, and an existing membership is left as it is. That policy belongs to a later phase.

The expected failures in the table use the shared `ApiException`, so a future API can return them in the common error format.

## Student access, later

Neither a `PUBLISHED` LMS course nor an `ACTIVE` membership lets a student in by itself. The intended rule is:

```text
valid ERP CourseEnrollment  +  ACTIVE LmsStudentMembership  +  PUBLISHED LmsCourse   ->   the student may be given access
```

**This is not implemented yet.** Memberships can be provisioned, but only by an explicit call to the service. Nothing keeps them in step when an enrollment is withdrawn, and nothing answers whether a student may open a course.

## Not built yet

- No API or screen for LMS courses or memberships. The stored models and the provisioning service exist, and nothing calls the service yet.
- No rows are seeded for either, and none is created automatically from existing course offerings or course enrollments.
- No automatic synchronization from the ERP to the LMS, and no service that creates LMS courses.
- No teacher assignment, and no content such as modules, assignments, or announcements.
- No permissions for LMS data. They will be added together with the API they protect.
