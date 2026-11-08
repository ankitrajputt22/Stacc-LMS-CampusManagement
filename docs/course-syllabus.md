# Course Syllabus

Every LMS course can have one **syllabus** (v0.6.4): formatted text (`text-formatting.md`) that faculty write and students read. It lives in `lms.syllabus` (`LmsSyllabus`, table `lms_syllabi`, `V80`), at most one per course.

## Model

- `draftContent` is what faculty save, as often as they like (required, at most 30,000 characters). `publishedContent` is what students read. Publishing copies the draft to the published version.
- `firstPublishedAt` is set by the first publication and never changes; `lastPublishedAt` moves with each publication that changes something. Publishing a draft students already read changes nothing. There is no unpublish and no delete.
- The official course code, name, credits, program, term, and department always come from the academic records and are shown beside the syllabus, never stored in it.
- Saving and publishing lock the course row and then read the syllabus with a locking read (a plain read could see a snapshot from before the lock), so two first saves at once make one syllabus (`LmsSyllabusMySqlIT`).

## API

| Route | Needs | Does |
| --- | --- | --- |
| `GET /api/v1/lms/courses/{lmsCourseId}/syllabus` | `STUDENT` + `LMS_COURSE_VIEW`, and `LmsStudentAccessService` | The published version and when it was published, or nulls when none is published. Drafts never appear. |
| `GET /api/v1/lms/faculty/courses/{lmsCourseId}/syllabus` | `FACULTY` + `FACULTY_COURSE_VIEW`, and `LmsFacultyAccessService` | The draft, the published version, both publication times, and whether the draft has unpublished changes; nulls when none is saved. |
| `PUT /api/v1/lms/faculty/courses/{lmsCourseId}/syllabus` | also `LMS_CONTENT_MANAGE` | Saves the draft (`{ "content": … }`), starting the syllabus when there is none. |
| `POST /api/v1/lms/faculty/courses/{lmsCourseId}/syllabus/publish` | also `LMS_CONTENT_MANAGE` | Publishes the saved draft; 409 when none is saved. |

A course the caller may not use answers the same 404 as a missing one. No notification is sent.

## Pages

- Students: **Syllabus** in the course home's tools opens `/courses/:lmsCourseId/syllabus`: the course details from the academic records, then the published syllabus, or "No syllabus published yet."
- Faculty: **Syllabus** in the teaching course's tools opens `/teaching/:lmsCourseId/syllabus`: a note saying whether students see the current text, the editor with Write and Preview (always formatted, no format choice), **Save draft**, and **Publish**, confirmed first, which saves the text and publishes it. A new syllabus starts from the usual sections as headings: overview, learning objectives, units, reference books, evaluation scheme, and policies.
