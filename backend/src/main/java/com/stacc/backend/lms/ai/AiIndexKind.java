package com.stacc.backend.lms.ai;

/** Which index of a course a knowledge row describes (v0.13.5). */
public enum AiIndexKind {
    /** The course's published, student-visible content. */
    COURSE_CONTENT,
    /** The course's teacher-only documents, read by Teacher AI only. */
    TEACHER_DOCUMENTS
}
