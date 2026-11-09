package com.stacc.backend.lms.question;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.outcome.BloomLevel;
import com.stacc.backend.lms.outcome.LmsCourseOutcome;

/**
 * Everything a faculty member writes about a question, for adding or editing it. The course, author, review status, and
 * practice flag are not part of it.
 *
 * @param options for choice questions the choices, for short-answer questions the accepted answers (all correct);
 *        empty for the other types
 */
public record LmsQuestionContent(
        QuestionType type,
        String text,
        LmsTextFormat textFormat,
        String explanation,
        List<Option> options,
        Boolean trueFalseAnswer,
        String answerGuidance,
        String topic,
        QuestionDifficulty difficulty,
        BigDecimal marks,
        BloomLevel bloomLevel,
        LmsCourseOutcome outcome) {

    /** The same content with the outcome the server looked up. */
    public LmsQuestionContent withOutcome(LmsCourseOutcome chosen) {
        return new LmsQuestionContent(type, text, textFormat, explanation, options, trueFalseAnswer, answerGuidance, topic,
                difficulty, marks, bloomLevel, chosen);
    }

    /** One choice, or one accepted answer. */
    public record Option(String text, boolean correct) {
    }
}
