# Gradebook

Each LMS course has one gradebook (v0.7.6, `lms.gradebook`): the marks of every current student for the course's published assignments, published graded quizzes, and manual items, and each student's own Grades page.

## Columns

| Column | Marks come from | Maximum |
| --- | --- | --- |
| Assignment (published) | The grade of the student's latest attempt. A latest attempt without a grade needs grading; an earlier attempt's grade does not count. | The assignment's maximum marks. |
| Quiz (published, graded) | The best score among the student's fully marked attempts. A handed-in attempt waiting for a teacher needs grading. Practice quizzes are left out. | The quiz's total marks. |
| Manual item | Marks entered in the gradebook, such as a class test on paper. | The item's own maximum. |

Columns come in that order: assignments by due date, then quizzes and manual items in the order they were made. Assignment and quiz marks are read from their own records on every request (`GradebookBuilder`); nothing is copied or synchronized, so a regrade shows at once. A cell is `GRADED`, `NEEDS_GRADING`, or `NO_MARKS`.

Rows are the course offering's current students: an `ENROLLED` course and semester enrollment and an `ACTIVE` student profile, by college ID. A student's total is the sum of their graded cells over the sum of those columns' maxima ("graded work only"). There are no weights, percentages, averages, or ranks.

## Stored data (`V88`)

- `LmsGradeItem` (`lms_grade_items`): a manual item (title up to 200 characters, maximum marks above 0 with two decimals), or, for an assignment or quiz column, a record made the first time its grades are published (unique per assignment and per quiz). `publishedAt` is set once.
- `LmsGradeEntry` (`lms_grade_entries`): one student's marks for a manual item, through their course enrollment (unique per item and enrollment), 0 to the item's maximum, with optional plain-text feedback up to 10,000 characters; the server sets who entered it and when. Entering again replaces it without history.

Nothing is deleted. A manual item can be renamed, and its maximum changed but never below marks already entered (409).

## Publishing

Faculty publish one column at a time; students see a column, and their own marks in it, on their Grades page only once it is published. Publishing again changes nothing, and nothing unpublishes. Publishing locks the course row and then reads the course's grade items with a locking read, so two publications of one column make one record; entering marks locks the manual item and reads the entry with a locking read (`LmsGradebookMySqlIT`).

Publishing in the gradebook does not hide marks elsewhere: assignment grades still show on the assignment page as soon as they are saved, and quiz results follow the quiz's own result settings. On a student's Grades page a quiz counts only attempts whose result is released.

## API

Faculty routes need `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_GRADEBOOK_MANAGE` and the faculty access policy; for a manual item the course comes from the stored item.

| Route | Does |
| --- | --- |
| `GET /api/v1/lms/faculty/courses/{lmsCourseId}/gradebook` | The columns and every current student's cells and total. |
| `POST /api/v1/lms/faculty/courses/{lmsCourseId}/gradebook/items` | `{ "title", "maxMarks" }`: adds a manual item. |
| `PUT /api/v1/lms/faculty/gradebook/items/{itemId}` | Renames it or changes its maximum. |
| `POST /api/v1/lms/faculty/courses/{lmsCourseId}/gradebook/publish` | `{ "type": "ASSIGNMENT", "QUIZ", or "MANUAL", "sourceId" }`. |
| `PUT /api/v1/lms/faculty/gradebook/items/{itemId}/entries/{courseEnrollmentId}` | `{ "marks", "feedback" }` for a current student. |

`GET /api/v1/lms/courses/{lmsCourseId}/grades` needs `STUDENT`, `LMS_COURSE_VIEW`, and `LMS_GRADE_VIEW`, takes the student from the access token and the enrollment from the student access policy, and returns only published columns with that student's marks and total.

## Pages

- **Gradebook** in the faculty course navigation (`/teaching/:lmsCourseId/gradebook`): the table (search by college ID; a stacked list on phones), **Columns** with each column's state, a link to where its marks are given (assignment grading, quiz results, or the manual item), and **Publish grades** (confirmed first), and **Add a manual item**.
- `/teaching/:lmsCourseId/gradebook/items/:itemId`: a manual item's title and maximum (Save item) and each current student's marks and feedback (Save).
- **Grades** in the student course navigation (`/courses/:lmsCourseId/grades`): Assessment, Type, Score, Maximum, and Feedback for published columns, with the current total over graded work.

## Demo data

In CS301 the "Array warm-up exercises" column is published, and the published manual item "Class test 1" (25 marks) gives `student1` 21.

Not in this phase: weights and categories, percentages, letter grades, dropping lowest scores, exporting the gradebook, grade history, or a spreadsheet-style editor.
