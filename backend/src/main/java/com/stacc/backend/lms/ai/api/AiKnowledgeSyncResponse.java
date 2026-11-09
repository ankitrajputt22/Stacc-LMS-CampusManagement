package com.stacc.backend.lms.ai.api;

import java.time.Instant;

/** The outcome of a successful refresh: how many published sources were indexed and how many were skipped. */
public record AiKnowledgeSyncResponse(String status, int indexedSourceCount, int skippedSourceCount, Instant indexedAt) {
}
