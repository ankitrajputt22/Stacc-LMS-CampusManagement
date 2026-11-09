package com.stacc.backend.lms.ai.api;

import java.util.List;

/** Practice questions for self-study. Nothing is saved, and nothing affects grades, results, or assignments. */
public record AiPracticeResponse(List<AiPracticeQuestionResponse> questions, boolean insufficientContext) {
}
