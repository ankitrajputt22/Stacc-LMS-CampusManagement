package com.stacc.backend.lms.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.lms.ai.AiServiceException.Reason;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexResult;
import com.stacc.backend.lms.content.LmsMaterialProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

class AiKnowledgeRefresherTest {

    private static final long COURSE = 7L;

    private final AiKnowledgeSources sources = mock(AiKnowledgeSources.class);
    private final AiKnowledgeRecords records = mock(AiKnowledgeRecords.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final AiServiceClient client = mock(AiServiceClient.class);
    private AiKnowledgeRefresher refresher;
    private AiCourseKnowledge knowledge;

    private static AiProperties properties(int maxSources, long maxBytes) {
        return new AiProperties(true, "http://ai:8000", "x".repeat(40), Duration.ofSeconds(1), Duration.ofSeconds(5),
                Duration.ofSeconds(5), maxSources, DataSize.ofBytes(maxBytes), Duration.ofMinutes(30));
    }

    private AiKnowledgeRefresher refresher(int maxSources, long maxBytes) {
        AiIndexPayloads payloads = new AiIndexPayloads(storage, properties(maxSources, maxBytes),
                new LmsMaterialProperties(DataSize.ofKilobytes(1)));
        return new AiKnowledgeRefresher(sources, records, payloads, client);
    }

    @BeforeEach
    void setUp() {
        knowledge = new AiCourseKnowledge("f".repeat(64),
                List.of(new AiCourseKnowledge.Topic(1, 10, "Normal forms", "Third normal form removes transitive dependencies."),
                        new AiCourseKnowledge.Topic(2, 10, "Empty topic", "  ")),
                List.of(new AiCourseKnowledge.Material(20, 1, "Notes", "notes.txt", "text/plain", 12, "lms/courses/7/key-20"),
                        new AiCourseKnowledge.Material(21, 1, "Diagram", "diagram.png", "image/png", 12, "lms/courses/7/key-21"),
                        new AiCourseKnowledge.Material(22, 2, "Big", "big.pdf", "application/pdf", 5000, "lms/courses/7/key-22")));
        when(sources.publishedKnowledge(COURSE)).thenReturn(knowledge);
        when(client.isEnabled()).thenReturn(true);
        when(storage.open("lms/courses/7/key-20"))
                .thenReturn(new ByteArrayInputStream("plain notes!".getBytes(StandardCharsets.UTF_8)));
        AiCourseKnowledgeIndex ready = new AiCourseKnowledgeIndex(com.stacc.backend.lms.content.ContentFixtures.lmsCourse(COURSE));
        ready.ready("f".repeat(64), Instant.parse("2026-10-27T10:00:00Z"));
        when(records.markReady(COURSE, "f".repeat(64))).thenReturn(ready);
        refresher = refresher(100, 1_000_000);
    }

    @Test
    void successfulRefreshClaimsSendsAndMarksReadyInOrder() {
        when(client.replaceIndex(anyString(), any())).thenReturn(new ReplaceIndexResult(2, 0, 3, "f".repeat(64)));
        AiKnowledgeRefresher.Outcome outcome = refresher.refresh(COURSE);

        InOrder order = inOrder(records, client);
        order.verify(records).claim(COURSE);
        ArgumentCaptor<ReplaceIndexRequest> sent = ArgumentCaptor.forClass(ReplaceIndexRequest.class);
        order.verify(client).replaceIndex(eq(AiCourseKeys.forLmsCourse(COURSE)), sent.capture());
        order.verify(records).markReady(COURSE, "f".repeat(64));
        verify(records, never()).markFailed(any(), any());

        // The topic with text and the readable small file; the blank topic, the image, and the oversized file are skipped.
        assertThat(sent.getValue().sources()).extracting(AiServiceMessages.IndexSource::sourceId).containsExactly(1L, 20L);
        assertThat(sent.getValue().contentFingerprint()).isEqualTo("f".repeat(64));
        assertThat(outcome.indexedSourceCount()).isEqualTo(2);
        assertThat(outcome.skippedSourceCount()).isEqualTo(3);
        assertThat(outcome.indexedAt()).isEqualTo(Instant.parse("2026-10-27T10:00:00Z"));
        assertThat(sent.getValue().toString()).doesNotContain("lms/courses/7/key");
    }

    @Test
    void failedRefreshIsRecordedWithASafeMessageAndNeverMarkedReady() {
        when(client.replaceIndex(anyString(), any())).thenThrow(new AiServiceException(Reason.UNAVAILABLE));
        assertThatThrownBy(() -> refresher.refresh(COURSE))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(records).markFailed(COURSE, "The AI service was unavailable.");
        verify(records, never()).markReady(any(), any());
    }

    @Test
    void aMismatchedReplyIsABadResponse() {
        when(client.replaceIndex(anyString(), any())).thenReturn(new ReplaceIndexResult(2, 0, 3, "e".repeat(64)));
        assertThatThrownBy(() -> refresher.refresh(COURSE))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verify(records).markFailed(COURSE, "The AI service could not process the course content.");
    }

    @Test
    void courseWithoutIndexableContentIsRefusedBeforeAnythingStarts() {
        when(sources.publishedKnowledge(COURSE)).thenReturn(new AiCourseKnowledge("f".repeat(64),
                List.of(new AiCourseKnowledge.Topic(2, 10, "Empty topic", null)),
                List.of(new AiCourseKnowledge.Material(21, 2, "Diagram", "diagram.png", "image/png", 12, "k"))));
        assertThatThrownBy(() -> refresher.refresh(COURSE))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("No published course content is available for AI indexing.");
                });
        verifyNoInteractions(records);
        verify(client, never()).replaceIndex(anyString(), any());
    }

    @Test
    void anotherRefreshRunningIsAConflict() {
        doThrow(new ApiException(HttpStatus.CONFLICT, AiKnowledgeRecords.ALREADY_REFRESHING)).when(records).claim(COURSE);
        assertThatThrownBy(() -> refresher.refresh(COURSE))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        doThrow(new DataIntegrityViolationException("duplicate")).when(records).claim(COURSE);
        assertThatThrownBy(() -> refresher.refresh(COURSE))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getMessage()).isEqualTo(AiKnowledgeRecords.ALREADY_REFRESHING));
        verify(client, never()).replaceIndex(anyString(), any());
    }

    @Test
    void disabledAiTouchesNothing() {
        when(client.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> refresher.refresh(COURSE))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(records, storage);
    }

    @Test
    void unreadableFilesAndLimitsAreSkippedAndCounted() {
        when(storage.open("lms/courses/7/key-20")).thenThrow(new RuntimeException("storage down"));
        when(client.replaceIndex(anyString(), any())).thenReturn(new ReplaceIndexResult(1, 0, 1, "f".repeat(64)));
        assertThat(refresher.refresh(COURSE).skippedSourceCount()).isEqualTo(4);

        AiKnowledgeRefresher oneSource = refresher(1, 1_000_000);
        org.mockito.Mockito.doReturn(new ByteArrayInputStream("plain notes!".getBytes(StandardCharsets.UTF_8)))
                .when(storage).open("lms/courses/7/key-20");
        ArgumentCaptor<ReplaceIndexRequest> sent = ArgumentCaptor.forClass(ReplaceIndexRequest.class);
        oneSource.refresh(COURSE);
        verify(client, org.mockito.Mockito.times(2)).replaceIndex(anyString(), sent.capture());
        assertThat(sent.getValue().sources()).hasSize(1);
    }
}
