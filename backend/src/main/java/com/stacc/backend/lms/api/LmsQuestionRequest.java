package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.outcome.BloomLevel;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionContent;
import com.stacc.backend.lms.question.LmsQuestionOption;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A question with its answers, for adding or editing one. The course, author, review status, and practice flag are not
 * part of it: they come from the route and the review and practice actions.
 */
public record LmsQuestionRequest(
        @Schema(description = "SINGLE_CHOICE and MULTIPLE_CHOICE take options; TRUE_FALSE takes trueFalseAnswer; "
                + "SHORT_ANSWER takes its accepted answers as options, all correct; DESCRIPTIVE takes optional answerGuidance.")
        @NotNull(message = "Question type is required")
        QuestionType type,

        @Schema(description = "Shown as textFormat says, never as HTML.", example = "Which normal form removes partial dependencies?")
        @NotBlank(message = "Question text is required")
        @Size(max = LmsQuestion.MAX_TEXT_LENGTH, message = "Question text must be at most 20,000 characters")
        String text,

        @Schema(description = "Applies to the question text and the explanation. Left out, a new question is PLAIN and an edited one keeps its format.",
                allowableValues = {"PLAIN", "FORMATTED"})
        LmsTextFormat textFormat,

        @Schema(description = "Optional: why the answer is right, for review and later feedback.")
        @Size(max = LmsQuestion.MAX_EXPLANATION_LENGTH, message = "Explanation must be at most 20,000 characters")
        String explanation,

        @Schema(description = "Choices or accepted answers, in order. Plain text.")
        @Size(max = LmsQuestion.MAX_OPTIONS, message = "A question takes at most 10 options")
        List<@Valid @NotNull(message = "Options cannot be empty") OptionRequest> options,

        @Schema(description = "The answer of a true/false question; left out for every other type.")
        Boolean trueFalseAnswer,

        @Schema(description = "Optional plain-text guidance for marking a descriptive question.")
        @Size(max = LmsQuestion.MAX_GUIDANCE_LENGTH, message = "Answer guidance must be at most 5,000 characters")
        String answerGuidance,

        @Schema(description = "Optional plain-text topic, such as Normalization.", example = "Normalization")
        @Size(max = LmsQuestion.MAX_TOPIC_LENGTH, message = "Topic must be at most 100 characters")
        String topic,

        @NotNull(message = "Difficulty is required")
        QuestionDifficulty difficulty,

        @Schema(description = "Greater than zero, with at most two decimal places.", example = "2")
        @NotNull(message = "Marks are required")
        @DecimalMin(value = "0", inclusive = false, message = "Marks must be greater than zero")
        @Digits(integer = 6, fraction = LmsQuestion.MARK_DECIMAL_PLACES, message = "Marks must be below 1,000,000 with at most 2 decimal places")
        BigDecimal marks,

        @Schema(description = "Optional. It describes the question and decides nothing.")
        BloomLevel bloomLevel,

        @Schema(description = "Optional: an active outcome of the same course. An archived outcome the question already has may stay.")
        Long outcomeId) {

    /** One choice or accepted answer. */
    public record OptionRequest(
            @NotBlank(message = "Option text is required")
            @Size(max = LmsQuestionOption.MAX_TEXT_LENGTH, message = "Options must be at most 500 characters")
            String text,
            @Schema(description = "Whether the option is a correct answer. Accepted short answers are all correct.")
            boolean correct) {
    }

    LmsQuestionContent content() {
        List<LmsQuestionContent.Option> given = options == null ? List.of()
                : options.stream().map(option -> new LmsQuestionContent.Option(option.text(), option.correct())).toList();
        return new LmsQuestionContent(type, text, textFormat, explanation, given, trueFalseAnswer, answerGuidance, topic,
                difficulty, marks, bloomLevel, null);
    }
}
