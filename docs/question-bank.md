# Question Bank

Each LMS course has a question bank (v0.7.2): reusable questions with their answers, written and kept by the faculty who teach the course. Quizzes draw on it (see `quizzes.md`), and exam papers will. It lives in `lms.question`.

## Questions

`LmsQuestion` (`lms_questions`, `V84`) belongs to one LMS course and keeps:

- the type: `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `TRUE_FALSE`, `SHORT_ANSWER`, or `DESCRIPTIVE`;
- the question text and an optional explanation, both in one text format (`PLAIN` or `FORMATTED`, see `text-formatting.md`);
- the answers, as its type needs them (below);
- an optional plain-text topic (up to 100 characters), a difficulty (`EASY`, `MEDIUM`, `HARD`), and marks (`DECIMAL(8,2)`, above 0);
- an optional Bloom level and an optional outcome of the same course (see `course-outcomes.md`). They describe the question and decide nothing;
- a review status (`NEEDS_REVIEW` or `REVIEWED`), whether it is OK for practice, and `ACTIVE` or `ARCHIVED`;
- the account that wrote it, set from the access token.

| Type | Answers |
| --- | --- |
| Single choice | 2 to 10 options, exactly one marked correct. |
| Multiple choice | 2 to 10 options, at least one correct. |
| True or false | The correct answer; no options. |
| Short answer | 1 to 10 accepted answers, all correct. |
| Descriptive | Optional plain-text guidance for whoever marks it; no options. |

Options and accepted answers are plain text up to 500 characters, must differ from each other (ignoring case), and are kept in `lms_question_options` as part of their question: an edit replaces them together. A question of another type that sends options, a true/false answer, or guidance it does not take answers 400.

## Rules

- A new question needs review and is not offered for practice. **Mark reviewed** records the review; only a reviewed question can be offered for practice (409 otherwise).
- Editing replaces everything written about the question, the type included, and sets it back to needing review and not for practice.
- Every change locks the question row first, so an edit and a review or practice change never overwrite each other (`LmsQuestionBankMySqlIT`). Access is checked from the stored question's course before the lock.
- An outcome must be an active outcome of the same course; one the question already has may stay after it is archived. Anything else answers 409.
- Archiving is final: the question stays in the bank, readable under Archived, but can no longer change or be offered for practice. Nothing deletes or restores a question.
- A question that a published or archived quiz uses can no longer be edited (409), so the quiz keeps asking what it asked; it can still be reviewed, offered for practice, or archived. Answers carry `inPublishedQuiz`, and the page shows "In a published quiz" without Edit. See `quizzes.md` for how an edit and a publication never miss each other.
- A question in a final exam paper can no longer be edited either (409); see `exam-papers.md`.
- Students have no route to the bank: questions carry their answers. Quizzes send students only what they may see.

## API

Every route needs `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_QUESTION_BANK_MANAGE` (`V84`) and the faculty access policy for the course; for an existing question the course comes from the stored question. A course or question the caller may not use is the same 404 as a missing one. No admin pass.

| Route | Does |
| --- | --- |
| `GET /api/v1/lms/faculty/courses/{lmsCourseId}/questions` | Every question with its answers, archived ones included, newest first. |
| `POST /api/v1/lms/faculty/courses/{lmsCourseId}/questions` | Adds a question. |
| `PUT /api/v1/lms/faculty/questions/{questionId}` | Edits it; 409 when archived or in a published quiz. |
| `POST /api/v1/lms/faculty/questions/{questionId}/review` | Marks it reviewed. |
| `PUT /api/v1/lms/faculty/questions/{questionId}/practice` | `{ "allowed": true or false }`. |
| `POST /api/v1/lms/faculty/questions/{questionId}/archive` | Archives it. |

A question is sent as `type`, `text`, `textFormat`, `explanation`, `options` (`text`, `correct`), `trueFalseAnswer`, `answerGuidance`, `topic`, `difficulty`, `marks`, `bloomLevel`, and, in requests, `outcomeId` (answers carry the `outcome` with its code).

## Page

**Question bank** in the faculty course navigation (`/teaching/:lmsCourseId/questions`):

- **Add a question**: the type decides which parts the form shows (options with Correct marks, the true or false answer, accepted answers, or guidance), then topic, difficulty, marks, course outcome, and Bloom level. Obvious slips are caught before sending; the backend decides.
- **Questions**: search the question text and filter by type, topic, difficulty, and status (Active by default), on the page over what the API sent. Each question shows its answers, facts, and badges (Needs review, Reviewed, OK for practice, Archived), with Edit, Mark reviewed, OK for practice or Not for practice, and Archive (confirmed first).

Not in this phase: "Used in" counts, question pools, import or export, or AI-generated questions (v0.14).
