# Course Files

A course's **Files** area (v0.6.3) lists every file of the course in one place. A file is a study material (`LmsStudyMaterial`): its details are in MySQL, its bytes in private object storage behind `ObjectStorageService`, and it is always on a page (`course-pages.md`). There is no second file model and no file that floats outside the course's pages; for course-wide documents, make a page such as "Course resources". Uploads, file checks (allow-list, size, declared type, first bytes), storage keys, downloads, and archiving are exactly as described in `lms-foundation.md`.

## Students

- `/courses/:lmsCourseId/files`, linked as **All files** from Course content on the course home: every published file in course order (modules, then pages, then files) with its type, size, upload date, page (a link to it) and module, and **Download**. **Search files** narrows the list on the page by title, file name, page, or module.
- `GET /api/v1/lms/courses/{lmsCourseId}/files` needs `STUDENT` and `LMS_COURSE_VIEW`. It is built from exactly what `GET /courses/{lmsCourseId}/content` shows, so a file appears only when the student may use the course and the file, its page, and its module are all `PUBLISHED`. A course they may not use answers the same 404 as a missing one.

## Faculty

- `/teaching/:lmsCourseId/files`, linked as **Files** in the course's tools: every file that is not archived, drafts included, with its page (a link to the page preview) and module, and whether students see it. A hidden file says why: the file, its page, or its module is a draft, or the course is not published.
- **Upload file** puts a new file on a chosen page as a draft, through the same upload route and checks as course content. **Publish** and **Archive** (confirmed first) use the existing material routes. Nothing is deleted.
- `GET /api/v1/lms/faculty/courses/{lmsCourseId}/files` needs `FACULTY` and `FACULTY_COURSE_VIEW` and is built from exactly what the faculty content route lists (`LmsFacultyAccessService`). Changes still need `LMS_CONTENT_MANAGE`.

File responses carry `uploadedAt` (when the material was created). Who uploaded a file is not stored. Both list routes only read.
