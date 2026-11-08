# Assignment submission rules

Faculty decide when an assignment takes work (v0.7.8): when submissions open, whether late work is accepted and until when, and how many attempts each student may make. The rules live on `LmsAssignment` (`V90`) and are set through their own route, like the alignment and the rubric.

| Rule | Column | Meaning |
| --- | --- | --- |
| Opens | `available_from` | No attempt before it; empty means from publication. Not after the due date. |
| Late work | `allow_late_submissions` | When false, no attempt after the due date. When true (the default), late attempts are taken and marked late. |
| Closes | `available_until` | With late work allowed, no attempt after it; empty means no final closing time. Not before the due date. |
| Attempts | `max_attempts` | 1 to 100 per student; empty means unlimited. |

Existing assignments keep today's behaviour: always open, late work accepted, unlimited attempts. The assignment and its instructions are visible to students from publication either way; the rules decide only when work is taken.

## Rules on the server

- `LmsAssignment.submissionWindowAt(now)` answers `NOT_OPEN`, `OPEN`, or `CLOSED` from the server's clock.
- Handing in checks the window before any file is stored, then again with the attempt's own time once the course enrollment row and the submission row are locked (409 otherwise; files already stored are removed again).
- The attempt limit is counted under those same locks, so several hand-ins at once never pass it (`SubmissionAttemptLimitMySqlIT`). Attempts already handed in always stay; lowering the limit only stops new ones.
- Lateness is still worked out per attempt from the due date it was handed in under (`dueAtSnapshot`).
- The due date can change only within the opening and closing times (409); change those first. An archived assignment's rules cannot change.

## API

`PUT /api/v1/lms/faculty/assignments/{assignmentId}/submission-rules` takes `{ "availableFrom", "availableUntil", "allowLateSubmissions", "maxAttempts" }` (times as ISO 8601 moments with a zone; zone-less times are refused). It needs `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_ASSIGNMENT_MANAGE` and the faculty access policy, finding the course from the stored assignment. Bad times or limits answer 400.

The faculty assignment list carries the four rules; the student assignment page also carries `submissionWindow` at the moment it was read.

## Pages

- **Assignments** (faculty): each assignment sums up its rules ("Open from publication · no late work · 1 attempt") with **Submission rules**: Opens, Attempts allowed, Accept late work, and Late work accepted until, in the browser's own time zone.
- **Assignment page** (students): Opens, Late work, and Attempts rows; when no attempt can be made, the submission area says why (not open yet, no longer takes work, or every attempt used) instead of the form.

Not in this phase: per-student extensions, late penalties, availability for viewing the assignment itself, and submission types (text only, files only).
