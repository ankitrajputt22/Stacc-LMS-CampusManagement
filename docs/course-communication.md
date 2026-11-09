# Course Discussions and Messages

Stacc v0.8 adds three things to every LMS course: discussions, private messages with the course's faculty, and one shared way to keep files on records.

## Discussions (`lms.discussion`)

- A thread (`LmsDiscussionThread`) has a title and plain text and belongs to one LMS course. Replies (`LmsDiscussionReply`) belong to one thread.
- Students take part in a course that `LmsStudentAccessService` allows. Faculty take part in a course that `LmsFacultyAccessService` allows. Both checks go through `LmsCourseParticipation`. The route decides whether someone acts as a student or as faculty; the access token decides who they are.
- Anyone taking part can start threads and reply. Faculty can pin and unpin a thread. The student who asked, or the faculty, can accept one reply as the answer; choosing another reply replaces the earlier choice.
- Threads and replies are never edited or deleted. Authors appear by login ID with a Faculty badge, never by name or contact details.
- Routes:
  - Students: `/api/v1/lms/courses/{id}/discussions…`, needing `STUDENT`, `LMS_COURSE_VIEW`, and `LMS_DISCUSSION_USE`.
  - Faculty: `/api/v1/lms/faculty/courses/{id}/discussions…`, needing `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_DISCUSSION_USE`.
  - A course or thread the caller may not use answers 404.

## Messages (`lms.message`)

- A student starts a private conversation (`LmsConversation`) with the faculty of a course. Every faculty member teaching that course can read and answer it.
- A student sees only their own conversations, and loses them along with course access. Another person's conversation answers the same 404 as a missing one.
- Messages are append-only.
- Routes are `/api/v1/lms/courses/{id}/conversations…` and `/api/v1/lms/faculty/courses/{id}/conversations…`, with `LMS_MESSAGE_USE`. Sending is `multipart/form-data` (`subject` for a new conversation, `body`, and up to five `files`).

## Attachments pattern (`lms.attachment.LmsAttachmentStore`)

- A feature keeps its own attachment table and decides who may download each file. `LmsAttachmentStore` does the rest:
  - checks each upload with the study-material rules (size, allowed type, declared type, first bytes);
  - stores it in the private bucket under a key made from IDs and a random part;
  - removes the stored files again if the record fails;
  - opens a file for a download the feature has already allowed.
- Downloads are always attachments. Keys never reach a client.
- Message files (`lms_conversation_message_attachments`) are the first user, downloaded through `/conversations/{id}/attachments/{attachmentId}` after the conversation's own check.

## Pages

- Students: `/courses/:id/discussions`, `/courses/:id/discussions/:threadId`, `/courses/:id/messages`, and `/courses/:id/messages/:conversationId`.
- Faculty: the same paths under `/teaching/:id`.
- Discussions and Messages appear in both course navigations.
- Texts are shown as plain text.
