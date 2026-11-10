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

### Moderation (v0.8.3)

- Faculty of the course (with `LMS_DISCUSSION_MODERATE`, `V103`) hide a thread or a reply and lock a thread. Nothing is deleted: a hidden thread is left out of the students' list and answers them the same 404 as a missing one; a hidden reply reaches students only as hidden, without its author or text, and stops being the accepted answer. A locked thread takes no new replies (409), from anyone, until it is unlocked. Faculty see everything, marked as hidden.
- Students report a thread or a reply with a reason (up to 500 characters) to the course's faculty (`lms_discussion_reports`). Hidden posts and one's own cannot be reported, and a person has one open report per post (a unique key on open reports, checked under the thread row lock).
- Faculty see the open reports of the course without the reporters, and resolve a post's reports together: Hide post (the post is hidden and its reports resolved as `HIDDEN`) or Dismiss (`DISMISSED`). Hiding a post directly resolves its open reports too. Reports are never deleted.
- Routes: faculty `POST …/discussions/{threadId}/hide`, `…/lock`, `…/replies/{replyId}/hide`, `GET /api/v1/lms/faculty/courses/{id}/discussion-reports`, and `POST …/discussion-reports/{reportId}/resolve`, each needing `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_DISCUSSION_MODERATE`; students `POST /api/v1/lms/courses/{id}/discussions/{threadId}/reports`.
- Every moderation change locks the thread row first (`LmsDiscussionModerationMySqlIT`).

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
- The faculty discussions page starts with Reported posts (Hide post, Dismiss) when there are open reports; a thread shows Lock and Hide from students to faculty and Report to students, with a reason form.
