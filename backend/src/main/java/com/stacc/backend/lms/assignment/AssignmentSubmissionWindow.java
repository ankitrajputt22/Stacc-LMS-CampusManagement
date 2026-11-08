package com.stacc.backend.lms.assignment;

/** Whether an assignment takes new attempts at a moment, from its opening, due, and closing times. */
public enum AssignmentSubmissionWindow {
    /** Before the opening time. */
    NOT_OPEN,
    /** Open; work handed in after the due date is marked late. */
    OPEN,
    /** After the due date when late work is not accepted, or after the closing time. */
    CLOSED
}
