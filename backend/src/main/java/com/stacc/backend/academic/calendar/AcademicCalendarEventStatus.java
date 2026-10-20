package com.stacc.backend.academic.calendar;

/** Where an official calendar event is in its life. It moves forward only: nothing is restored or deleted. */
public enum AcademicCalendarEventStatus {
    /** Being prepared by an admin. No student or faculty member sees it. */
    DRAFT,
    /** On the calendar of the students and faculty it applies to. */
    PUBLISHED,
    /** Called off for good. If it was ever published, it stays on the calendar, marked as cancelled. */
    CANCELLED
}
