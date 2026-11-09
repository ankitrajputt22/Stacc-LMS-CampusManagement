package com.stacc.backend.lms.ai;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexResult;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Rebuilds one LMS course's AI knowledge from its published content. Callers check faculty access first.
 *
 * <p>The steps: find the published sources and their fingerprint, mark the refresh as building, read the readable
 * study-material files, send everything to the AI service, which builds a new index and swaps it in atomically, and
 * only then mark the row READY with that fingerprint. If anything fails, the row becomes FAILED with a fixed safe
 * message and the AI service keeps its previous index files untouched.
 */
@Service
public class AiKnowledgeRefresher {

    public record Outcome(int indexedSourceCount, int skippedSourceCount, Instant indexedAt) {
    }

    private final AiKnowledgeSources sources;
    private final AiKnowledgeRecords records;
    private final AiIndexPayloads payloads;
    private final AiServiceClient client;

    AiKnowledgeRefresher(AiKnowledgeSources sources, AiKnowledgeRecords records, AiIndexPayloads payloads,
            AiServiceClient client) {
        this.sources = sources;
        this.records = records;
        this.payloads = payloads;
        this.client = client;
    }

    public Outcome refresh(Long lmsCourseId) {
        if (!client.isEnabled()) {
            throw AiErrors.unavailable();
        }
        AiCourseKnowledge knowledge = sources.publishedKnowledge(lmsCourseId);
        // An empty or unreadable course gets no index at all, rather than a misleading empty one.
        if (!knowledge.hasIndexableContent()) {
            throw AiErrors.noContent();
        }
        try {
            records.claim(lmsCourseId);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, AiKnowledgeRecords.ALREADY_REFRESHING);
        }
        try {
            AiIndexPayloads.Payload payload = payloads.build(knowledge);
            if (payload.sources().isEmpty()) {
                throw new AiServiceException(AiServiceException.Reason.NO_CONTENT);
            }
            ReplaceIndexResult result = client.replaceIndex(AiCourseKeys.forLmsCourse(lmsCourseId),
                    new ReplaceIndexRequest(knowledge.fingerprint(), payload.sources()));
            if (result.indexedSourceCount() == null || result.skippedSourceCount() == null
                    || result.indexedSourceCount() < 1 || !knowledge.fingerprint().equals(result.contentFingerprint())) {
                throw new AiServiceException(AiServiceException.Reason.BAD_RESPONSE);
            }
            AiCourseKnowledgeIndex index = records.markReady(lmsCourseId, knowledge.fingerprint());
            return new Outcome(result.indexedSourceCount(), result.skippedSourceCount() + payload.skipped(),
                    index.getIndexedAt());
        } catch (AiServiceException exception) {
            records.markFailed(lmsCourseId, AiErrors.refreshFailure(exception.reason()));
            throw AiErrors.of(exception, AiErrors.TEACHING_NOT_READY);
        } catch (RuntimeException exception) {
            records.markFailed(lmsCourseId, "The refresh failed. Try again.");
            throw exception;
        }
    }
}
