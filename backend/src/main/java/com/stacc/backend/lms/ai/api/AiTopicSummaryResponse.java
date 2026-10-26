package com.stacc.backend.lms.ai.api;

import java.util.List;

/** A short summary of one published topic, from its own text and study materials, with key points and citations. */
public record AiTopicSummaryResponse(
        long topicId,
        String topicTitle,
        String summary,
        List<String> keyPoints,
        boolean insufficientContext,
        List<AiCitationResponse> citations) {
}
