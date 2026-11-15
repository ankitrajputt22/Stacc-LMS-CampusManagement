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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * AI jobs (v0.13.1). Faculty queue a course's knowledge refresh, which answers at once; a worker claims queued jobs
 * one at a time and runs the existing refresher outside any long transaction. Callers check faculty access first. At
 * most one refresh is queued or running per course. Results and errors are short safe texts.
 */
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
    private final Clock clock;

    public AiJobService(AiJobRepository jobs, AiKnowledgeSources sources, AiKnowledgeRefresher refresher, AiServiceClient client,
            AiJobSteps steps, Clock clock) {
        this.jobs = jobs;
        this.sources = sources;
        this.refresher = refresher;
        this.client = client;
        this.steps = steps;
        this.clock = clock;
    }

    /** Queues a refresh of the course's AI knowledge; 409 while one is queued or running, or with nothing to index. */
    public JobView queueRefresh(Long accountId, Long lmsCourseId) {
        if (!client.isEnabled()) {
            throw AiErrors.unavailable();
        }
        if (!sources.publishedKnowledge(lmsCourseId).hasIndexableContent()) {
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

    /** Claims and runs the oldest queued job, if any; answers whether one ran. */
    public boolean runNext() {
        AiJob claimed = steps.claim(now()).orElse(null);
        if (claimed == null) {
            return false;
        }
        String summary = null;
        String error = null;
        try {
            AiKnowledgeRefresher.Outcome outcome = refresher.refresh(claimed.getLmsCourseId());
            summary = "Indexed " + outcome.indexedSourceCount() + " sources" + (outcome.skippedSourceCount() > 0
                    ? ", skipped " + outcome.skippedSourceCount() + "." : ".");
        } catch (ApiException refused) {
            error = refused.getMessage();
        } catch (RuntimeException unexpected) {
            error = "The refresh failed. Try again.";
        }
        steps.finish(claimed.getId(), summary, error, now());
        return true;
    }

    /** Fails jobs a stopped process left running. */
    public int recoverAfterRestart() {
        return steps.recover(now());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
