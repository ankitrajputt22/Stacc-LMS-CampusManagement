package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** A course's AI knowledge as faculty see it. It never names the AI service, model, index location, or fingerprint. */
public record AiKnowledgeStatusResponse(
        @Schema(description = "Whether AI learning features are switched on for this Stacc installation.") boolean enabled,
        @Schema(description = "NOT_INDEXED, BUILDING, READY, or FAILED: the latest refresh.") String status,
        @Schema(description = "Published content changed since the last successful refresh.") boolean stale,
        @Schema(description = "When the last successful refresh went live, or null.") Instant indexedAt,
        @Schema(description = "Published topics and study materials the knowledge is built from.") int publishedSourceCount,
        @Schema(description = "A short safe message when the latest refresh failed, otherwise null.") String failureMessage) {
}
