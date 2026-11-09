package com.stacc.backend.lms.ai.api;

import java.util.List;

/**
 * A grounded answer with at least one citation, or, when the published course content does not support an answer,
 * {@code insufficientContext} with no answer and no citations.
 */
public record AiAnswerResponse(String answer, boolean insufficientContext, List<AiCitationResponse> citations) {

    static AiAnswerResponse insufficient() {
        return new AiAnswerResponse(null, true, List.of());
    }
}
