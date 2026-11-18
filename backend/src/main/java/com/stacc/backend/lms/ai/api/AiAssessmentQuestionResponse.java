package com.stacc.backend.lms.ai.api;

import java.math.BigDecimal;
import java.util.List;

public record AiAssessmentQuestionResponse(
        String questionType,
        String prompt,
        List<String> options,
        String suggestedAnswer,
        BigDecimal suggestedMarks,
        List<AiCitationResponse> citations) {
}
