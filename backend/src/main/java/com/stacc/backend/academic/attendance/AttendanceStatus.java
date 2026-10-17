package com.stacc.backend.academic.attendance;

/** One student's attendance in one session. Stored as the word, never as a number. */
public enum AttendanceStatus {
    /** Not marked yet. This is how every record starts; it never means absent. */
    UNMARKED,
    PRESENT,
    ABSENT
}
