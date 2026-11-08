# Rubrics

Faculty describe how an assignment is marked with a rubric (v0.7.7), and grade with it: points per criterion, a comment each, and the marks as their sum. Rubric definitions live in `lms.assignment.rubric`; the scores a grade was given with live with the grade in `lms.assignment.submission`.

## Rubrics (`V89`)

`LmsRubric` (`lms_rubrics`) belongs to one LMS course: a title (up to 200 characters), an optional plain-text description, and 1 to 20 criteria in order (`lms_rubric_criteria`). A criterion has a title (unique in the rubric, ignoring case), an optional description, maximum points above 0 (two decimals), and 0 to 6 levels (`lms_rubric_levels`): a title, an optional description, and points from 0 to the criterion's maximum. The rubric's total is the sum of the criteria's maximum points, worked out and never stored.

- Editing replaces the title, description, and criteria together. Once a rubric has been used for grading it keeps its criteria (409); make a new rubric instead.
- Archiving keeps the rubric, and assignments that use it keep it, but it can no longer change or be chosen. Nothing deletes or restores a rubric.
- An edit locks the rubric row and then checks, with a locking read, whether a grade has used it; grading reads the rubric with a shared lock. So an edit and a grade never cross (`LmsRubricMySqlIT`).

## Assignments

An assignment may use one active rubric of its own course whose total equals the assignment's maximum marks (`lms_assignments.lms_rubric_id`; 409 otherwise). While it has one, its maximum marks cannot change to anything else (409); remove the rubric first. Choosing or removing a rubric never changes grades already given.

## Grading

`PUT /api/v1/lms/faculty/submission-attempts/{attemptId}/rubric-grade` takes points for every criterion exactly once (0 to its maximum, two decimals), an optional comment each (up to 2,000 characters), and optional feedback. It locks the attempt row, reads the rubric with a shared lock, and gives the attempt the sum as its marks, through the same rules as plain grading (first grade out of the maximum then, regrades keep that maximum, notifications as before). The scores are kept as an `LmsRubricAssessment` with each criterion's title and maximum as they were (`lms_rubric_assessments`, `lms_rubric_scores`). Grading again replaces them; grading with plain marks removes them, so a grade's marks always match its scores.

Grades are sent with `rubric`: the criterion, points, maximum, and comment per criterion (empty without a rubric), to students and faculty alike. Students also see the assignment's rubric on its page before handing in.

## API

Rubric routes need `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_ASSIGNMENT_MANAGE` and the faculty access policy; for an existing rubric the course comes from the stored rubric. Rubric grading needs `LMS_ASSIGNMENT_GRADE`, like plain grading. No new permission.

| Route | Does |
| --- | --- |
| `GET /api/v1/lms/faculty/courses/{lmsCourseId}/rubrics` | Every rubric with its criteria and levels, archived ones included, newest first, and whether it has been used for grading. |
| `POST /api/v1/lms/faculty/courses/{lmsCourseId}/rubrics` | Adds a rubric. |
| `PUT /api/v1/lms/faculty/rubrics/{rubricId}` | Edits it while unused. |
| `POST /api/v1/lms/faculty/rubrics/{rubricId}/archive` | Archives it. |
| `PUT /api/v1/lms/faculty/assignments/{assignmentId}/rubric` | `{ "rubricId" }`, or null for none. |
| `PUT /api/v1/lms/faculty/submission-attempts/{attemptId}/rubric-grade` | `{ "scores": [{ "criterionId", "points", "comment" }], "feedback" }`. |

The faculty assignment list, the faculty submission detail, and the student assignment page carry the assignment's `rubric`.

## Pages

- **Rubrics** in the faculty course navigation (`/teaching/:lmsCourseId/rubrics`): New rubric (criteria with Add level, Remove level, Add criterion, Remove criterion, and the total as points are typed), each rubric's criteria and levels, Edit while unused, and Archive, confirmed first.
- **Assignments**: each assignment shows its rubric and **Change rubric** (only active rubrics whose total equals its maximum can be chosen).
- **Grading**: with a rubric, an attempt is graded criterion by criterion (a level's button fills in its points), with the total shown; **Grade without the rubric** switches to plain marks.
- **Students**: the assignment page shows the rubric, and each graded attempt shows its points and comments per criterion.

## Demo data

The CS301 warm-up assignment uses "Array exercises rubric": Correct answers (12, with three levels) and Clear working (8, with two levels).

Not in this phase: rubric templates shared across courses, copying rubrics, rubrics for quizzes or manual gradebook items, peer or self assessment, and weighted criteria.
