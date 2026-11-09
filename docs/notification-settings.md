# Notification Settings and Email

Each person chooses, per category, whether notifications show in the Inbox and whether they are emailed. Email is either sent at once, collected into a daily digest, or not sent at all.

| Category | Notification types |
| --- | --- |
| `COURSES` | Assignment and quiz published, assignment graded, attendance updated, result published or updated |
| `CALENDAR` | Academic calendar event published or cancelled |
| `SUPPORT` | Helpdesk reply, status change, assignment |
| `APPROVALS` | Approval needed, approved, rejected, cancelled |
| `CAMPUS` | Hostel allocated or vacated, campus notice, campus request completed |

## Storage and delivery

- **Defaults:** `notification_preferences` keeps one row per account and category. Without a row, the Inbox is on and nothing is emailed.
- **Inbox off:** `NotificationService` then creates no Inbox row for that person.
- **Instant email:** sent after the change commits, so a rolled-back change emails nobody.
- **Daily digest:** a waiting row in `notification_digest_items`. `NotificationDigestJob` runs at `MAIL_DIGEST_CRON` (UTC), only while `MAIL_ENABLED=true`. It locks the waiting rows, marks them sent, and emails each person one list after that commits, so a digest is sent at most once.
- **Content:** emails are plain text: the title, the message, and a link built from `MAIL_LINK_BASE_URL` and the notification's own path. The destination page still checks access.
- **Recipients:** an email goes only to the college-recorded address on the person's profile, and only for an `ACTIVE` account.
- **Privacy:** no address or email content is ever stored or logged. A send failure is logged as a count only, and never affects the change.

## Settings and API

- **Mail server:** set with the `MAIL_*` settings (see `configuration.md`). Email is off by default. The mail server is never part of health.
- **API:**
  - `GET /api/v1/notifications/settings` reads one's own choices.
  - `PUT /api/v1/notifications/settings` changes them.
  - Both need `NOTIFICATION_VIEW` with `STUDENT`, `FACULTY`, or `ADMIN`, and take the account from the token.
- **Page:** `/inbox/settings`, linked from the Inbox.
