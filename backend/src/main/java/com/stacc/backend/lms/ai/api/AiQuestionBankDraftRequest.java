package com.stacc.backend.lms.ai.api;

import java.util.List;

import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** What to draft into the question bank (v0.14.1). Nothing else: the course and teacher come from the route and token. */
public record AiQuestionBankDraftRequest(
        @Schema(description = "From 1 to 10.", example = "5") @NotNull @Min(1) @Max(10) Integer questionCount,
        @Schema(description = "The question types to draft, one or more.") @NotEmpty @Size(max = 5) List<@NotNull QuestionType> questionTypes,
        @NotNull QuestionDifficulty difficulty,
        @Schema(description = "A published topic of the course, or omitted for the whole course.") @Positive Long topicId,
        @Schema(description = "Guidance on focus, up to 500 characters.") @Size(max = 500) String additionalInstruction) {
}
