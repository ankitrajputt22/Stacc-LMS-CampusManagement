package com.stacc.backend.lms.ai.api;

import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.paper.LmsExamBlueprintRow;
import com.stacc.backend.lms.paper.LmsExamPaperService;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionType;
import org.springframework.stereotype.Service;

@Service
public class AiExamShortfallService {

    static final int MAX_PER_ROW = 10;
    private static final List<QuestionType> ANY_TYPE = List.of(QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE,
            QuestionType.TRUE_FALSE, QuestionType.SHORT_ANSWER, QuestionType.DESCRIPTIVE);

    public record RowResult(int position, String sectionTitle, int missing, int added, int droppedForKeyMismatch) {
    }

    public record Result(List<RowResult> rows, String note) {

        public int added() {
            return rows.stream().mapToInt(RowResult::added).sum();
        }
    }

    private final LmsExamPaperService papers;
    private final AiQuestionBankDraftService bankDrafts;

    public AiExamShortfallService(LmsExamPaperService papers, AiQuestionBankDraftService bankDrafts) {
        this.papers = papers;
        this.bankDrafts = bankDrafts;
    }

    public Result fill(Long accountId, Long blueprintId) {
        LmsExamPaperService.BlueprintShortfall shortfall = papers.shortfall(accountId, blueprintId);
        List<RowResult> results = new ArrayList<>();
        String note = null;
        for (LmsExamPaperService.RowShortfall row : shortfall.rows()) {
            if (row.missing() == 0) {
                continue;
            }
            LmsExamBlueprintRow blueprintRow = row.row();
            try {
                AiQuestionBankDraftResponse drafted = bankDrafts.draft(accountId, shortfall.lmsCourseId(), new AiQuestionBankDraftRequest(
                        Math.min(row.missing(), MAX_PER_ROW),
                        blueprintRow.getQuestionType() == null ? ANY_TYPE : List.of(blueprintRow.getQuestionType()),
                        blueprintRow.getDifficulty() == null ? QuestionDifficulty.MEDIUM : blueprintRow.getDifficulty(), null, null),
                        new AiQuestionBankDraftService.Labels(blueprintRow.getTopic(), blueprintRow.getBloomLevel(),
                                blueprintRow.getOutcomeId()));
                results.add(new RowResult(blueprintRow.getPosition(), blueprintRow.getSectionTitle(), row.missing(), drafted.added(),
                        drafted.droppedForKeyMismatch()));
            } catch (ApiException refused) {
                if (results.isEmpty()) {
                    throw refused;
                }
                note = refused.getMessage();
                break;
            }
        }
        return new Result(List.copyOf(results), note);
    }
}
