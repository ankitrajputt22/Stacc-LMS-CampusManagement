package com.stacc.backend.notification;

/**
 * What kind of record a notification is about. With the source ID it is a loose, historical reference only: no foreign
 * key, and never a way past the source feature's own access rules.
 */
public enum NotificationSourceType {
    ASSIGNMENT,
    SUBMISSION_GRADE,
    ACADEMIC_CALENDAR_EVENT,
    ATTENDANCE_RECORD,
    COURSE_RESULT,
    HELPDESK_TICKET,
    APPROVAL_REQUEST
}
