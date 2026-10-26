package com.stacc.backend.lms.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.ai.AiCourseKeys;
import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiCourseKnowledgeIndex;
import com.stacc.backend.lms.ai.AiKnowledgeRecords;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.AiServiceException;
import com.stacc.backend.lms.ai.AiServiceMessages.AskRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.AskResult;
import com.stacc.backend.lms.ai.AiServiceMessages.Citation;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeQuestion;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeResult;
import com.stacc.backend.lms.ai.AiServiceMessages.SummaryResult;
import com.stacc.backend.lms.content.ContentFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class AiStudyServiceTest {

    private static final long STUDENT_A = 11L;
    private static final long STUDENT_B = 12L;
    private static final long COURSE = 7L;
    private static final String CURRENT = "c".repeat(64);
    private static final String OLD = "0".repeat(64);

    private final LmsStudentAccessService access = mock(LmsStudentAccessService.class);
    private final AiKnowledgeSources sources = mock(AiKnowledgeSources.class);
    private final AiKnowledgeRecords records = mock(AiKnowledgeRecords.class);
    private final AiServiceClient client = mock(AiServiceClient.class);
    private final AiStudyService service = new AiStudyService(access, sources, records, client);

    private static Citation topicCitation(long id) {
        return new Citation("TOPIC", id, id, "From the AI service", null, null, null);
    }

    private static Citation materialCitation(long id, Integer page) {
        return new Citation("STUDY_MATERIAL", id, 1L, "From the AI service", "ignored.pdf", page, null);
    }

    private static AiCourseKnowledgeIndex indexOf(String fingerprint) {
        AiCourseKnowledgeIndex index = new AiCourseKnowledgeIndex(ContentFixtures.lmsCourse(COURSE));
        index.ready(fingerprint, Instant.parse("2026-10-27T09:00:00Z"));
        return index;
    }

    @BeforeEach
    void setUp() {
        when(access.canAccessCourse(STUDENT_A, COURSE)).thenReturn(true);
        when(access.canAccessCourse(STUDENT_B, COURSE)).thenReturn(false);
        when(client.isEnabled()).thenReturn(true);
        when(sources.publishedKnowledge(COURSE)).thenReturn(new AiCourseKnowledge(CURRENT,
                List.of(new AiCourseKnowledge.Topic(1, 10, "Database Normalization", "Normal forms...")),
                List.of(new AiCourseKnowledge.Material(20, 1, "Normalization notes", "normalization-notes.pdf",
                        "application/pdf", 100, "lms/courses/7/topics/1/secret-key"))));
        when(records.find(COURSE)).thenReturn(Optional.of(indexOf(CURRENT)));
    }

    @Test
    void groundedAnswerKeepsOnlyCitationsOfCurrentPublishedSources() {
        when(client.ask(anyString(), any())).thenReturn(new AskResult("Normalization reduces redundancy.", false, List.of(
                topicCitation(1), materialCitation(20, 4), topicCitation(2), materialCitation(21, 1), materialCitation(20, 4))));
        AiAnswerResponse answer = service.ask(STUDENT_A, COURSE, new AiAskRequest("  What does normalization reduce?  "));

        assertThat(answer.insufficientContext()).isFalse();
        assertThat(answer.answer()).isEqualTo("Normalization reduces redundancy.");
        assertThat(answer.citations()).containsExactly(
                new AiCitationResponse("TOPIC", 1, "Database Normalization", null, null, null),
                new AiCitationResponse("STUDY_MATERIAL", 20, "Normalization notes", "normalization-notes.pdf", 4, null));
        assertThat(answer.toString()).doesNotContain("secret-key", "ignored.pdf", "From the AI service");

        ArgumentCaptor<AskRequest> sent = ArgumentCaptor.forClass(AskRequest.class);
        verify(client).ask(eq(AiCourseKeys.forLmsCourse(COURSE)), sent.capture());
        assertThat(sent.getValue()).isEqualTo(new AskRequest(CURRENT, "What does normalization reduce?"));
    }

    @Test
    void studentWithoutAccessGetsTheSame404AndNothingIsAsked() {
        assertThatThrownBy(() -> service.ask(STUDENT_B, COURSE, new AiAskRequest("What is a key?")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Course not found or unavailable.");
                });
        verifyNoInteractions(sources, records);
        verify(client, never()).ask(anyString(), any());
    }

    @Test
    void answerWithoutAValidCitationIsInsufficientContext() {
        when(client.ask(anyString(), any())).thenReturn(new AskResult("Invented.", false, List.of(topicCitation(99))));
        assertThat(service.ask(STUDENT_A, COURSE, new AiAskRequest("What is a key?")))
                .isEqualTo(new AiAnswerResponse(null, true, List.of()));
        when(client.ask(anyString(), any())).thenReturn(new AskResult(null, true, List.of()));
        assertThat(service.ask(STUDENT_A, COURSE, new AiAskRequest("Who won the cup?")).insufficientContext()).isTrue();
    }

    @Test
    void staleNotIndexedOrFailedKnowledgeIsNotReady() {
        when(records.find(COURSE)).thenReturn(Optional.of(indexOf(OLD)));
        assertNotReady();
        when(records.find(COURSE)).thenReturn(Optional.empty());
        assertNotReady();
        AiCourseKnowledgeIndex failed = indexOf(CURRENT);
        failed.failed("The AI service was unavailable.");
        when(records.find(COURSE)).thenReturn(Optional.of(failed));
        assertNotReady();
        verify(client, never()).ask(anyString(), any());
    }

    private void assertNotReady() {
        assertThatThrownBy(() -> service.ask(STUDENT_A, COURSE, new AiAskRequest("What is a key?")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("AI study content is not ready for this course yet.");
                });
    }

    @Test
    void disabledOrUnavailableAiIs503() {
        when(client.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.ask(STUDENT_A, COURSE, new AiAskRequest("What is a key?")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).isEqualTo("AI learning features are temporarily unavailable.");
                });
        when(client.isEnabled()).thenReturn(true);
        when(client.ask(anyString(), any())).thenThrow(new AiServiceException(AiServiceException.Reason.UNAVAILABLE));
        assertThatThrownBy(() -> service.ask(STUDENT_A, COURSE, new AiAskRequest("What is a key?")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void questionsAreTrimmedAndBounded() {
        for (String question : List.of(" a ", "  ", "x".repeat(1001))) {
            assertThatThrownBy(() -> service.ask(STUDENT_A, COURSE, new AiAskRequest(question)))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(client);
    }

    @Test
    void summaryOnlyForATopicTheStudentCanSeeInThisCourse() {
        when(client.summarize(anyString(), any())).thenReturn(new SummaryResult("Short summary.",
                List.of("Point one", " ", "Point two"), false, List.of(topicCitation(1))));
        AiTopicSummaryResponse summary = service.summarize(STUDENT_A, COURSE, 1L);
        assertThat(summary.summary()).isEqualTo("Short summary.");
        assertThat(summary.keyPoints()).containsExactly("Point one", "Point two");
        assertThat(summary.topicTitle()).isEqualTo("Database Normalization");

        // A draft, archived, or other course's topic is not in the published knowledge: the same 404.
        assertThatThrownBy(() -> service.summarize(STUDENT_A, COURSE, 2L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Topic not found or unavailable.");
                });
        verify(client, org.mockito.Mockito.times(1)).summarize(anyString(), any());
    }

    @Test
    void practiceKeepsCitedQuestionsUpToTheCount() {
        when(client.practice(anyString(), any())).thenReturn(new PracticeResult(List.of(
                new PracticeQuestion("Q1?", "A1", List.of(topicCitation(1))),
                new PracticeQuestion("Uncited?", "A", List.of()),
                new PracticeQuestion("Q2?", "A2", List.of(materialCitation(20, null))),
                new PracticeQuestion("Q3?", "A3", List.of(topicCitation(1)))), false));
        AiPracticeResponse practice = service.practice(STUDENT_A, COURSE, new AiPracticeRequest(2, 1L));
        assertThat(practice.questions()).extracting(AiPracticeQuestionResponse::question).containsExactly("Q1?", "Q2?");

        ArgumentCaptor<PracticeRequest> sent = ArgumentCaptor.forClass(PracticeRequest.class);
        verify(client).practice(anyString(), sent.capture());
        assertThat(sent.getValue()).isEqualTo(new PracticeRequest(CURRENT, 2, 1L));

        assertThat(new AiPracticeRequest(null, null).questionCountOrDefault()).isEqualTo(5);
    }
}
