package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.outcome.BloomLevel;
import com.stacc.backend.lms.outcome.LmsCourseOutcome;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionReviewStatus;
import com.stacc.backend.lms.question.QuestionStatus;
import com.stacc.backend.lms.question.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;

/** One question of the bank with its answers. Only faculty who teach the course receive it. */
public record LmsQuestionResponse(
        Long id,
        QuestionType type,
        String text,
        LmsTextFormat textFormat,
        String explanation,
        @Schema(description = "Choices with the correct ones marked, or accepted short answers, in order.")
        List<Option> options,
        Boolean trueFalseAnswer,
        String answerGuidance,
        String topic,
        QuestionDifficulty difficulty,
        @Schema(example = "2.00")
        BigDecimal marks,
        BloomLevel bloomLevel,
        @Schema(description = "The aligned outcome, or null.")
        LmsOutcomeResponse outcome,
        @Schema(description = "A new or edited question NEEDS_REVIEW.")
        QuestionReviewStatus reviewStatus,
        @Schema(description = "Only a reviewed, active question can be offered for practice.")
        boolean practiceAllowed,
        QuestionStatus status,
        @Schema(description = "A published or archived quiz uses the question, so it can no longer be edited.")
        boolean inPublishedQuiz,
        Instant updatedAt,
        @Schema(description = "Drafted by AI (v0.14.1); it still needs a teacher's review like any new question.")
        boolean aiGenerated,
        @Schema(description = "For an AI draft, the course sources it was drafted from; otherwise empty.")
        List<Source> sources) {

    public record Option(String text, boolean correct) {
    }

    /** A course source an AI draft was drafted from, as it was then. */
    public record Source(String sourceType, long sourceId, String title, String fileName, Integer pageNumber,
            Integer slideNumber, Long pageId) {
    }

    static LmsQuestionResponse of(LmsQuestion question, boolean inPublishedQuiz) {
        LmsCourseOutcome outcome = question.getOutcome();
        return new LmsQuestionResponse(question.getId(), question.getType(), question.getText(), question.getTextFormat(),
                question.getExplanation(),
                question.getOptions().stream().map(option -> new Option(option.getText(), option.isCorrect())).toList(),
                question.getTrueFalseAnswer(), question.getAnswerGuidance(), question.getTopic(), question.getDifficulty(),
                question.getMarks(), question.getBloomLevel(), outcome == null ? null : LmsOutcomeResponse.of(outcome),
                question.getReviewStatus(), question.isPracticeAllowed(), question.getStatus(), inPublishedQuiz,
                question.getUpdatedAt(), question.isAiGenerated(), question.getSources().stream()
                        .map(source -> new Source(source.getSourceType(), source.getSourceId(), source.getTitle(),
                                source.getFileName(), source.getPageNumber(), source.getSlideNumber(), source.getPageId()))
                        .toList());
    }
}
