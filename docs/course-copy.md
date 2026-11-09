# Course Copy

Faculty can reuse a course's content from an earlier offering of the **same catalog course** that they taught (v0.6.8), so a new term does not start from nothing. It lives in `lms.copy` (`LmsCourseCopyService`).

## Who may copy what

- The target is an LMS course the faculty member may manage now (`LmsFacultyAccessService`: active profile, active teaching assignment, draft or published course).
- A source is an LMS course of the same `Course` in **another** offering, any term and any LMS status (archived included), for which the faculty member has a teaching assignment, active or ended (`LmsCourseRepository.findCopySources`). This is the only place an ended teaching assignment counts, and it only lets content be copied; it never grants access to the old course or its students.
- Anything else answers the same 404.

## What is copied

- Modules that are not archived, with their titles, descriptions, and "wait for previous module" flag.
- Their pages that are not archived: title, text, text format, and required flag.
- Their study files that are not archived: the bytes are copied to new server-made keys under the target (`lms/courses/{id}/topics/{id}/{uuid}`) through `ObjectStorageService`; storage keys never reach a client.
- The syllabus's draft text, only when the target has no syllabus yet; an existing syllabus is never replaced.

Everything arrives as a **draft**, after the target's own modules, so students see nothing until faculty publish it. Assignments, announcements, submissions, grades, completions, and anything about students are never copied. The target course row is locked first. If file storage fails, the files already copied are removed, nothing is saved, and the answer is 503.

## API and page

| Route | Needs | Does |
| --- | --- | --- |
| `GET /api/v1/lms/faculty/courses/{lmsCourseId}/copy-sources` | `FACULTY`, `FACULTY_COURSE_VIEW`, `LMS_CONTENT_MANAGE` | The sources, the latest term first. |
| `POST /api/v1/lms/faculty/courses/{lmsCourseId}/copy` | the same | `{ "sourceLmsCourseId": … }`; answers how many modules, pages, and files were copied and whether the syllabus was. |

On the faculty **Modules** page, **Copy from an earlier offering** appears only when there is a source: choose the term, **Copy content**, and confirm. The demo data has CS250 Algorithms Lab for `faculty2`: last session's archived course with a module, two pages, and a syllabus, and an empty draft course in semester 4.
