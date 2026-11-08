# Course Home and Navigation

Every page of an LMS course shares one **course navigation** (v0.6.6): a left column on wide screens and a **Course menu** that opens on phones, so content keeps the full width there (`CourseNav`, `CourseFrame` in `src/courses`). It lists only sections Stacc has; it is never access control, and each page still asks the backend for its own data.

## Students (`/courses/:lmsCourseId`, `CourseLayout`)

| Item | Address | Shows |
| --- | --- | --- |
| Home | `/courses/:lmsCourseId` | The official course details (code, name, program, semester, session, credits, section, and teachers as the API sends them), then what the faculty chose. |
| Modules | `/modules` | Your progress and the published modules with their pages. |
| Pages | `/pages` | Every published page in course order, with its module and Required, Completed, Optional, or Locked. |
| Assignments | `/assignments` | The published assignments; each opens at `/assignments/:assignmentId`. |
| Announcements | `/announcements` | The published announcements, newest first. |
| Files | `/files` | See `course-files.md`. |
| Syllabus | `/syllabus` | See `course-syllabus.md`. |
| AI Study Assistant | `/ai` | See `ai-learning.md`. |

Grades are not listed: grade pages come with v0.7.

## The chosen course home

Faculty choose what **Home** shows (`LmsCourse.homeView`, `V82`):

- `OVERVIEW` (the default): the welcome message, Your progress, announcements, assignments, and the modules.
- `MODULES`: Your progress and the modules.
- `SYLLABUS`: the published syllabus.

The optional **welcome message** (`LmsCourse.welcomeMessage`, formatted text up to 5,000 characters, see `text-formatting.md`) appears on the overview. Both are LMS settings: official course details always come from the academic records and are never stored on the LMS course. `PUT /api/v1/lms/faculty/courses/{lmsCourseId}/home` (`FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_COURSE_MANAGE`, through `LmsCourseManagementService`, locking the course; 409 for an archived course) changes them. `GET /api/v1/lms/courses/{lmsCourseId}` now also sends `sectionCode`, `faculty` (active teachers' names), `homeView`, and `welcomeMessage`.

## Faculty (`/teaching/:lmsCourseId`, `TeachingCourseLayout`)

| Item | Address | Shows |
| --- | --- | --- |
| Home | `/teaching/:lmsCourseId` | Course settings (publish and archive) and **Course home** (Home shows, the welcome message with Write and Preview, Save course home). |
| Modules | `/modules` | The content editor: modules, pages, requirements, and study materials; page previews open at `/pages/:pageId`. |
| Announcements | `/announcements` | The announcement editor. |
| Assignments | `/assignments` | The assignment editor; grading opens at `/assignments/:assignmentId/submissions`. |
| Files | `/files` | See `course-files.md`. |
| Syllabus | `/syllabus` | See `course-syllabus.md`. |
| AI Tools | `/ai` | See `ai-learning.md`. "Use in assignment form" now opens the Assignments page. |

Links that used to land on the single teaching page now open the right section: calendar teaching deadlines and faculty assignment search results open Assignments, announcement results Announcements, material results Files, and student announcement results the student's Announcements page.
