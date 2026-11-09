# Quizzes

Faculty build quizzes from their course's question bank (v0.7.3). A quiz holds its settings and an ordered list of bank questions, each with the marks it carries in that quiz. It lives in `lms.quiz`. Students take published quizzes (v0.7.4, `lms.quiz.attempt`), and attempts are marked automatically and by hand (v0.7.5).

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
| Results | `AFTER_MARKING` (default) or `AFTER_DUE_DATE`, and whether results show the correct answers and explanations (`showAnswers`). Both can change after publishing. |

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

The faculty quiz answer lists each question with its quiz ID, position, marks, and what the bank says (type, text, topic, difficulty, bank marks, review and practice state, status), but not its answers: those stay on the Question bank page. Students use their own routes (below), which never send answers.

## Pages

**Quizzes** in the faculty course navigation:

- `/teaching/:lmsCourseId/quizzes`: **New quiz** opens the settings form and, after Create quiz, the new quiz's page. The list shows each quiz with its type, status, size and rules ("5 questions · 10 marks · 20 minutes · 1 attempt"), and times.
- `/teaching/:lmsCourseId/quizzes/:quizId`: the quiz with **Publish quiz** and **Archive quiz** (each confirmed first; archiving returns to the list), **Settings** (Edit settings; fields fixed by publishing are shown but disabled), **Questions** (in a draft: Marks with Save marks, Move up, Move down, and Remove), and in a draft **Add from the question bank** (search and type filter over the active questions not yet in the quiz, with their review and practice badges, and Add).

Times are entered and shown in the browser's own time zone.

## Taking a quiz (v0.7.4)

`LmsQuizAttempt` (`lms_quiz_attempts`, `V86`) is one student's attempt at a published quiz, through their official `CourseEnrollment` (never an account or profile). It keeps its number (1, 2, …), `startedAt`, and two values fixed at the start: `deadlineAt`, the earlier of the time limit from the start and the quiz's closing time (none when neither is set), and `dueAtSnapshot`. Later changes to the quiz never move an attempt's deadline. Stored statuses are `IN_PROGRESS`, `SUBMITTED`, and `EXPIRED`; an open attempt whose deadline has passed is shown as `TIME_UP`, worked out on every read and never stored. `late` compares the hand-in time with the due date snapshot.

Answers (`LmsQuizAnswer`, `lms_quiz_answers`, one per attempt and quiz question) keep the chosen options by their position in the question (`lms_quiz_answer_options`), a true or false answer, or plain text (up to 500 characters for a short answer, 20,000 for a descriptive one). Saving again replaces the answer; an empty answer leaves the question unanswered. Parts a question's type does not take, unknown positions, and two choices for a single-choice question answer 400.

Rules:

- **Who:** the student comes from the access token; the enrollment is found with `LmsStudentAccessService.findAccessGrantingEnrollment`. An attempt made through another enrollment, of a quiz that is no longer published, or in a course the student can no longer use, is the same 404 as a missing one.
- **Starting** needs the quiz open (after `availableFrom`, before `availableUntil`) and an attempt left (409 otherwise). It locks the course enrollment (still `ENROLLED`) and then the enrollment's attempts at the quiz with a locking read: an attempt still open is returned instead (Resume), one left open past its deadline is closed first, and the next number follows the highest. A unique key on the number and a unique generated column allowing one `IN_PROGRESS` attempt per quiz and enrollment back this up (`LmsQuizAttemptMySqlIT`).
- **Saving** locks the attempt row and takes the answer only while the attempt is in progress and the server's clock is at most 15 seconds past the deadline (for answers already on their way); after that it answers 409.
- **Handing in** locks the attempt row: before the deadline (and its grace) it is `SUBMITTED` at that moment; after it, `EXPIRED` at the deadline. Handing in again changes nothing. An answer racing a hand-in is either saved before it or refused.
- **Reading never changes data**, so an attempt left open past its deadline stays stored as in progress (shown as time up) until it is handed in or the next attempt starts.
- **Order:** with shuffling on, each attempt shows questions, and choices, in its own order, worked out from the attempt's ID with `java.util.Random` (`QuizAttemptOrder`), the same on every read; nothing is stored.
- **Nothing gives answers away:** an attempt sends each question's text, type, marks, and, for single and multiple choice only, the choices as `position` and `text`. Never which choices are correct, accepted short answers, the true or false answer, guidance, or explanations.
- The first publication of a quiz notifies every student who may use the course now (`QUIZ_PUBLISHED`, linking to the quiz page).

Student routes need `STUDENT`, `LMS_COURSE_VIEW`, and `LMS_QUIZ_TAKE` (`V86`):

