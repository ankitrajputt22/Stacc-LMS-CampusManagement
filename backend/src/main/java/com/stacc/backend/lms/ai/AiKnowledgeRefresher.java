package com.stacc.backend.lms.ai;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexResult;
import com.stacc.backend.lms.ai.teacher.AiTeacherKnowledge;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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

    public Outcome refreshTeacherDocuments(Long lmsCourseId, AiTeacherKnowledge teacher) {
        if (!client.isEnabled()) {
            throw AiErrors.unavailable();
        }
        if (teacher.isEmpty()) {
            throw AiErrors.noContent();
        }
        try {
            records.claim(lmsCourseId, AiIndexKind.TEACHER_DOCUMENTS);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, AiKnowledgeRecords.ALREADY_REFRESHING);
        }
        try {
            ReplaceIndexResult result = client.replaceIndex(AiCourseKeys.forTeacherDocuments(lmsCourseId),
                    new ReplaceIndexRequest(teacher.fingerprint(), teacher.indexSources()));
            if (result.indexedSourceCount() == null || result.skippedSourceCount() == null
                    || result.indexedSourceCount() < 1 || !teacher.fingerprint().equals(result.contentFingerprint())) {
                throw new AiServiceException(AiServiceException.Reason.BAD_RESPONSE);
            }
            AiCourseKnowledgeIndex index = records.markReady(lmsCourseId, AiIndexKind.TEACHER_DOCUMENTS,
                    teacher.fingerprint());
            return new Outcome(result.indexedSourceCount(), result.skippedSourceCount(), index.getIndexedAt());
        } catch (AiServiceException exception) {
            records.markFailed(lmsCourseId, AiIndexKind.TEACHER_DOCUMENTS, AiErrors.refreshFailure(exception.reason()));
            throw AiErrors.of(exception, AiErrors.TEACHING_NOT_READY);
        } catch (RuntimeException exception) {
            records.markFailed(lmsCourseId, AiIndexKind.TEACHER_DOCUMENTS, "The refresh failed. Try again.");
            throw exception;
        }
    }
}
