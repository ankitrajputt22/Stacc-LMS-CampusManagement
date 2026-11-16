package com.stacc.backend.lms.ai.api;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A suggested rubric for an assignment (v0.14.5): criteria whose maximum points add up to the assignment's maximum
 * marks, each with levels. Only shown to the teacher; the rubric form saves it, as any rubric written by hand.
 */
public record AiRubricSuggestionResponse(
        @Schema(description = "The assignment's title, for the rubric's title.") String title,
        BigDecimal maxMarks,
        List<Criterion> criteria,
        List<AiCitationResponse> citations,
        boolean insufficientContext) {

    public record Criterion(String title, String description, BigDecimal maxPoints, List<Level> levels) {
    }

    public record Level(String title, String description, BigDecimal points) {
    }
}
