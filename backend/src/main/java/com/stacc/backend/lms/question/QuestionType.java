package com.stacc.backend.lms.question;

/** The kinds of question. Choice and true/false questions are marked automatically later; descriptive ones by hand. */
public enum QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    SHORT_ANSWER,
    DESCRIPTIVE
}
