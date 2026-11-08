# Learning Requirements and Progress

Basic sequential learning (v0.6.5): faculty mark pages **required** and let a module **wait for the previous module**; students mark pages **complete** and see their own progress. Everything about a student's path is worked out live; only the completions are stored.

## Rules

- **Required pages** (`lms_topics.required`, `V81`): count towards their module's completion and the student's progress. Optional pages can be completed too but count for nothing.
- **Waiting modules** (`lms_modules.requires_previous`): such a module is **locked** for a student until the module before it (in course order, among the published modules they can see) is complete. The first module never waits. A single flag instead of a chosen prerequisite module keeps reordering safe and makes loops impossible.
- A module is **complete** for a student when it is open and every required page in it is completed; a module without required pages is complete once open.
- **Progress** is required pages completed out of required pages the student can see, with the next required page in an open module ("Continue"). It is the student's own view, never a grade, and never stored.
- The rule lives once, in `LmsLearningPath` (`lms.content`); `LmsLearningPathService` loads what it needs. Locked pages show their titles but no text and no files: the page route, the Files list, and file downloads all honour the lock.

## Completions

- `LmsPageCompletion` (`lms_page_completions`): one row per official course enrollment and page, with `completedAt`. Like a submission it belongs to the `CourseEnrollment` that gives access, found with `LmsStudentAccessService.findAccessGrantingEnrollment`, never to an account or anything the client sends.
- A completion is recorded once and never changed or deleted; there is no "mark incomplete".
- `LmsPageCompletionService` checks the page is visible to the student and its module open (409 when locked), locks the course enrollment row (re-checking it is still `ENROLLED`), then reads the completion with a locking read before inserting, so two clicks at once record one (`LmsPageCompletionMySqlIT`).
- Opening a page records nothing: read pages never change data. Completion is always the student's explicit **Mark as complete**; Stacc does not claim that opening a page or downloading a file means it was studied.

## API

| Route | Needs | Does |
| --- | --- | --- |
| `GET /api/v1/lms/courses/{lmsCourseId}/progress` | `STUDENT` + `LMS_COURSE_VIEW` | Required pages, completed required pages, and the next page, for the signed-in student. |
| `POST /api/v1/lms/courses/{lmsCourseId}/pages/{pageId}/complete` | `STUDENT` + `LMS_COURSE_VIEW` | Marks the page complete once and answers the progress. 404 for a hidden page, 409 for a locked one. |
| `PUT /api/v1/lms/faculty/topics/{topicId}/required` | `FACULTY` + `LMS_CONTENT_MANAGE` | `{ "required": true or false }`. |
| `PUT /api/v1/lms/faculty/modules/{moduleId}/requires-previous` | `FACULTY` + `LMS_CONTENT_MANAGE` | `{ "required": true or false }`. |

The student content tree carries `required` and `completed` per page and `requiresPrevious`, `locked`, `completed`, and `waitingFor` per module; the page route adds the same flags. Faculty responses carry `required` and `requiresPrevious`.

## Pages

- Course home: **Your progress** ("1 of 4 required pages completed", a progress bar, and Continue), shown only when the course has required pages. Modules show Locked (with the module to complete first) or Module completed; pages show Required or Completed, and a locked module's pages are not links.
- A page shows whether it is required and **Mark as complete** or Completed; a locked page says which module to complete first.
- Faculty course content: **Make required** or **Make optional** on each page, and **Wait for previous module** on every module after the first, with tags showing both.

Not in this phase: quiz or assignment requirements, several prerequisites, unlock dates, faculty progress reports, or completion notifications.
