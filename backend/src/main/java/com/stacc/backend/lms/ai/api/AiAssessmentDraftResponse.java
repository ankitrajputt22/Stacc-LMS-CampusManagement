package com.stacc.backend.lms.ai.api;

import java.util.List;

public record AiAssessmentDraftResponse(
        String titleSuggestion,
        String instructionsSuggestion,
        List<AiAssessmentQuestionResponse> questions,
        boolean insufficientContext) {
}
