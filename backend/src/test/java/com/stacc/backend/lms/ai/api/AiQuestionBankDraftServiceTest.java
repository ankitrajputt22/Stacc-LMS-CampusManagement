package com.stacc.backend.lms.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiCourseKnowledgeIndex;
import com.stacc.backend.lms.ai.AiKnowledgeRecords;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.AiServiceClient;
import com.stacc.backend.lms.ai.AiServiceMessages.BankDraftResult;
import com.stacc.backend.lms.ai.AiServiceMessages.BankOption;
import com.stacc.backend.lms.ai.AiServiceMessages.BankQuestion;
import com.stacc.backend.lms.ai.AiServiceMessages.Citation;
import com.stacc.backend.lms.ai.AiServiceMessages.QualityChecks;
import com.stacc.backend.lms.ai.control.AiLimitProperties;
import com.stacc.backend.lms.ai.control.AiRequestGate;
import com.stacc.backend.lms.ai.control.AiRequestLimiter;
import com.stacc.backend.lms.ai.control.AiRequestLogRepository;
import com.stacc.backend.lms.ai.teacher.AiTeacherDocumentService;
import com.stacc.backend.lms.ai.teacher.AiTeacherKnowledge;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionBankService;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionReviewStatus;
import com.stacc.backend.lms.question.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class AiQuestionBankDraftServiceTest {

    private static final long FACULTY = 5L;
    private static final long COURSE = 3L;
    private static final String CURRENT = "c".repeat(64);
    private static final Citation TOPIC_1 = new Citation("TOPIC", 1L, 1L, "x", null, null, null);

    private final LmsFacultyAccessService access = mock(LmsFacultyAccessService.class);
    private final AiKnowledgeSources sources = mock(AiKnowledgeSources.class);
    private final AiKnowledgeRecords records = mock(AiKnowledgeRecords.class);
    private final AiServiceClient client = mock(AiServiceClient.class);
    private final AiTeacherDocumentService teacherDocuments = mock(AiTeacherDocumentService.class);
    private final LmsQuestionBankService bank = mock(LmsQuestionBankService.class);
    private final AiQuestionBankDraftService service = new AiQuestionBankDraftService(access, sources, records, client,
            teacherDocuments, bank, new AiRequestGate(new AiRequestLimiter(new AiLimitProperties(4, Duration.ofSeconds(5), null)),
                    mock(AiRequestLogRepository.class), client, Clock.systemUTC()));

    @BeforeEach
    void setUp() {
        when(access.canAccessCourse(FACULTY, COURSE)).thenReturn(true);
        when(client.isEnabled()).thenReturn(true);
        when(sources.publishedKnowledge(COURSE)).thenReturn(new AiCourseKnowledge(CURRENT,
                List.of(new AiCourseKnowledge.Topic(1, 10, "Normal forms", "Text")), List.of()));
        AiCourseKnowledgeIndex ready = new AiCourseKnowledgeIndex(ContentFixtures.lmsCourse(COURSE));
        ready.ready(CURRENT, Instant.EPOCH);
        when(records.find(COURSE)).thenReturn(Optional.of(ready));
        when(teacherDocuments.knowledge(COURSE)).thenReturn(new AiTeacherKnowledge("0".repeat(64), List.of()));
        when(bank.forQualityChecks(COURSE, AiQuestionBankDraftService.MAX_EXISTING)).thenReturn(List.of(
                new LmsQuestionBankService.CheckedQuestion(41L, "Which form is stricter?", true)));
        when(bank.addAiDrafts(anyLong(), anyLong(), any())).thenAnswer(invocation -> {
            List<LmsQuestionBankService.AiDraft> drafts = invocation.getArgument(2);
            return drafts.stream().map(draft -> LmsQuestion.aiDraft(ContentFixtures.lmsCourse(COURSE), FACULTY, draft.content(),
                    draft.sources())).toList();
        });
    }

    private static AiQuestionBankDraftRequest request(QuestionType... types) {
        return new AiQuestionBankDraftRequest(3, List.of(types), QuestionDifficulty.MEDIUM, null, " Focus on 3NF ");
    }

    @Test
    void citedQuestionsOfTheChosenTypesAreAddedForReviewWithTheirSources() {
        when(client.questionBankDraft(anyString(), any())).thenReturn(new BankDraftResult(List.of(
                new BankQuestion("SINGLE_CHOICE", "Which removes transitive dependencies?",
                        List.of(new BankOption("3NF", true), new BankOption("1NF", false)), null, List.of(), null, "Because.",
                        new BigDecimal("2.555"), List.of(TOPIC_1),
                        new QualityChecks(41L, true, false, "HARD", "APPLY")),
                new BankQuestion("SHORT_ANSWER", "Name the form.", List.of(), null, List.of("3NF", " "), null, null,
                        BigDecimal.ONE, List.of(TOPIC_1), new QualityChecks(999L, true, true, "IMPOSSIBLE", null)),
                new BankQuestion("DESCRIPTIVE", "Uncited?", List.of(), null, List.of(), "Guidance", null, BigDecimal.ONE, List.of(), null),
                new BankQuestion("TRUE_FALSE", "Not asked for.", List.of(), true, List.of(), null, null, BigDecimal.ONE, List.of(TOPIC_1), null)),
                1, 2, false));

        AiQuestionBankDraftResponse response = service.draft(FACULTY, COURSE, request(QuestionType.SINGLE_CHOICE, QuestionType.SHORT_ANSWER,
                QuestionType.DESCRIPTIVE));

        assertThat(response.added()).isEqualTo(2);
        assertThat(response.droppedForKeyMismatch()).isEqualTo(1);
        assertThat(response.droppedAsRepeats()).isEqualTo(2);
        assertThat(response.leftOut()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LmsQuestionBankService.AiDraft>> added = ArgumentCaptor.forClass(List.class);
        verify(bank).addAiDrafts(eq(FACULTY), eq(COURSE), added.capture());
        LmsQuestion choice = LmsQuestion.aiDraft(ContentFixtures.lmsCourse(COURSE), FACULTY, added.getValue().get(0).content(),
                added.getValue().get(0).sources());
        assertThat(choice.isAiGenerated()).isTrue();
        assertThat(choice.getReviewStatus()).isEqualTo(QuestionReviewStatus.NEEDS_REVIEW);
        assertThat(choice.isPracticeAllowed()).isFalse();
        assertThat(choice.getMarks()).isEqualByComparingTo("2.56");
        assertThat(choice.getSources()).extracting(source -> source.getSourceType() + ":" + source.getSourceId() + ":" + source.getTitle()
                + ":" + source.getPageId()).containsExactly("TOPIC:1:Normal forms:1");
        assertThat(added.getValue().get(1).content().options()).extracting(LmsQuestionContentOptionText::of).containsExactly("3NF");
        assertThat(added.getValue().get(0).checks().getDuplicateOfQuestionId()).isEqualTo(41L);
        assertThat(added.getValue().get(0).checks().isInPreviousPaper()).isTrue();
        assertThat(added.getValue().get(0).checks().getSuggestedDifficulty()).isEqualTo(QuestionDifficulty.HARD);
        assertThat(added.getValue().get(1).checks().getDuplicateOfQuestionId()).isNull();
        assertThat(added.getValue().get(1).checks().isInPreviousPaper()).isFalse();
        assertThat(added.getValue().get(1).checks().isOutOfSyllabus()).isTrue();
        assertThat(added.getValue().get(1).checks().getSuggestedDifficulty()).isNull();
        ArgumentCaptor<com.stacc.backend.lms.ai.AiServiceMessages.BankDraftRequest> sentRequest =
                ArgumentCaptor.forClass(com.stacc.backend.lms.ai.AiServiceMessages.BankDraftRequest.class);
        verify(client).questionBankDraft(anyString(), sentRequest.capture());
        assertThat(sentRequest.getValue().existingQuestions()).containsExactly(
                new com.stacc.backend.lms.ai.AiServiceMessages.ExistingQuestion(41L, "Which form is stricter?", true));
    }

    @Test
    void draftsNeedCurrentKnowledgeAndAccess() {
        when(records.find(COURSE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.draft(FACULTY, COURSE, request(QuestionType.TRUE_FALSE)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.draft(6L, COURSE, request(QuestionType.TRUE_FALSE)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(client, never()).questionBankDraft(anyString(), any());
        verify(bank, never()).addAiDrafts(anyLong(), anyLong(), any());
    }

    private static final class LmsQuestionContentOptionText {
        static String of(com.stacc.backend.lms.question.LmsQuestionContent.Option option) {
            return option.text();
        }
    }
}
