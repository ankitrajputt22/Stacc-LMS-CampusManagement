# Course Outcomes and Bloom Levels

Course learning outcomes (CO1, CO2, …) and Bloom levels describe what a course teaches and what an assessment asks for (v0.7.1). They live in `lms.outcome`; the question bank (0.7.2) and quizzes will use them too.

## Outcomes

- `LmsCourseOutcome` (`lms_course_outcomes`, `V83`) belongs to one LMS course: a number and code (`CO` plus the number) given by the server, a plain-text description up to 500 characters, and `ACTIVE` or `ARCHIVED`.
- A new outcome takes the next number of its course, after every earlier one, archived ones included: codes are never reused. `LmsOutcomeService.add` locks the course row and then reads the outcomes with a locking read, so two outcomes added at once get different codes (`LmsOutcomeMySqlIT`).
- Only the description can be corrected. Archiving keeps the outcome on whatever it was aligned to but stops new alignments and hides it from students; nothing restores or deletes it.

## Bloom levels

`BloomLevel` is the revised taxonomy, lowest first: Remember, Understand, Apply, Analyze, Evaluate, Create. It is optional, describes an assessment, and decides nothing by itself.

## Assignment alignment

An assignment can be aligned with outcomes of its own course and an optional Bloom level (`lms_assignments.bloom_level`, `lms_assignment_outcomes`). Newly added outcomes must be active outcomes of the same course; one already aligned may stay after it is archived; anything else answers 409. Alignment has its own route, so the assignment form and its API are unchanged, and it is never copied by course copy (assignments are not copied).

## API

| Route | Needs | Does |
| --- | --- | --- |
| `GET /api/v1/lms/courses/{lmsCourseId}/outcomes` | `STUDENT` + `LMS_COURSE_VIEW`, student access policy | Active outcomes, by code. |
| `GET /api/v1/lms/faculty/courses/{lmsCourseId}/outcomes` | `FACULTY` + `FACULTY_COURSE_VIEW`, faculty access policy | Every outcome, archived ones included. |
| `POST /api/v1/lms/faculty/courses/{lmsCourseId}/outcomes` | also `LMS_CONTENT_MANAGE` | Adds the next outcome (`{ "description": … }`). |
| `PUT /api/v1/lms/faculty/outcomes/{outcomeId}` | also `LMS_CONTENT_MANAGE` | Corrects the description; 409 when archived. The course is found from the stored outcome. |
| `POST /api/v1/lms/faculty/outcomes/{outcomeId}/archive` | also `LMS_CONTENT_MANAGE` | Archives it. |
| `PUT /api/v1/lms/faculty/assignments/{assignmentId}/alignment` | `FACULTY` + `FACULTY_COURSE_VIEW` + `LMS_ASSIGNMENT_MANAGE` | `{ "bloomLevel": … or null, "outcomeIds": [ … ] }`. |

Faculty and student assignment answers now carry `bloomLevel` and `outcomes`.

## Pages

- Faculty: **Outcomes** in the course navigation (`/teaching/:lmsCourseId/outcomes`): Add an outcome, Edit, and Archive (confirmed first). On **Assignments**, each assignment shows its alignment ("Outcomes CO1 · Bloom level Apply") and **Edit alignment** with the outcomes as checkboxes and the Bloom level.
- Students: the **Syllabus** page lists the course outcomes; an assignment's page shows its outcomes and Bloom level.

Not in this phase: program outcomes, outcome attainment calculations, or outcome reports.
