package com.stacc.backend.lms.ai.api;

import java.util.List;

/**
 * An AI-generated assessment draft for a teacher to review and edit. It is not saved and creates, publishes, or
 * grades nothing; only the teacher's own submission of the normal assignment form creates an assignment.
 */
public record AiAssessmentDraftResponse(
        String titleSuggestion,
        String instructionsSuggestion,
        List<AiAssessmentQuestionResponse> questions,
        boolean insufficientContext) {
}
