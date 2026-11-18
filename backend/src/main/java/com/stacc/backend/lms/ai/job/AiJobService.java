package com.stacc.backend.lms.ai.job;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.ai.AiErrors;
import com.stacc.backend.lms.ai.AiKnowledgeRecords;
import com.stacc.backend.lms.ai.AiKnowledgeRefresher;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.teacher.AiTeacherDocumentService;
import com.stacc.backend.lms.ai.teacher.AiTeacherKnowledge;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiJobService {

    public record JobView(Long jobId, AiJob.Status status, Instant createdAt, Instant startedAt, Instant finishedAt, String resultSummary,
            String errorMessage) {

        static JobView of(AiJob job) {
            return new JobView(job.getId(), job.getStatus(), job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt(), job.getResultSummary(),
                    job.getErrorMessage());
        }
    }

    private final AiJobRepository jobs;
    private final AiKnowledgeSources sources;
    private final AiKnowledgeRefresher refresher;
    private final AiServiceClient client;
    private final AiJobSteps steps;
    private final AiTeacherDocumentService teacherDocuments;
    private final Clock clock;

    public AiJobService(AiJobRepository jobs, AiKnowledgeSources sources, AiKnowledgeRefresher refresher, AiServiceClient client,
            AiJobSteps steps, AiTeacherDocumentService teacherDocuments, Clock clock) {
        this.teacherDocuments = teacherDocuments;
        this.jobs = jobs;
        this.sources = sources;
        this.refresher = refresher;
        this.client = client;
        this.steps = steps;
        this.clock = clock;
    }

    public JobView queueRefresh(Long accountId, Long lmsCourseId) {
        if (!client.isEnabled()) {
            throw AiErrors.unavailable();
        }
        if (!sources.publishedKnowledge(lmsCourseId).hasIndexableContent() && teacherDocuments.knowledge(lmsCourseId).isEmpty()) {
            throw AiErrors.noContent();
        }
        try {
            return JobView.of(steps.queue(new AiJob(AiJob.Kind.REFRESH_INDEX, lmsCourseId, accountId, now())));
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, AiKnowledgeRecords.ALREADY_REFRESHING);
        }
    }

    public Optional<JobView> latestRefresh(Long lmsCourseId) {
        return jobs.findFirstByLmsCourseIdAndKindOrderByIdDesc(lmsCourseId, AiJob.Kind.REFRESH_INDEX).map(JobView::of);
    }

    public boolean runNext() {
        AiJob claimed = steps.claim(now()).orElse(null);
        if (claimed == null) {
            return false;
        }
        String summary = null;
        String error = null;
        try {
            summary = refreshAll(claimed.getLmsCourseId());
        } catch (ApiException refused) {
            error = refused.getMessage();
        } catch (RuntimeException unexpected) {
            error = "The refresh failed. Try again.";
        }
        steps.finish(claimed.getId(), summary, error, now());
        return true;
    }

    private String refreshAll(Long lmsCourseId) {
        StringBuilder summary = new StringBuilder();
        if (sources.publishedKnowledge(lmsCourseId).hasIndexableContent()) {
            AiKnowledgeRefresher.Outcome outcome = refresher.refresh(lmsCourseId);
            summary.append("Indexed ").append(outcome.indexedSourceCount()).append(" sources")
                    .append(outcome.skippedSourceCount() > 0 ? ", skipped " + outcome.skippedSourceCount() + "." : ".");
        }
        AiTeacherKnowledge teacher = teacherDocuments.knowledge(lmsCourseId);
        if (!teacher.isEmpty()) {
            AiKnowledgeRefresher.Outcome outcome = refresher.refreshTeacherDocuments(lmsCourseId, teacher);
            summary.append(summary.isEmpty() ? "" : " ").append("Indexed ").append(outcome.indexedSourceCount())
                    .append(outcome.indexedSourceCount() == 1 ? " teacher document." : " teacher documents.");
        }
        if (summary.isEmpty()) {
            throw AiErrors.noContent();
        }
        return summary.toString();
    }

    public int recoverAfterRestart() {
        return steps.recover(now());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
