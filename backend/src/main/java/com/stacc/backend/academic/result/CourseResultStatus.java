package com.stacc.backend.academic.result;

/** Where an official course result stands. Stored as the word, never as a number. */
public enum CourseResultStatus {
    /** Being recorded. The student does not see it. New results start here. */
    DRAFT,
    /** Visible to the student. It stays published, also after a correction. */
    PUBLISHED
}
