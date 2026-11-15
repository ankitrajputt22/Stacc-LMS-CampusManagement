package com.stacc.backend.lms.ai.control;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.stacc.backend.lms.ai.AiCourseKnowledgeIndexRepository;
import com.stacc.backend.lms.ai.AiIndexKind;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.job.AiJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI health for admins (v0.13.7): whether AI is on and the AI service answers, index and job states, requests running
 * and waiting now, and the last 24 hours of the request log by outcome and kind. Statuses and numbers only: no
 * address, setting, course, person, or content. Worked out live; nothing is stored.
 */
@Service
public class AiHealthService {

    public record AiHealth(boolean enabled, boolean reachable, Map<String, Long> courseIndexes, Map<String, Long> teacherIndexes,
            Map<String, Long> jobs, int running, int waiting, Map<String, Long> requestsByOutcome, Map<String, Long> requestsByKind,
            Long averageMillis, long coursesWithStudentAiOff) {
    }

    private static final Duration WINDOW = Duration.ofHours(24);

    private final AiServiceClient client;
    private final AiCourseKnowledgeIndexRepository indexes;
    private final AiJobRepository jobs;
    private final AiRequestLogRepository log;
    private final AiCourseControlRepository controls;
    private final AiRequestLimiter limiter;
    private final Clock clock;

    public AiHealthService(AiServiceClient client, AiCourseKnowledgeIndexRepository indexes, AiJobRepository jobs,
            AiRequestLogRepository log, AiCourseControlRepository controls, AiRequestLimiter limiter, Clock clock) {
        this.client = client;
        this.indexes = indexes;
        this.jobs = jobs;
        this.log = log;
        this.controls = controls;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AiHealth now() {
        var since = clock.instant().minus(WINDOW);
        List<Object[]> outcomes = log.countByOutcomeSince(since);
        Object[] succeeded = outcomes.stream().filter(row -> "SUCCEEDED".equals(row[0])).findFirst().orElse(null);
        Long average = succeeded == null || succeeded[2] == null ? null : Math.round(((Number) succeeded[2]).doubleValue());
        return new AiHealth(client.isEnabled(), client.isEnabled() && client.isReachable(),
                counts(indexes.countByStatus(AiIndexKind.COURSE_CONTENT)), counts(indexes.countByStatus(AiIndexKind.TEACHER_DOCUMENTS)),
                counts(jobs.countByStatusSince(since)), limiter.running(), limiter.waiting(), counts(outcomes),
                counts(log.countByKindSince(since)), average, controls.countTurnedOff());
    }

    private static Map<String, Long> counts(List<Object[]> rows) {
        Map<String, Long> result = new TreeMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }
}
