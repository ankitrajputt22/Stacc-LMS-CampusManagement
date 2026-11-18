package com.stacc.backend.lms.ai.api;

import java.util.List;

public record AiTopicSummaryResponse(
        long topicId,
        String topicTitle,
        String summary,
        List<String> keyPoints,
        boolean insufficientContext,
        List<AiCitationResponse> citations) {
}
