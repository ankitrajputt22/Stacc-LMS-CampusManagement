package com.stacc.backend.lms.question;

/** Whether a colleague (or the author) has checked the question and its answers. Editing a reviewed question needs a new review. */
public enum QuestionReviewStatus {
    NEEDS_REVIEW,
    REVIEWED
}
