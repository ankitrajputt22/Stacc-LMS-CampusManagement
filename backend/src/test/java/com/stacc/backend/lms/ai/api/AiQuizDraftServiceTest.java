package com.stacc.backend.lms.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionBankService;
import com.stacc.backend.lms.question.LmsQuestionContent;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionType;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizService;
import com.stacc.backend.lms.quiz.LmsQuizSettings;
import com.stacc.backend.lms.quiz.QuizType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class AiQuizDraftServiceTest {

    private static final long FACULTY = 5L;
    private static final long COURSE = 3L;

    private final LmsQuestionBankService bank = mock(LmsQuestionBankService.class);
    private final LmsQuizService quizzes = mock(LmsQuizService.class);
    private final AiQuestionBankDraftService bankDrafts = mock(AiQuestionBankDraftService.class);
    private final AiQuizDraftService service = new AiQuizDraftService(bank, quizzes, bankDrafts);

    private static LmsQuestion question(long id, QuestionType type, QuestionDifficulty difficulty, boolean reviewed, boolean practice) {
        LmsQuestion question = new LmsQuestion(ContentFixtures.lmsCourse(COURSE), FACULTY, new LmsQuestionContent(type, "Q" + id,
                LmsTextFormat.PLAIN, null, type == QuestionType.TRUE_FALSE ? List.of()
                        : List.of(new LmsQuestionContent.Option("A", true), new LmsQuestionContent.Option("B", false)),
                type == QuestionType.TRUE_FALSE ? Boolean.TRUE : null, null, "Keys", difficulty, BigDecimal.ONE, null, null));
        ReflectionTestUtils.setField(question, "id", id);
        if (reviewed) {
            question.markReviewed();
        }
        if (practice) {
            question.allowPractice(true);
        }
        return question;
    }

    @BeforeEach
    void setUp() {
        when(bank.list(FACULTY, COURSE)).thenReturn(List.of(
                question(1, QuestionType.TRUE_FALSE, QuestionDifficulty.EASY, true, true),
                question(2, QuestionType.TRUE_FALSE, QuestionDifficulty.EASY, true, false),
                question(3, QuestionType.TRUE_FALSE, QuestionDifficulty.EASY, false, false),
                question(4, QuestionType.SINGLE_CHOICE, QuestionDifficulty.HARD, true, true)));
        LmsQuiz quiz = mock(LmsQuiz.class);
        when(quiz.getId()).thenReturn(77L);
        when(quizzes.create(eq(FACULTY), eq(COURSE), any())).thenReturn(quiz);
    }

    private static AiQuizDraftRequest request(QuizType type, int count, boolean fill) {
        return new AiQuizDraftRequest("Test", type, count, List.of(QuestionType.TRUE_FALSE), QuestionDifficulty.EASY, " keys ", fill);
    }

    @Test
    void reviewedMatchingBankQuestionsComeFirstAndTheGapIsLeftWhenAiIsNotAllowed() {
        AiQuizDraftResponse response = service.draft(FACULTY, COURSE, request(QuizType.GRADED, 5, false));
        assertThat(response).isEqualTo(new AiQuizDraftResponse(77L, 2, 0, 3, null));
        verify(quizzes).addQuestion(eq(FACULTY), eq(77L), eq(1L), isNull());
        verify(quizzes).addQuestion(eq(FACULTY), eq(77L), eq(2L), isNull());
        verify(quizzes, never()).addQuestion(anyLong(), anyLong(), eq(3L), any());
        verify(bankDrafts, never()).draft(anyLong(), anyLong(), any());
        ArgumentCaptor<LmsQuizSettings> settings = ArgumentCaptor.forClass(LmsQuizSettings.class);
        verify(quizzes).create(eq(FACULTY), eq(COURSE), settings.capture());
        assertThat(settings.getValue().type()).isEqualTo(QuizType.GRADED);
    }

    @Test
    void aPracticeQuizTakesOnlyPracticeQuestionsAndAiFillsTheGap() {
        when(bankDrafts.draft(eq(FACULTY), eq(COURSE), any())).thenReturn(new AiQuestionBankDraftResponse(2, 0, 0, 0, false, List.of(90L, 91L)));
        AiQuizDraftResponse response = service.draft(FACULTY, COURSE, request(QuizType.PRACTICE, 3, true));
        assertThat(response).isEqualTo(new AiQuizDraftResponse(77L, 1, 2, 0, null));
        ArgumentCaptor<AiQuestionBankDraftRequest> asked = ArgumentCaptor.forClass(AiQuestionBankDraftRequest.class);
        verify(bankDrafts).draft(eq(FACULTY), eq(COURSE), asked.capture());
        assertThat(asked.getValue().questionCount()).isEqualTo(2);
        verify(quizzes).addQuestion(eq(FACULTY), eq(77L), eq(90L), isNull());
    }

    @Test
    void anAiRefusalStillMakesTheQuizFromTheBankAndSaysWhy() {
        when(bankDrafts.draft(anyLong(), anyLong(), any())).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI is off."));
        assertThat(service.draft(FACULTY, COURSE, request(QuizType.GRADED, 4, true)).aiNote()).isEqualTo("AI is off.");
    }

    @Test
    void nothingMatchingMakesNoQuiz() {
        AiQuizDraftRequest none = new AiQuizDraftRequest("Test", QuizType.GRADED, 3, List.of(QuestionType.DESCRIPTIVE), null, null, false);
        assertThatThrownBy(() -> service.draft(FACULTY, COURSE, none)).isInstanceOfSatisfying(ApiException.class,
                refused -> assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(quizzes, never()).create(anyLong(), anyLong(), any());
    }
}
