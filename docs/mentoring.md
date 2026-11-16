# Mentoring

Phase 0.12.4. Mentoring lives in the `mentoring` module.

## Pairings

- Admins (`ADMIN` with `MENTORING_MANAGE`, at `/admin/mentoring`) pair a mentor (an active account holding `FACULTY`) with a student (an active account holding `STUDENT`), by login ID (`V119`). A mentor may have many students and a student many mentors, but each pair is active at most once (a unique generated column).
- Admins end pairings; nothing is deleted. Admins see who mentors whom, never the meetings or notes.

## Meetings, notes, and action items

- A pairing opens only for its mentor and its student (`MENTORING_USE` with `STUDENT` or `FACULTY`, at `/mentoring`). Anyone else gets the same 404 as for a missing one, and there is no admin pass.
- Only the mentor of an active pairing records and edits meetings: a title, when it was held, shared notes (both read them), and private notes. Private notes are sent to the mentor only and are always empty for the student.
- The mentor adds action items to a meeting, each owned by the mentor or the student, with an optional due time. An open item of an active pairing appears on its owner's To-Do list (kind `MENTORING`, worked out live by `TodoService` from `MentoringService.openItems`, never copied). Either of the pair marks it done, once; it is never deleted.
- An ended pairing stays readable by the pair but takes no new meetings or items.

## Routes

| Route | Who |
| --- | --- |
| `GET /api/v1/mentoring/me`, `GET …/pairings/{id}`, `POST …/pairings/{id}/meetings`, `PUT …/meetings/{id}`, `POST …/meetings/{id}/action-items`, `POST …/action-items/{id}/complete` | `STUDENT` or `FACULTY` with `MENTORING_USE`; the pair member comes from the token |
| `GET`/`POST /api/v1/admin/mentoring/pairings`, `POST …/pairings/{id}/end` | `ADMIN` with `MENTORING_MANAGE` |

The demo data pairs `faculty1` as mentor with `student1`.
