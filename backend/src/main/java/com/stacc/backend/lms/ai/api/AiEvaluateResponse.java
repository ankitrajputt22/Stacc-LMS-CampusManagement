package com.stacc.backend.lms.ai.api;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** A practice answer's marks and feedback (v0.15.4); practice only, never an official mark, and nothing is stored. */
public record AiEvaluateResponse(
        @Schema(nullable = true) BigDecimal marks,
        int maxMarks,
        @Schema(description = "CORRECT, PARTLY_CORRECT, or INCORRECT.", nullable = true) String verdict,
        @Schema(description = "What is right and each mistake explained.", nullable = true) String feedback,
        @Schema(description = "In a viva, the next question.", nullable = true) String followUpQuestion,
        List<AiCitationResponse> citations,
        boolean insufficientContext) {
}
