package com.stacc.backend.notification;

/** What happened. Each value has a real producer in a feature service; add one only together with its producer. */
public enum NotificationType {
    /** An assignment was published for the first time. */
    ASSIGNMENT_PUBLISHED,
    /** An attempt got its first grade, or its grade or feedback changed. */
    ASSIGNMENT_GRADED,
    /** An official calendar event was published for the first time. */
    ACADEMIC_CALENDAR_PUBLISHED,
    /** A published official calendar event was cancelled. */
    ACADEMIC_CALENDAR_CANCELLED,
    /** A finalized attendance record was corrected between present and absent. */
    ATTENDANCE_UPDATED,
    /** An official result was published. */
    RESULT_PUBLISHED,
    /** A published official result was corrected. */
    RESULT_UPDATED,
    /** Someone other than the requester replied on a helpdesk ticket. */
    HELPDESK_REPLY,
    /** A helpdesk ticket's status changed. */
    HELPDESK_STATUS_CHANGED,
    /** A helpdesk ticket was assigned to an admin. */
    HELPDESK_ASSIGNED,
    /** An approval step became the current step and waits for one of the account's roles. */
    APPROVAL_ACTION_REQUIRED,
    /** An approval request was approved at its last step. */
    APPROVAL_APPROVED,
    /** An approval request was rejected. */
    APPROVAL_REJECTED,
    /** An approval request was cancelled by the module that owns its record. */
    APPROVAL_CANCELLED,
    /** A student was allocated a hostel bed. */
    HOSTEL_ALLOCATED,
    /** A student's active hostel allocation was vacated. */
    HOSTEL_VACATED,
    /** A campus notice was published for the first time. */
    CAMPUS_NOTICE_PUBLISHED,
    /** A campus service request was completed. */
    CAMPUS_SERVICE_COMPLETED
}
