package com.stacc.backend.lms.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.ai.AiKnowledgeRefresher;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.AiServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The worker runs a claimed job and records a safe result or the refusal's own message. */
class AiJobServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    private final AiJobRepository jobs = mock(AiJobRepository.class);
    private final AiKnowledgeRefresher refresher = mock(AiKnowledgeRefresher.class);
    private final AiJobService service = new AiJobService(jobs, mock(AiKnowledgeSources.class), refresher, mock(AiServiceClient.class),
            new AiJobSteps(jobs), Clock.fixed(NOW, ZoneOffset.UTC));

    private AiJob queued() {
        AiJob job = new AiJob(AiJob.Kind.REFRESH_INDEX, 3L, 1L, NOW);
        when(jobs.claimNext()).thenReturn(Optional.of(job));
        when(jobs.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(job));
        return job;
    }

    @Test
    void aSuccessfulRefreshIsSummarised() {
        AiJob job = queued();
        when(refresher.refresh(3L)).thenReturn(new AiKnowledgeRefresher.Outcome(4, 1, NOW));
        assertThat(service.runNext()).isTrue();
        assertThat(job.getStatus()).isEqualTo(AiJob.Status.SUCCEEDED);
        assertThat(job.getResultSummary()).isEqualTo("Indexed 4 sources, skipped 1.");
    }

    @Test
    void aRefusedRefreshKeepsItsSafeMessage() {
        AiJob job = queued();
        when(refresher.refresh(3L)).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI is unavailable."));
        service.runNext();
        assertThat(job.getStatus()).isEqualTo(AiJob.Status.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("AI is unavailable.");
    }

    @Test
    void nothingQueuedRunsNothing() {
        when(jobs.claimNext()).thenReturn(Optional.empty());
        assertThat(service.runNext()).isFalse();
    }
}
