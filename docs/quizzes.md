# Quizzes

Faculty build quizzes from their course's question bank (v0.7.3). A quiz holds its settings and an ordered list of bank questions, each with the marks it carries in that quiz. It lives in `lms.quiz`. Students take published quizzes from 0.7.4; marking comes in 0.7.5.

## Quiz

`LmsQuiz` (`lms_quizzes`, `V85`) belongs to one LMS course and keeps:

| Setting | Rule |
| --- | --- |
| Title | Required, up to 200 characters. |
| Instructions | Optional, up to 20,000 characters, `PLAIN` or `FORMATTED` (see `text-formatting.md`). |
| Type | `GRADED`, or `PRACTICE` (only questions offered for practice). |
| Time limit | Optional whole minutes, 1 to 600, counted from the start of an attempt. Empty means none. |
| Opens, due, closes | Optional moments (`availableFrom`, `dueAt`, `availableUntil`), stored in UTC and sent as ISO 8601 with a zone. Opens is before closes; the due date lies between them. Empty means open on publication, no due date, and never closing. |
| Attempts | Optional, 1 to 100 per student. Empty means unlimited. |
| Shuffling | Shuffle questions and shuffle choices, each attempt in its own order. |
| Negative marking | 0 to 100 percent (two decimals) of a question's marks, taken off for a wrong objective answer. Unanswered questions lose nothing. |

A quiz uses `LmsContentStatus`: a new quiz is a `DRAFT`; publishing records `publishedAt` once; archiving keeps it for good. There is no unpublish, restore, or delete.

## Questions

`LmsQuizQuestion` (`lms_quiz_questions`) links one bank question to the quiz at a position, with marks (`DECIMAL(8,2)`, above 0; the bank's marks unless faculty choose others). A bank question is in a quiz at most once (unique constraint) but may be in several quizzes. Nothing is copied: the quiz asks the bank question.

- Questions are added (only active questions of the same course), re-marked, moved, and removed only while the quiz is a draft (409 otherwise). Removing takes the question out of the quiz only; it stays in the bank. A quiz holds at most 100 questions.
- The total marks are the sum of the question marks, worked out on every read and never stored.

## Publishing

Publishing needs at least one question, and every question active and reviewed; a practice quiz also needs every question offered for practice. A refusal names the question by its number.

After publishing:

- the questions, their marks and order, the type, shuffling, and negative marking stay as they are (409);
- the title, instructions, availability, time limit, and attempt limit can still be corrected;
- a question the quiz uses can no longer be edited in the bank (409): the quiz keeps asking exactly what it asked. It can still be archived, reviewed, or offered for practice. The bank shows "In a published quiz" and no Edit. An archived quiz keeps this rule, because it is kept for history.

A bank edit and a publication can race. The edit reads the statuses of the quizzes that use the question with a shared lock (`FOR SHARE`) before it locks the question; publishing locks the quiz, then reads its questions with a shared lock. Either the edit finishes first, and publishing finds the question needing review again, or publishing finishes first, and the edit finds the quiz published (`LmsQuizMySqlIT`).

## API

Every route needs `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_QUIZ_MANAGE` (`V85`) and the faculty access policy for the course; for an existing quiz the course comes from the stored quiz, and every change locks the quiz row first. A course or quiz the caller may not use is the same 404 as a missing one. No admin pass.

| Route | Does |
| --- | --- |
| `GET /api/v1/lms/faculty/courses/{lmsCourseId}/quizzes` | Every quiz with its question count and total marks, archived ones included, newest first. |
| `POST /api/v1/lms/faculty/courses/{lmsCourseId}/quizzes` | Adds a draft quiz without questions. |
| `GET /api/v1/lms/faculty/quizzes/{quizId}` | The quiz with its settings and questions in order. |
| `PUT /api/v1/lms/faculty/quizzes/{quizId}` | Changes the settings. |
| `POST /api/v1/lms/faculty/quizzes/{quizId}/questions` | `{ "questionId", "marks"? }`: adds a bank question at the end. |
| `PUT /api/v1/lms/faculty/quizzes/{quizId}/questions/{quizQuestionId}` | `{ "marks" }`. |
| `DELETE /api/v1/lms/faculty/quizzes/{quizId}/questions/{quizQuestionId}` | Takes the question out of a draft quiz. |
| `PUT /api/v1/lms/faculty/quizzes/{quizId}/question-order` | `{ "quizQuestionIds": [...] }`, every question of the quiz once. |
| `POST /api/v1/lms/faculty/quizzes/{quizId}/publish` | Publishes a draft; again changes nothing. |
| `POST /api/v1/lms/faculty/quizzes/{quizId}/archive` | Archives it; again changes nothing. |

The faculty quiz answer lists each question with its quiz ID, position, marks, and what the bank says (type, text, topic, difficulty, bank marks, review and practice state, status), but not its answers: those stay on the Question bank page. Students have no route here.

## Pages

**Quizzes** in the faculty course navigation:

- `/teaching/:lmsCourseId/quizzes`: **New quiz** opens the settings form and, after Create quiz, the new quiz's page. The list shows each quiz with its type, status, size and rules ("5 questions · 10 marks · 20 minutes · 1 attempt"), and times.
- `/teaching/:lmsCourseId/quizzes/:quizId`: the quiz with **Publish quiz** and **Archive quiz** (each confirmed first; archiving returns to the list), **Settings** (Edit settings; fields fixed by publishing are shown but disabled), **Questions** (in a draft: Marks with Save marks, Move up, Move down, and Remove), and in a draft **Add from the question bank** (search and type filter over the active questions not yet in the quiz, with their review and practice badges, and Add).

Times are entered and shown in the browser's own time zone.

## Demo data

`faculty1`'s CS301 has four reviewed questions (true/false, single choice, multiple choice, descriptive) and the published graded quiz "Stacks and queues check" (15 minutes, due in a week, 2 attempts, shuffled choices, 25% negative marking).

Not in this phase: taking quizzes, timers and answers (0.7.4), marking and results (0.7.5), the gradebook (0.7.6), quizzes as module items, question pools or random selection, and notifications.
