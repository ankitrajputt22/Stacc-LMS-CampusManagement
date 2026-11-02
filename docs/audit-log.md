# Audit Log

One append-only record of sensitive actions, shared by every module (v0.2.5). The code is `com.stacc.backend.audit`; the table is `audit_events`.

## Recording

- Feature services call `AuditService.record(actorAccountId, action, targetType, targetId, details)` inside their own transaction (`Propagation.MANDATORY`), so a change and its record are saved together or not at all.
- Each event keeps the moment, the acting account (null for the system), the action, the target's type and ID, short plain-text details (at most 500 characters), and the request ID from `RequestIdFilter`, so it can be matched with its log lines.
- Details never hold a password, hash, token, name, email, or phone number. User management writes, for example, "ACTIVE to DISABLED." or "STUDENT to STUDENT, FACULTY."
- Rows are never changed or deleted: `AuditEventRepository` can only save and read, and no route writes to it.
- Actions (`AuditAction`, stored as text): `USER_CREATED`, `USER_DETAILS_CHANGED`, `USER_STATUS_CHANGED`, `USER_ROLES_CHANGED`, `USER_TEMPORARY_PASSWORD_ISSUED`, `USER_PHOTO_CHANGED` (from `PersonPhotoService`), `STUDENT_PROFILE_CREATED`, and `FACULTY_PROFILE_CREATED`, all from `UserAdminService` with target type `USER_ACCOUNT`. Later phases add their own actions.

## Reading

`GET /api/v1/admin/audit-events` needs `ADMIN` with `AUDIT_LOG_VIEW` (`V76`). It answers one page (size 1 to 100, default 20), newest first, filtered by `action` and by `from` (included) and `to` (excluded), ISO 8601 moments with a zone, and searched with `q` in the login ID of the actor or of a target account. Each row has the moment, the actor's login ID, the action, the target, the target's login ID for an account, the details, and the request ID.

The page is **Audit log** in the Manage tray (`/admin/audit`): an action select, From and To dates in the browser's time zone (the To day included), a login ID search sent on **Find**, and a paged table whose target links to the account's page.

## Not built yet

Recording for other modules (academic masters, enrollment, approvals, hostel, campus), export, and retention rules.
