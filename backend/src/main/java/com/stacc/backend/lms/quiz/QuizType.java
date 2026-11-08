package com.stacc.backend.lms.quiz;

/** What a quiz is for. Only reviewed questions go into a quiz; a practice quiz takes only questions offered for practice. */
public enum QuizType {
    /** Counts as assessment, such as a class test. */
    GRADED,
    /** For practice; built only from questions marked OK for practice. */
    PRACTICE
}
