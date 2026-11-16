package com.stacc.backend.lms.ai.api;

import java.util.List;

import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionType;
import com.stacc.backend.lms.quiz.QuizType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What draft quiz to build (v0.14.3): reviewed bank questions first, AI only for the gap when allowed. */
public record AiQuizDraftRequest(
        @Schema(example = "Unit 2 class test") @NotBlank @Size(max = 200) String title,
        @NotNull QuizType type,
        @Schema(description = "From 1 to 30.", example = "10") @NotNull @Min(1) @Max(30) Integer questionCount,
        @Schema(description = "The question types to use, one or more.") @NotEmpty @Size(max = 5) List<@NotNull QuestionType> questionTypes,
        @Schema(description = "Only questions of this difficulty, or any when omitted.", nullable = true) QuestionDifficulty difficulty,
        @Schema(description = "Only bank questions with this topic (case ignored), or any when omitted.", nullable = true)
        @Size(max = 100) String topic,
        @Schema(description = "Let AI draft the questions the bank is short of, into the bank as Needs review (at most 10).")
        boolean fillGapsWithAi) {
}
