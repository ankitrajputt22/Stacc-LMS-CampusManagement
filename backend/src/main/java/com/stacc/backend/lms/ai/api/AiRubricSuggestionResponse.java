package com.stacc.backend.lms.ai.api;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

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
