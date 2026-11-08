package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.lms.quiz.attempt.LmsQuizAttemptService;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An answer to one question: the chosen options' positions for single and multiple choice, trueFalseAnswer for true
 * or false, and text for short answer and descriptive questions. Leave the other parts empty; an empty answer leaves
 * the question unanswered.
 */
public record LmsQuizAnswerRequest(
        @ArraySchema(arraySchema = @Schema(description = "Positions of the chosen options.", example = "[2]"))
        @Size(max = 10, message = "Choose at most 10 options")
        List<@NotNull(message = "Option positions cannot be empty") Integer> selectedOptions,
        Boolean trueFalseAnswer,
        @Schema(description = "Plain text; up to 500 characters for a short answer and 20,000 for a descriptive one.")
        @Size(max = LmsQuizAttemptService.MAX_WRITTEN_ANSWER, message = "The answer must be at most 20,000 characters")
        String text) {

    LmsQuizAttemptService.AnswerInput input() {
        return new LmsQuizAttemptService.AnswerInput(selectedOptions, trueFalseAnswer, text);
    }
}
