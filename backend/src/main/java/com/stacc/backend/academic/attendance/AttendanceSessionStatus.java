package com.stacc.backend.academic.attendance;

/** Where a class session's attendance stands. Stored as the word, never as a number. */
public enum AttendanceSessionStatus {
    /** Being taken. Students do not see it. */
    OPEN,
    /** Every student is marked, and students see their own record. Marks can still be corrected. */
    FINALIZED
}
