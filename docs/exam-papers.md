# Exam Blueprints and Papers

Faculty plan exam papers with blueprints and build them from their course's question bank, without AI (v0.7.9). A paper is printed from the browser or downloaded as a Word document, with or without an answer key. The code lives in `lms.paper`; the API in `LmsExamPaperController`.

## Model

- **`LmsExamBlueprint`** (`lms_exam_blueprints`) belongs to one LMS course: a title and 1 to 30 ordered rows (`lms_exam_blueprint_rows`), at most 200 questions in all. Each row has an optional section title, optional filters (question type, difficulty, Bloom level, an outcome of the course, a topic), how many questions to take (1 to 100), and the marks each carries (`DECIMAL(8,2)`, above 0). An empty filter matches every question. Rows are replaced together on edit; blueprints are `ACTIVE` or `ARCHIVED`, never deleted.
- **`LmsExamPaper`** (`lms_exam_papers`) belongs to one LMS course: a title, plain-text instructions (up to 5,000 characters), an optional duration (1 to 1,440 minutes), the blueprint it was built from (for the record only), and a status: `DRAFT`, `FINAL` (with `finalizedAt`, set once), or `ARCHIVED`. Never deleted, never reopened.
- **`LmsExamPaperQuestion`** (`lms_exam_paper_questions`) links a bank question to a paper at a position, with an optional section title and its marks in the paper. Each question is in a paper at most once. Nothing is copied from the bank: the paper always reads the question as it is.

## Rules

- Only active, reviewed questions of the paper's own course go in a paper, whether chosen by hand or from a blueprint.
- **Building from a blueprint** takes, for each row in order, that many matching questions chosen at random, never one already chosen for the paper. When the bank has too few for a row, nothing is saved and the 409 names the row. The paper starts as a draft and is edited like any other.
- **The builder** saves the whole question list at once (order, section, marks), and only while the paper is a draft. The details (title, instructions, duration) change only in a draft too.
- **Making final** needs at least one question, each still active and reviewed. A final paper keeps its details and questions, and the bank refuses edits to its questions (409), as it does for questions in published quizzes. Finalizing locks the course row, then the paper row, and reads the questions with a shared lock after reading only their IDs; a bank edit locks the course row first too, so the two never both succeed (`LmsExamPaperMySqlIT`).
- **Archiving** keeps a blueprint or paper for history; an archived blueprint builds no papers.

## Access

Every route needs `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_EXAM_PAPER_MANAGE` (`V102`), and the faculty access policy for the course, found from the stored blueprint or paper. A record of another course answers the same 404 as a missing one. There is no admin pass and no student route: papers carry answers.

| Route | What it does |
| --- | --- |
| `GET`, `POST /api/v1/lms/faculty/courses/{id}/exam-blueprints` | List or add blueprints |
| `PUT /api/v1/lms/faculty/exam-blueprints/{id}`, `POST …/archive` | Change or archive a blueprint |
| `POST /api/v1/lms/faculty/exam-blueprints/{id}/papers` | Build a draft paper from it |
| `GET`, `POST /api/v1/lms/faculty/courses/{id}/exam-papers` | List papers, or add an empty one |
| `GET`, `PUT /api/v1/lms/faculty/exam-papers/{id}` | Read a paper with its questions and answers, or change its details |
| `PUT /api/v1/lms/faculty/exam-papers/{id}/questions` | Save the builder's question list |
| `POST …/finalize`, `POST …/archive` | Make final, or archive |
| `GET /api/v1/lms/faculty/exam-papers/{id}/document?answers=` | The Word document, as an attachment, never cached |

## Word export

`ExamPaperDocument` writes a small `.docx` by hand (no library): the title, course, time and maximum marks, instructions, section headings, numbered questions with their marks, lettered choices, and, with `answers=true`, an answer key on its own page (correct choices, true or false, accepted answers, or marking guidance, with explanations). Formatted question text is written as it was typed. The document is built per request and never stored.

## Pages

Exam papers is a section of the teaching course navigation:

- `/teaching/:lmsCourseId/papers` lists blueprints (New blueprint, Edit, Build paper, Archive confirmed first) and papers (New paper).
- `/teaching/:lmsCourseId/papers/:paperId` is the builder: Make final and Archive (each confirmed), Print view, Download Word with or without answers, the details, and, in a draft, the questions with section, marks, Move up, Move down, and Remove, saved together with Save questions, and the reviewed bank questions to add.
- `/teaching/:lmsCourseId/papers/:paperId/print` shows the paper as printed, with Show answer key and Print; a print style prints only the paper.

## Demo data

CS301 has the blueprint "Unit 1 test blueprint" (one row per kind of demo question, 14 marks) and the draft paper "Unit 1 test" built from it.
