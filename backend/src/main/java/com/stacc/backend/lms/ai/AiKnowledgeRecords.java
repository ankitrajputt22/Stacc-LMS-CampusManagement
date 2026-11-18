package com.stacc.backend.lms.ai;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.course.LmsCourseRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return find(lmsCourseId, AiIndexKind.COURSE_CONTENT);
    }

    @Transactional(readOnly = true)
    public Optional<AiCourseKnowledgeIndex> find(Long lmsCourseId, AiIndexKind kind) {
        return indexes.findByLmsCourseIdAndKind(lmsCourseId, kind);
    }

    @Transactional
    public void claim(Long lmsCourseId) {
        claim(lmsCourseId, AiIndexKind.COURSE_CONTENT);
    }

    @Transactional
    public void claim(Long lmsCourseId, AiIndexKind kind) {
        Optional<AiCourseKnowledgeIndex> existing = indexes.findForUpdateByLmsCourseIdAndKind(lmsCourseId, kind);
        if (existing.isEmpty()) {
            indexes.saveAndFlush(new AiCourseKnowledgeIndex(lmsCourses.getReferenceById(lmsCourseId), kind));
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
        return markReady(lmsCourseId, AiIndexKind.COURSE_CONTENT, contentFingerprint);
    }

    @Transactional
    public AiCourseKnowledgeIndex markReady(Long lmsCourseId, AiIndexKind kind, String contentFingerprint) {
        AiCourseKnowledgeIndex index = locked(lmsCourseId, kind);
        index.ready(contentFingerprint, clock.instant());
        return index;
    }

    @Transactional
    public void markFailed(Long lmsCourseId, String message) {
        markFailed(lmsCourseId, AiIndexKind.COURSE_CONTENT, message);
    }

    @Transactional
    public void markFailed(Long lmsCourseId, AiIndexKind kind, String message) {
        locked(lmsCourseId, kind).failed(message);
    }

    private AiCourseKnowledgeIndex locked(Long lmsCourseId, AiIndexKind kind) {
        return indexes.findForUpdateByLmsCourseIdAndKind(lmsCourseId, kind)
                .orElseThrow(() -> new IllegalStateException("The refresh was not claimed"));
    }
}
