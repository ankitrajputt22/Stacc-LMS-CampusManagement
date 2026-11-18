package com.stacc.backend.lms.ai.mastery;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiMasteryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiMasteryService.class);
    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";

    public record TopicMastery(long topicId, String title, Mastery.Result result) {
    }

    private final AiLearningEvidenceRepository evidence;
    private final LmsStudentAccessService studentAccess;
    private final AiKnowledgeSources sources;
    private final Clock clock;

    public AiMasteryService(AiLearningEvidenceRepository evidence, LmsStudentAccessService studentAccess, AiKnowledgeSources sources,
            Clock clock) {
        this.evidence = evidence;
        this.studentAccess = studentAccess;
        this.sources = sources;
        this.clock = clock;
    }

    public void record(Long accountId, Long lmsCourseId, Long topicId, AiLearningEvidence.Kind kind, double score) {
        if (topicId == null) {
            return;
        }
        try {
            evidence.save(new AiLearningEvidence(lmsCourseId, accountId, topicId, kind, BigDecimal.valueOf(score), clock.instant()));
        } catch (RuntimeException failure) {
            LOGGER.warn("Learning evidence could not be recorded: {}", failure.getClass().getSimpleName());
        }
    }

    @Transactional(readOnly = true)
    public List<TopicMastery> view(Long accountId, Long lmsCourseId) {
        requireCourse(accountId, lmsCourseId);
        AiCourseKnowledge knowledge = sources.publishedKnowledge(lmsCourseId);
        Map<Long, List<AiLearningEvidence>> byTopic = new LinkedHashMap<>();
        for (AiLearningEvidence piece : evidence.findTop500ByLmsCourseIdAndUserAccountIdOrderByCreatedAtDescIdDesc(lmsCourseId, accountId)) {
            byTopic.computeIfAbsent(piece.getTopicId(), id -> new ArrayList<>()).add(piece);
        }
        return knowledge.topics().stream()
                .map(topic -> new TopicMastery(topic.id(), topic.title(), Mastery.of(byTopic.getOrDefault(topic.id(), List.of()))))
                .toList();
    }

    @Transactional
    public int clear(Long accountId, Long lmsCourseId) {
        requireCourse(accountId, lmsCourseId);
        return evidence.deleteForStudent(lmsCourseId, accountId);
    }

    private void requireCourse(Long accountId, Long lmsCourseId) {
        if (!studentAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }
}
