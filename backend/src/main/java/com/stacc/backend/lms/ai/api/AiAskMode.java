package com.stacc.backend.lms.ai.api;

/** What a student's question searches (v0.13.9). */
public enum AiAskMode {
    /** The course's published content only. */
    COURSE,
    /** The course's published content and the student's own notes. */
    COURSE_AND_NOTES,
    /** The student's own notes only. */
    NOTES_ONLY
}
