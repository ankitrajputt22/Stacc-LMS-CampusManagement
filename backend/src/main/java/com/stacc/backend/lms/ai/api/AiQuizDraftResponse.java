package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** The draft quiz built (v0.14.3). It is an ordinary draft quiz; publishing still needs every question reviewed. */
public record AiQuizDraftResponse(
        long quizId,
        @Schema(description = "Reviewed bank questions used.") int fromBank,
        @Schema(description = "Questions AI drafted into the bank for the gap; they need review before publishing.") int fromAi,
        @Schema(description = "Questions still missing from the requested count.") int missing,
        @Schema(description = "Why AI did not fill the gap, in plain words, or null.", nullable = true) String aiNote) {
}
