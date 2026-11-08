package com.stacc.backend.lms.question;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.lms.content.LmsTextFormat;

/** Question contents for tests. */
public final class QuestionFixtures {

    private QuestionFixtures() {
    }

    public static LmsQuestionContent singleChoice(String text) {
        return new LmsQuestionContent(QuestionType.SINGLE_CHOICE, text, LmsTextFormat.FORMATTED, null,
                List.of(new LmsQuestionContent.Option("First normal form", false),
                        new LmsQuestionContent.Option("Second normal form", true)),
                null, null, "Normalization", QuestionDifficulty.MEDIUM, new BigDecimal("2"), null, null);
    }

    public static LmsQuestionContent of(QuestionType type, List<LmsQuestionContent.Option> options, Boolean trueFalse,
            String guidance) {
        return new LmsQuestionContent(type, "A question", null, null, options, trueFalse, guidance, null,
                QuestionDifficulty.EASY, BigDecimal.ONE, null, null);
    }

    public static LmsQuestionContent.Option option(String text, boolean correct) {
        return new LmsQuestionContent.Option(text, correct);
    }
}
