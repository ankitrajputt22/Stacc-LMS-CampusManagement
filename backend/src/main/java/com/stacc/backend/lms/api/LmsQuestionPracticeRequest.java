package com.stacc.backend.lms.api;

import jakarta.validation.constraints.NotNull;

public record LmsQuestionPracticeRequest(@NotNull(message = "Choose whether the question is offered for practice") Boolean allowed) {
}
