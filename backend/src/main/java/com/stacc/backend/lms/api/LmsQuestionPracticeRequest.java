package com.stacc.backend.lms.api;

import jakarta.validation.constraints.NotNull;

/** Whether a reviewed question is offered for practice. */
public record LmsQuestionPracticeRequest(@NotNull(message = "Choose whether the question is offered for practice") Boolean allowed) {
}
