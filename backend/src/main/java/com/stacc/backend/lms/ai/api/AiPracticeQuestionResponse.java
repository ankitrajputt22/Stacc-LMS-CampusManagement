package com.stacc.backend.lms.ai.api;

import java.util.List;

/** One practice question with a suggested answer. It is never saved, graded, or counted anywhere. */
public record AiPracticeQuestionResponse(String question, String suggestedAnswer, List<AiCitationResponse> citations) {
}
