package com.stacc.backend.notification;

/** The groups a person chooses delivery for. Every notification type belongs to exactly one. */
public enum NotificationCategory {
    COURSES,
    CALENDAR,
    SUPPORT,
    APPROVALS,
    CAMPUS;

    public static NotificationCategory of(NotificationType type) {
        return switch (type) {
            case ASSIGNMENT_PUBLISHED, QUIZ_PUBLISHED, ANNOUNCEMENT_PUBLISHED, ASSIGNMENT_GRADED, ATTENDANCE_UPDATED, RESULT_PUBLISHED, RESULT_UPDATED -> COURSES;
            case ACADEMIC_CALENDAR_PUBLISHED, ACADEMIC_CALENDAR_CANCELLED -> CALENDAR;
            case HELPDESK_REPLY, HELPDESK_STATUS_CHANGED, HELPDESK_ASSIGNED -> SUPPORT;
            case APPROVAL_ACTION_REQUIRED, APPROVAL_APPROVED, APPROVAL_REJECTED, APPROVAL_CANCELLED -> APPROVALS;
            case HOSTEL_ALLOCATED, HOSTEL_VACATED, HOSTEL_EMERGENCY, CAMPUS_NOTICE_PUBLISHED, GROUP_ANNOUNCEMENT, CAMPUS_SERVICE_READY, CAMPUS_SERVICE_COMPLETED -> CAMPUS;
        };
    }
}