| Route | Does |
| --- | --- |
| `GET /api/v1/lms/courses/{lmsCourseId}/quizzes` | The published quizzes, newest first, each with availability, attempts left, the open attempt, whether it can start, and the student's attempts. |
| `GET /api/v1/lms/courses/{lmsCourseId}/quizzes/{quizId}` | One of them, with its instructions. |
| `POST /api/v1/lms/courses/{lmsCourseId}/quizzes/{quizId}/attempts` | Starts an attempt, or returns the open one, with its questions. |
| `GET /api/v1/lms/quiz-attempts/{attemptId}` | One of the student's attempts, with `serverTime` for the timer. |
| `PUT /api/v1/lms/quiz-attempts/{attemptId}/answers/{quizQuestionId}` | `{ "selectedOptions", "trueFalseAnswer", "text" }`. |
| `POST /api/v1/lms/quiz-attempts/{attemptId}/submit` | Hands the attempt in. |

Student pages, under **Quizzes** in the course navigation:

- `/courses/:lmsCourseId/quizzes`: each published quiz with its type, size, time limit, due date, attempts used, and whether it can start now.
- `/courses/:lmsCourseId/quizzes/:quizId`: the instructions and rules, **Start quiz** (confirmed first, saying the time keeps running) or **Resume quiz**, and **Your attempts**.
- `/courses/:lmsCourseId/quizzes/:quizId/attempts/:attemptId`: the questions with their inputs. Choices are saved at once and writing after a short pause or on leaving the field, one save at a time per question, each showing Saving…, Saved, or Not saved. The timer counts down to the server's deadline, allowing for the device's clock, and warns at five minutes and one minute. When it runs out the saved answers are handed in. **Submit quiz** is confirmed first and says how many questions are answered. A handed-in attempt shows its answers read-only, without marks until 0.7.5.

## Marking and results (v0.7.5)

Marks are worked out on every read by `QuizScoring` from the answers, the bank questions, and teachers' marks; no score is stored.

- **Automatic:** a single or multiple choice answer is correct only when it chooses exactly the correct options; true/false when it matches; a short answer when it equals an accepted answer, ignoring case, surrounding spaces, and repeated spaces. Correct earns the question's marks in the quiz; wrong loses the quiz's negative-marking share of them (rounded to two decimals); unanswered earns nothing.
- **By hand:** a descriptive answer needs a teacher's marks; an unanswered one scores zero and needs none.
- **Teacher's marks** (`LmsQuizAnswerMark`, `lms_quiz_answer_marks`, `V87`, one per answer): 0 to the question's marks with two decimals, optional plain-text feedback up to 10,000 characters, and the marker and time from the server. For an objective answer they replace the automatic marks (a regrade). Marking again replaces them without history; only answered questions of handed-in attempts of published quizzes are marked, under the attempt row lock (`LmsQuizMarkingMySqlIT`).
- **Score:** the sum of every question's marks, never below zero, once no answer waits for a teacher.
- **Release:** a student sees an attempt's result once it is handed in and fully marked: at once (`AFTER_MARKING`), or from the due date, or the closing time without one (`AFTER_DUE_DATE`). Before that the attempt says why (`resultStatus`: `NOT_HANDED_IN`, `WAITING_FOR_MARKING`, `WAITING_FOR_RELEASE`, `RELEASED`). A released result has the score, counts of correct, incorrect, and unanswered questions, and each question's outcome, marks, and feedback; the correct options, accepted answers, true or false answer, and explanation only with `showAnswers`. Marking guidance is never sent to students.
- **Closing:** an attempt a student left open after its time ran out can be closed by a teacher, as handing it in would, so it can be marked. There is no scheduler.

Faculty routes need `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_QUIZ_GRADE` (`V87`) and the faculty access policy on the course of the stored quiz or attempt; results can be read for published and archived quizzes, and marks given for published ones:

| Route | Does |
| --- | --- |
| `GET /api/v1/lms/faculty/quizzes/{quizId}/attempts` | Every attempt by student college ID with automatic marks, teachers' marks, the score once complete, and how many answers need marking. |
| `GET /api/v1/lms/faculty/quiz-attempts/{attemptId}` | One attempt: each question with its answers, the student's answer, the outcome, and the marks. |
| `PUT /api/v1/lms/faculty/quiz-attempts/{attemptId}/answers/{quizQuestionId}/mark` | `{ "marks", "feedback" }`. |
| `POST /api/v1/lms/faculty/quiz-attempts/{attemptId}/close` | Closes an attempt whose time is up. |

Pages: **Results** on a published or archived quiz opens `/teaching/:lmsCourseId/quizzes/:quizId/results` (attempts, Needs marking, Automatic, Teacher, Score), and each attempt opens `/teaching/:lmsCourseId/quiz-attempts/:attemptId` with the answers, the key, and Marks and Feedback per answered question (Save marks or Update marks), plus Close attempt when the time is up. Students see their score in **Your attempts** and on the attempt page, with each question's outcome, marks, feedback, and, if shown, the answers.

## Demo data

`faculty1`'s CS301 has four reviewed questions (true/false, single choice, multiple choice, descriptive) and the published graded quiz "Stacks and queues check" (15 minutes, due in a week, 2 attempts, shuffled choices, 25% negative marking).

`student1` and `student2` can take it (two attempts each).

Not in this phase: the gradebook (0.7.6), partial credit for multiple choice, AI marking, mark notifications, quizzes as module items, question pools or random selection, closing open attempts with a scheduler, extra time for individual students, and proctoring.
