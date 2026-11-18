package com.stacc.backend.lms.ai.api;

import java.util.List;

public record AiAnswerResponse(String answer, boolean insufficientContext, List<AiCitationResponse> citations) {

    static AiAnswerResponse insufficient() {
        return new AiAnswerResponse(null, true, List.of());
    }
}
