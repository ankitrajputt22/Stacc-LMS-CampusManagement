# Results

An official course result is the academic record of how one student did in one course offering. Faculty who teach the offering record it, publish it, and can correct it; the student reads their own published results. The code is in `com.stacc.backend.academic.result` (the model, repository, and `CourseResultManagementService`) and `com.stacc.backend.academic.result.api` (controllers, DTOs, query services). The table is `course_results` (`V33`), and the permissions are added by `V34`.

```text
CourseOffering ------< CourseEnrollment ------ 0..1 CourseResult >------ FacultyProfile   (who recorded it last)
```

## Why the course enrollment

A course enrollment already means this student, through this semester enrollment, in this exact course offering, so it is the result's identity. The student, account, course, offering, semester, program, and academic session are reached through it and are not stored again. A unique constraint on `course_enrollment_id` keeps one result per enrollment; saving again changes the same row.

## Separate from the LMS

- An official result is not an LMS assignment grade. `LmsSubmissionGrade` belongs to one assignment attempt; a course has many of them. A `CourseResult` belongs to one course enrollment. Neither is ever calculated from, or synchronized with, the other, and attendance plays no part either.
- Results need no LMS course. A course offering with a teaching assignment and enrollments is enough.

## Fields

| Field | Rule |
| --- | --- |
| `marksObtained` | `BigDecimal`, `DECIMAL(8,2)`. Required, zero or more, at most `maxMarks`, at most two decimal places. |
| `maxMarks` | `BigDecimal`, `DECIMAL(8,2)`. Required and greater than zero. It is the official maximum for the course; nothing assumes 100. |
| `gradeCode` | Optional, up to 20 characters. Kept exactly as entered apart from spaces: surrounding spaces are removed and runs of spaces inside become one, so "B+" stays "B+". Blank means none. |
| `remarks` | Optional plain text, up to 2,000 characters, shown as text with its line breaks, never as HTML. Blank means none. |
| `status` | `DRAFT` or `PUBLISHED`, stored as the word. |
| `recordedByFacultyProfile` | The faculty profile that last saved the result. Never sent to clients. |
| `publishedAt` | Set by the server on the first publication, then never changed. |

There is no grading scale: the grade code is never derived from the marks, and no percentage, grade point average (GPA, SGPA, CGPA), rank, division, or pass or fail is calculated or stored. Database checks keep `0 <= marks_obtained <= max_marks`, `max_marks > 0`, the status words, and `published_at` present exactly when the result is published.

## Lifecycle

| Status | Meaning |
| --- | --- |
| `DRAFT` | Being recorded. The student does not see it. Every new result starts here. |
| `PUBLISHED` | The student sees it. It stays published. |

- **Saving** creates the draft, edits the draft, or corrects a published result. A correction stays `PUBLISHED`, keeps its first `publishedAt`, and records the correcting faculty member; the student sees it on their next load. No history of earlier values is kept, and there is no approval workflow yet; both can be added later.
- **Publishing** a draft sets `publishedAt` from the server's clock. Publishing again changes nothing. Publishing when nothing has been saved answers 404 and creates nothing.
- There is no unpublish, no archive, and no delete. Results are kept as academic history.

## Enrollments

- A **new** result can be recorded for an `ENROLLED` or `COMPLETED` enrollment. A `WITHDRAWN` enrollment without a result answers 409.
- An **existing** result is never removed when the enrollment changes later. It stays on the roster and can still be corrected or published, and a published one stays visible to the student.

## Who may do what

- **Faculty** routes need `hasRole('FACULTY') and hasAuthority('ACADEMIC_RESULT_MANAGE')`, then `FacultyTeachingAccessService`: an `ACTIVE` faculty profile with an `ACTIVE` teaching assignment for the course offering in the route. No LMS course or `FACULTY_COURSE_VIEW` is needed. The enrollment in the route must belong to that offering; an enrollment ID alone never gives access. An offering the caller does not teach, a missing or foreign enrollment, all answer the same 404.
- **Students** use `hasRole('STUDENT') and hasAuthority('ACADEMIC_RESULT_VIEW')`, and see only their own published results, reached through their own course enrollments from the account in the access token. No ID from the client plays a part. A result stays visible after its enrollment is completed or withdrawn.
- `ADMIN` holds neither permission and has no bypass.

| Route | What it does |
| --- | --- |
| `GET /api/results/faculty/my-courses` | The course offerings the faculty member teaches now: `courseOfferingId`, `courseCode`, `courseName`, `credits`, `semesterNumber`, `programCode`, `academicSessionCode`, in course code order. |
| `GET /api/results/faculty/course-offerings/{courseOfferingId}` | The offering's details and every official enrollment, withdrawn ones included, in student ID order: `courseEnrollmentId`, `studentLoginId`, `courseEnrollmentStatus`, and `result` (or null). |
| `PUT /api/results/faculty/course-offerings/{courseOfferingId}/enrollments/{courseEnrollmentId}` | Saves `{"marksObtained", "maxMarks", "gradeCode", "remarks"}` as described above. `200` with `resultId`, the four values, `status`, `publishedAt`, and `updatedAt`. |
| `POST /api/results/faculty/course-offerings/{courseOfferingId}/enrollments/{courseEnrollmentId}/publish` | Publishes the saved result. |
| `GET /api/results/student/me` | `{"results": [...]}`: the student's published results with the course code and name, credits, semester number, program, academic session, the marks, grade code, remarks, and `publishedAt`. No enrollment, offering, faculty, or account IDs. |

- The student's results come newest academic session first (by its start date), then the highest semester number, then course code.
- Invalid values answer 400: missing marks, negative marks, a maximum that is not above zero, more than two decimal places, marks above the maximum ("Marks obtained cannot exceed maximum marks."), or a grade or remarks that are too long. Nothing is saved.
- Saving locks the enrollment row and reads the result with a lock, so two saves of the same student take turns and the result is created once; the unique constraint is the last safety net.
- The reads are side-effect free. Publishing a result, and a real correction of a published one, tell the student in their Inbox (`RESULT_PUBLISHED`, `RESULT_UPDATED`, see `notifications-inbox.md`). Drafts, repeated publishing, and saves that change nothing tell nobody.

## Not built yet

- Grading scales, grade points, GPA, SGPA, CGPA, ranks, divisions, pass or fail rules, and semester or overall totals.
- Approval workflows, correction requests, revision history, and unpublishing.
- Admin result management, reports, transcripts, exports, and email notifications.
