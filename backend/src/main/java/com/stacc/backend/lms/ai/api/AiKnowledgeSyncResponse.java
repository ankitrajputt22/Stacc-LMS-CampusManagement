package com.stacc.backend.lms.ai.api;

import java.time.Instant;

public record AiKnowledgeSyncResponse(String status, int indexedSourceCount, int skippedSourceCount, Instant indexedAt) {
}
