package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/** How many practice questions to generate, optionally about one published topic. */
public record AiPracticeRequest(
        @Schema(description = "From 1 to 10; 5 when omitted.", example = "5") @Min(1) @Max(10) Integer questionCount,
        @Schema(description = "A published topic of the course, or omitted for the whole course.") @Positive Long topicId) {

    public static final int DEFAULT_QUESTION_COUNT = 5;

    public int questionCountOrDefault() {
        return questionCount == null ? DEFAULT_QUESTION_COUNT : questionCount;
    }
}
