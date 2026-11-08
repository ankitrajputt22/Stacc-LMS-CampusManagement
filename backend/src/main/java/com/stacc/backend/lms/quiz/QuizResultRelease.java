package com.stacc.backend.lms.quiz;

/** When students see the result of a handed-in attempt. Either way, only once every answer that needs a teacher is marked. */
public enum QuizResultRelease {
    /** As soon as the attempt is fully marked: at once for a quiz marked automatically. */
    AFTER_MARKING,
    /** Also not before the due date (or, without one, the closing time; with neither, after marking). */
    AFTER_DUE_DATE
}
