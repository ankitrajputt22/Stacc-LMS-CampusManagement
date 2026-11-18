package com.stacc.backend.lms.ai.api;

import java.util.List;

public record AiPracticeQuestionResponse(String question, String suggestedAnswer, List<AiCitationResponse> citations) {
}
