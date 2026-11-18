package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record AiPracticeRequest(
        @Schema(description = "From 1 to 10; 5 when omitted.", example = "5") @Min(1) @Max(10) Integer questionCount,
        @Schema(description = "A published topic of the course, or omitted for the whole course.") @Positive Long topicId,
        @Schema(description = "True (v0.15.6): choose the page to practise, weighted to the student's weaker pages; "
                + "topicId is then ignored.", nullable = true) Boolean adaptive) {

    public AiPracticeRequest(Integer questionCount, Long topicId) {
        this(questionCount, topicId, null);
    }

    public static final int DEFAULT_QUESTION_COUNT = 5;

    public int questionCountOrDefault() {
        return questionCount == null ? DEFAULT_QUESTION_COUNT : questionCount;
    }
}
