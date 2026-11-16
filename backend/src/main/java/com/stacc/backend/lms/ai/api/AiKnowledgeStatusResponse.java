package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import com.stacc.backend.lms.ai.job.AiJobService;
import java.time.Instant;

/** A course's AI knowledge as faculty see it. It never names the AI service, model, index location, or fingerprint. */
public record AiKnowledgeStatusResponse(
        @Schema(description = "Whether AI learning features are switched on for this Stacc installation.") boolean enabled,
        @Schema(description = "NOT_INDEXED, BUILDING, READY, or FAILED: the latest refresh.") String status,
        @Schema(description = "Published content changed since the last successful refresh.") boolean stale,
        @Schema(description = "When the last successful refresh went live, or null.") Instant indexedAt,
        @Schema(description = "Published topics and study materials the knowledge is built from.") int publishedSourceCount,
        @Schema(description = "A short safe message when the latest refresh failed, otherwise null.") String failureMessage,
        @Schema(nullable = true, description = "The latest background refresh job, or null when none was ever queued.") AiJobService.JobView job,
        @Schema(description = "Active teacher-only documents of the course (v0.13.5).") int teacherDocumentCount,
        @Schema(description = "Whether AI Tools use the current teacher documents: true when there are none or their "
                + "latest refresh covers them; false until a refresh includes a change.") boolean teacherDocumentsCurrent,
        @Schema(description = "Whether students may use the AI Study Assistant in this course (v0.13.7).") boolean studentAiEnabled) {

    /** The status before teacher documents are added in. */
    AiKnowledgeStatusResponse(boolean enabled, String status, boolean stale, Instant indexedAt, int publishedSourceCount,
            String failureMessage, AiJobService.JobView job) {
        this(enabled, status, stale, indexedAt, publishedSourceCount, failureMessage, job, 0, true, true);
    }

    AiKnowledgeStatusResponse withTeacherDocuments(int count, boolean current) {
        return new AiKnowledgeStatusResponse(enabled, status, stale, indexedAt, publishedSourceCount, failureMessage, job,
                count, current, studentAiEnabled);
    }

    AiKnowledgeStatusResponse withStudentAi(boolean on) {
        return new AiKnowledgeStatusResponse(enabled, status, stale, indexedAt, publishedSourceCount, failureMessage, job,
                teacherDocumentCount, teacherDocumentsCurrent, on);
    }
}
