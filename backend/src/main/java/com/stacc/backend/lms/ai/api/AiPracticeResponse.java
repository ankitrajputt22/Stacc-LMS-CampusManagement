package com.stacc.backend.lms.ai.api;

import java.util.List;

/** Practice questions for self-study. Nothing is saved, and nothing affects grades, results, or assignments. */
public record AiPracticeResponse(List<AiPracticeQuestionResponse> questions, boolean insufficientContext,
        @io.swagger.v3.oas.annotations.media.Schema(description = "With adaptive practice (v0.15.6), the page chosen.", nullable = true)
        Long chosenTopicId,
        @io.swagger.v3.oas.annotations.media.Schema(description = "With adaptive practice, why that page, in words.", nullable = true)
        String reason) {

    public AiPracticeResponse(List<AiPracticeQuestionResponse> questions, boolean insufficientContext) {
        this(questions, insufficientContext, null, null);
    }
}
