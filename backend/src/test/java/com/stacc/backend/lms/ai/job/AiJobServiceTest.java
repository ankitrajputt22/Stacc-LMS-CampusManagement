package com.stacc.backend.lms.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiKnowledgeRefresher;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.teacher.AiTeacherDocumentService;
import com.stacc.backend.lms.ai.teacher.AiTeacherKnowledge;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The worker runs a claimed job and records a safe result or the refusal's own message. */
class AiJobServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    private final AiJobRepository jobs = mock(AiJobRepository.class);
    private final AiKnowledgeRefresher refresher = mock(AiKnowledgeRefresher.class);
    private final AiKnowledgeSources sources = mock(AiKnowledgeSources.class);
    private final AiTeacherDocumentService teacherDocuments = mock(AiTeacherDocumentService.class);
    private final AiJobService service = new AiJobService(jobs, sources, refresher, mock(AiServiceClient.class),
            new AiJobSteps(jobs), teacherDocuments, Clock.fixed(NOW, ZoneOffset.UTC));
    private final AiTeacherKnowledge noTeacherDocuments = new AiTeacherKnowledge("0".repeat(64), List.of());

    private AiJob queued() {
        when(sources.publishedKnowledge(3L)).thenReturn(new AiCourseKnowledge("f".repeat(64),
                List.of(new AiCourseKnowledge.Topic(1L, 1L, "Topic", "Text")), List.of()));
        when(teacherDocuments.knowledge(3L)).thenReturn(noTeacherDocuments);
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
    void teacherDocumentsAreRefreshedTooAndSummarised() {
        AiJob job = queued();
        AiTeacherKnowledge teacher = new AiTeacherKnowledge("e".repeat(64),
                List.of(new AiTeacherKnowledge.Document(9L, "Exam focus", "Normal forms")));
        when(teacherDocuments.knowledge(3L)).thenReturn(teacher);
        when(refresher.refresh(3L)).thenReturn(new AiKnowledgeRefresher.Outcome(4, 0, NOW));
        when(refresher.refreshTeacherDocuments(3L, teacher)).thenReturn(new AiKnowledgeRefresher.Outcome(1, 0, NOW));
        service.runNext();
        assertThat(job.getStatus()).isEqualTo(AiJob.Status.SUCCEEDED);
        assertThat(job.getResultSummary()).isEqualTo("Indexed 4 sources. Indexed 1 teacher document.");
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
