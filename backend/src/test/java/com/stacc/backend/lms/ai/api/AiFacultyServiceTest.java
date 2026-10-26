package com.stacc.backend.lms.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiCourseKnowledgeIndex;
import com.stacc.backend.lms.ai.AiKnowledgeRecords;
import com.stacc.backend.lms.ai.AiKnowledgeRefresher;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentDraftRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentDraftResult;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentQuestion;
import com.stacc.backend.lms.ai.AiServiceMessages.Citation;
import com.stacc.backend.lms.content.ContentFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class AiFacultyServiceTest {

    private static final long FACULTY_A = 21L;
    private static final long COURSE_A = 7L;
    private static final long COURSE_B = 8L;
    private static final String CURRENT = "c".repeat(64);

    private final LmsFacultyAccessService access = mock(LmsFacultyAccessService.class);
    private final AiKnowledgeSources sources = mock(AiKnowledgeSources.class);
    private final AiKnowledgeRecords records = mock(AiKnowledgeRecords.class);
    private final AiKnowledgeRefresher refresher = mock(AiKnowledgeRefresher.class);
    private final AiServiceClient client = mock(AiServiceClient.class);
    private final AiFacultyService service = new AiFacultyService(access, sources, records, refresher, client);

    private static final Citation CITED = new Citation("TOPIC", 1L, 1L, "t", null, null, null);

    private static AiCourseKnowledgeIndex ready(String fingerprint) {
        AiCourseKnowledgeIndex index = new AiCourseKnowledgeIndex(ContentFixtures.lmsCourse(COURSE_A));
        index.ready(fingerprint, Instant.parse("2026-10-27T09:00:00Z"));
        return index;
    }

    @BeforeEach
    void setUp() {
        when(access.canAccessCourse(FACULTY_A, COURSE_A)).thenReturn(true);
        when(access.canAccessCourse(FACULTY_A, COURSE_B)).thenReturn(false);
        when(client.isEnabled()).thenReturn(true);
        when(sources.publishedKnowledge(COURSE_A)).thenReturn(new AiCourseKnowledge(CURRENT,
                List.of(new AiCourseKnowledge.Topic(1, 10, "Normal forms", "Text")), List.of()));
    }

    @Test
    void statusCoversEveryState() {
        when(records.find(COURSE_A)).thenReturn(Optional.empty());
        assertThat(service.status(FACULTY_A, COURSE_A))
                .isEqualTo(new AiKnowledgeStatusResponse(true, "NOT_INDEXED", false, null, 1, null));

        when(records.find(COURSE_A)).thenReturn(Optional.of(ready(CURRENT)));
        AiKnowledgeStatusResponse fresh = service.status(FACULTY_A, COURSE_A);
        assertThat(fresh.status()).isEqualTo("READY");
        assertThat(fresh.stale()).isFalse();
        assertThat(fresh.indexedAt()).isEqualTo(Instant.parse("2026-10-27T09:00:00Z"));

        when(records.find(COURSE_A)).thenReturn(Optional.of(ready("0".repeat(64))));
        assertThat(service.status(FACULTY_A, COURSE_A).stale()).isTrue();

        AiCourseKnowledgeIndex failed = ready(CURRENT);
        failed.failed("The AI service was unavailable.");
        when(records.find(COURSE_A)).thenReturn(Optional.of(failed));
        AiKnowledgeStatusResponse failure = service.status(FACULTY_A, COURSE_A);
        assertThat(failure.status()).isEqualTo("FAILED");
        assertThat(failure.failureMessage()).isEqualTo("The AI service was unavailable.");

        when(client.isEnabled()).thenReturn(false);
        assertThat(service.status(FACULTY_A, COURSE_A).enabled()).isFalse();
    }

    @Test
    void anotherFacultysCourseIsUnavailableForEveryTool() {
        for (Runnable call : List.<Runnable>of(
                () -> service.status(FACULTY_A, COURSE_B),
                () -> service.sync(FACULTY_A, COURSE_B),
                () -> service.assessmentDraft(FACULTY_A, COURSE_B, request(AiQuestionType.MIXED)))) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(e.getMessage()).isEqualTo("Course not found or unavailable.");
            });
        }
        verifyNoInteractions(sources, records, refresher);
        verify(client, never()).assessmentDraft(anyString(), any());
    }

    @Test
    void syncReportsTheRefreshOutcome() {
        when(refresher.refresh(COURSE_A)).thenReturn(new AiKnowledgeRefresher.Outcome(3, 2, Instant.parse("2026-10-27T10:00:00Z")));
        assertThat(service.sync(FACULTY_A, COURSE_A))
                .isEqualTo(new AiKnowledgeSyncResponse("READY", 3, 2, Instant.parse("2026-10-27T10:00:00Z")));
    }

    private static AiAssessmentDraftRequest request(AiQuestionType type) {
        return new AiAssessmentDraftRequest(2, AiDifficulty.MEDIUM, type, "  Focus on normal forms.  ");
    }

    @Test
    void draftKeepsWellFormedCitedQuestionsOfTheRequestedType() {
        when(records.find(COURSE_A)).thenReturn(Optional.of(ready(CURRENT)));
        when(client.assessmentDraft(anyString(), any())).thenReturn(new AssessmentDraftResult("Quiz on normal forms",
                "Answer every question.", List.of(
                        new AssessmentQuestion("MCQ", "Which form?", List.of("2NF", "3NF"), "3NF", new BigDecimal("2"), List.of(CITED)),
                        new AssessmentQuestion("MCQ", "One option?", List.of("Only"), "Only", BigDecimal.ONE, List.of(CITED)),
                        new AssessmentQuestion("SHORT_ANSWER", "Uncited?", List.of(), "x", BigDecimal.ONE, List.of()),
                        new AssessmentQuestion("SHORT_ANSWER", "Too many marks?", List.of(), "x", new BigDecimal("500"), List.of(CITED)),
                        new AssessmentQuestion("LONG_ANSWER", "Explain 3NF.", List.of("ignored"), "Answer", new BigDecimal("10.555"), List.of(CITED)),
                        new AssessmentQuestion("SHORT_ANSWER", "Over the count?", List.of(), "x", BigDecimal.ONE, List.of(CITED))),
                false));
        AiAssessmentDraftResponse draft = service.assessmentDraft(FACULTY_A, COURSE_A, request(AiQuestionType.MIXED));
        assertThat(draft.insufficientContext()).isFalse();
        assertThat(draft.titleSuggestion()).isEqualTo("Quiz on normal forms");
        assertThat(draft.questions()).extracting(AiAssessmentQuestionResponse::prompt).containsExactly("Which form?", "Explain 3NF.");
        assertThat(draft.questions().get(1).options()).isEmpty();
        assertThat(draft.questions().get(1).suggestedMarks()).isEqualByComparingTo("10.56");

        ArgumentCaptor<AssessmentDraftRequest> sent = ArgumentCaptor.forClass(AssessmentDraftRequest.class);
        verify(client).assessmentDraft(anyString(), sent.capture());
        assertThat(sent.getValue()).isEqualTo(new AssessmentDraftRequest(CURRENT, 2, "MEDIUM", "MIXED", "Focus on normal forms."));
    }

    @Test
    void draftOfAnotherTypeIsDropped() {
        when(records.find(COURSE_A)).thenReturn(Optional.of(ready(CURRENT)));
        when(client.assessmentDraft(anyString(), any())).thenReturn(new AssessmentDraftResult("Quiz", null, List.of(
                new AssessmentQuestion("LONG_ANSWER", "Explain.", List.of(), "x", BigDecimal.TEN, List.of(CITED))), false));
        AiAssessmentDraftResponse draft = service.assessmentDraft(FACULTY_A, COURSE_A, request(AiQuestionType.MCQ));
        assertThat(draft).isEqualTo(new AiAssessmentDraftResponse(null, null, List.of(), true));
    }

    @Test
    void draftNeedsReadyCurrentKnowledge() {
        when(records.find(COURSE_A)).thenReturn(Optional.of(ready("0".repeat(64))));
        assertThatThrownBy(() -> service.assessmentDraft(FACULTY_A, COURSE_A, request(AiQuestionType.MIXED)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("AI knowledge is not ready for this course. Refresh AI knowledge first.");
                });
        verify(client, never()).assessmentDraft(anyString(), any());
    }
}
