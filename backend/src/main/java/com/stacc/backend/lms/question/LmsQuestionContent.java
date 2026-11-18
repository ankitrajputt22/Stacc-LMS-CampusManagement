package com.stacc.backend.lms.question;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.outcome.BloomLevel;
import com.stacc.backend.lms.outcome.LmsCourseOutcome;

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

    public LmsQuestionContent withOutcome(LmsCourseOutcome chosen) {
        return new LmsQuestionContent(type, text, textFormat, explanation, options, trueFalseAnswer, answerGuidance, topic,
                difficulty, marks, bloomLevel, chosen);
    }

    public record Option(String text, boolean correct) {
    }
}
