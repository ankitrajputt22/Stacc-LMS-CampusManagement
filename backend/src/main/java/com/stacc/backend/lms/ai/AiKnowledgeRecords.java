package com.stacc.backend.lms.ai;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.course.LmsCourseRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short transactions around a knowledge refresh. The refresh itself (reading files and waiting for the AI service)
 * runs outside any transaction, so no database connection or row lock is held while a model works.
 */
@Service
public class AiKnowledgeRecords {

    public static final String ALREADY_REFRESHING = "AI knowledge is already being refreshed for this course.";

    private final AiCourseKnowledgeIndexRepository indexes;
    private final LmsCourseRepository lmsCourses;
    private final AiProperties properties;
    private final Clock clock;

    public AiKnowledgeRecords(AiCourseKnowledgeIndexRepository indexes, LmsCourseRepository lmsCourses,
            AiProperties properties, Clock clock) {
        this.indexes = indexes;
        this.lmsCourses = lmsCourses;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<AiCourseKnowledgeIndex> find(Long lmsCourseId) {
        return indexes.findByLmsCourseId(lmsCourseId);
    }

    /**
     * Marks a refresh as started, under the row lock, unless another one is still running. A refresh marked as
     * building for longer than the building timeout is treated as abandoned and may be started again. A first refresh
     * inserts the row; two first refreshes at once meet the unique key, and the loser sees a conflict.
     */
    @Transactional
    public void claim(Long lmsCourseId) {
        Optional<AiCourseKnowledgeIndex> existing = indexes.findForUpdateByLmsCourseId(lmsCourseId);
        if (existing.isEmpty()) {
            indexes.saveAndFlush(new AiCourseKnowledgeIndex(lmsCourses.getReferenceById(lmsCourseId)));
            return;
        }
        AiCourseKnowledgeIndex index = existing.get();
        Instant abandonedBefore = clock.instant().minus(properties.buildingTimeout());
        if (index.getStatus() == AiKnowledgeIndexStatus.BUILDING && index.getUpdatedAt() != null
                && index.getUpdatedAt().isAfter(abandonedBefore)) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_REFRESHING);
        }
        index.startBuilding();
    }

    @Transactional
    public AiCourseKnowledgeIndex markReady(Long lmsCourseId, String contentFingerprint) {
        AiCourseKnowledgeIndex index = locked(lmsCourseId);
        index.ready(contentFingerprint, clock.instant());
        return index;
    }

    @Transactional
    public void markFailed(Long lmsCourseId, String message) {
        locked(lmsCourseId).failed(message);
    }

    private AiCourseKnowledgeIndex locked(Long lmsCourseId) {
        return indexes.findForUpdateByLmsCourseId(lmsCourseId)
                .orElseThrow(() -> new IllegalStateException("The refresh was not claimed"));
    }
}
