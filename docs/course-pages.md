# Course Pages

A **page** is what a module holds: a title, its text (plain or formatted, see `text-formatting.md`), and its study materials, kept in the module's order with `DRAFT`, `PUBLISHED`, and `ARCHIVED` states (v0.6.2). In the code and database a page is an `LmsTopic` (`lms_topics`); there is one model, not a second "page" table. Everything in `lms-foundation.md` about topics applies to pages.

## Students

- The course home lists each published module with its published pages as links, with a file count, instead of showing the text inline.
- `/courses/:lmsCourseId/pages/:pageId` opens one page: the module's title, the page title, the text, the study materials with Download, and links to the published pages before and after it in course order (modules in order, then pages in each module's order).
- `GET /api/v1/lms/courses/{lmsCourseId}/pages/{pageId}` needs `STUDENT` and `LMS_COURSE_VIEW`. It is answered from exactly what `GET /courses/{lmsCourseId}/content` shows (`LmsStudentAccessService` and `LmsContentPolicy`), so a page that is missing, a draft, archived, in an unpublished module, in another course, or in a course the student may not use answers the same 404 ("Page not found or unavailable."). The page shows "Page unavailable" for all of them.
- Search results for study materials open the material's page.

## Faculty

- Course content (`/teaching/:lmsCourseId`) calls topics pages: Add page, Edit, Publish, Archive, Move up and Move down, and **Preview** on every page.
- `/teaching/:lmsCourseId/pages/:pageId` shows the page as students will see it, drafts included, with a note saying whether students can see it now and, if not, why (the page or its module is a draft, or the course is not published). Editing stays in course content (**Edit in course content**). Previous and next go through the pages that are not archived.
- `GET /api/v1/lms/faculty/pages/{pageId}` needs `FACULTY` and `FACULTY_COURSE_VIEW`. The course is found from the stored page, never from the client, and the page is answered from exactly what the faculty content route lists (`LmsFacultyAccessService`): an archived page, a page in an archived module, or one of a course they do not teach answers the same 404. The address's course is not trusted; links use the course the API returns.

Both routes only read. Nothing is copied: the page is read from the same rows every time.
