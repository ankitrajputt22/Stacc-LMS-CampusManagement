package com.stacc.backend.audit;

/** What happened. Stored as text, so new actions can be added without touching old rows. */
public enum AuditAction {
    USER_CREATED,
    USER_DETAILS_CHANGED,
    USER_STATUS_CHANGED,
    USER_ROLES_CHANGED,
    USER_TEMPORARY_PASSWORD_ISSUED,
    USER_PHOTO_CHANGED,
    USERS_IMPORTED,
    STUDENT_PROFILE_CREATED,
    FACULTY_PROFILE_CREATED,
    DEPARTMENT_HEAD_ASSIGNED,
    DEPARTMENT_HEAD_ENDED,
    REPORT_EXPORT_REQUESTED,
    REPORT_EXPORT_DOWNLOADED
}
