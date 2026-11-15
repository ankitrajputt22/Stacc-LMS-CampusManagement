package com.stacc.backend.lms.ai.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** What a question bank draft added (v0.14.1). The questions themselves are in the bank, each NEEDS_REVIEW. */
public record AiQuestionBankDraftResponse(
        @Schema(description = "Questions added to the bank, each needing review.") int added,
        @Schema(description = "Questions dropped because the AI answered them differently from their own key.") int droppedForKeyMismatch,
        @Schema(description = "Questions dropped because they repeated another draft of the same request (v0.14.2).") int droppedAsRepeats,
        @Schema(description = "Questions left out because they had no valid course source or broke the bank's rules.") int leftOut,
        @Schema(description = "True when the course sources gave nothing to draft from.") boolean insufficientContext,
        @Schema(description = "The new questions' IDs, in the order drafted.") List<Long> questionIds) {
}
