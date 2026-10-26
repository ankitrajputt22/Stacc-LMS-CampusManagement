package com.stacc.backend.lms.ai.api;

import java.math.BigDecimal;
import java.util.List;

/** One drafted question. Its suggested answer and marks are suggestions for the teacher to review, nothing more. */
public record AiAssessmentQuestionResponse(
        String questionType,
        String prompt,
        List<String> options,
        String suggestedAnswer,
        BigDecimal suggestedMarks,
        List<AiCitationResponse> citations) {
}
