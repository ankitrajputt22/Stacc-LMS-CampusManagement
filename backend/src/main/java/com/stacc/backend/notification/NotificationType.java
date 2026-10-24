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
    RESULT_UPDATED
}
