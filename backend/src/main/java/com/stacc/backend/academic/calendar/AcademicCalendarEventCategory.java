package com.stacc.backend.academic.calendar;

/**
 * What kind of official date an academic calendar event is. Assignment deadlines are not a category: they always
 * come from the LMS assignment itself and are never stored as calendar events.
 */
public enum AcademicCalendarEventCategory {
    ACADEMIC,
    HOLIDAY,
    EXAM,
    REGISTRATION,
    OTHER
}
