package com.stacc.backend.lms.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.lms.outcome.BloomLevel;
import com.stacc.backend.lms.paper.LmsExamBlueprintRow;
import com.stacc.backend.lms.paper.LmsExamPaperService;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Only short rows are drafted, with the row's own labels, at most ten each. */
class AiExamShortfallServiceTest {

    @Test
    void shortRowsAreDraftedWithTheirLabels() {
        LmsExamPaperService papers = mock(LmsExamPaperService.class);
        AiQuestionBankDraftService bankDrafts = mock(AiQuestionBankDraftService.class);
        LmsExamBlueprintRow full = new LmsExamBlueprintRow(1, "A", QuestionType.TRUE_FALSE, null, null, null, null, 2, BigDecimal.ONE);
        LmsExamBlueprintRow shortRow = new LmsExamBlueprintRow(2, "B", QuestionType.DESCRIPTIVE, QuestionDifficulty.HARD,
                BloomLevel.APPLY, 31L, "Joins", 14, BigDecimal.TEN);
        when(papers.shortfall(5L, 9L)).thenReturn(new LmsExamPaperService.BlueprintShortfall(3L, List.of(
                new LmsExamPaperService.RowShortfall(full, 4), new LmsExamPaperService.RowShortfall(shortRow, 1))));
        when(bankDrafts.draft(eq(5L), eq(3L), any(), any())).thenReturn(new AiQuestionBankDraftResponse(10, 1, 0, 0, false, List.of()));

        AiExamShortfallService.Result result = new AiExamShortfallService(papers, bankDrafts).fill(5L, 9L);

        assertThat(result.rows()).containsExactly(new AiExamShortfallService.RowResult(2, "B", 13, 10, 1));
        ArgumentCaptor<AiQuestionBankDraftRequest> asked = ArgumentCaptor.forClass(AiQuestionBankDraftRequest.class);
        ArgumentCaptor<AiQuestionBankDraftService.Labels> labels = ArgumentCaptor.forClass(AiQuestionBankDraftService.Labels.class);
        verify(bankDrafts).draft(eq(5L), eq(3L), asked.capture(), labels.capture());
        assertThat(asked.getValue().questionCount()).isEqualTo(10);
        assertThat(asked.getValue().questionTypes()).containsExactly(QuestionType.DESCRIPTIVE);
        assertThat(asked.getValue().difficulty()).isEqualTo(QuestionDifficulty.HARD);
        assertThat(labels.getValue()).isEqualTo(new AiQuestionBankDraftService.Labels("Joins", BloomLevel.APPLY, 31L));
    }
}
