package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.lms.assignment.submission.LmsAssignmentGradingService;
import com.stacc.backend.lms.assignment.submission.LmsRubricScore;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Points for every criterion of the assignment's rubric, with optional comments and overall feedback. */
public record LmsRubricGradeRequest(
        @NotEmpty(message = "Give points for every criterion")
        @Size(max = 20, message = "Too many criteria")
        List<@Valid @NotNull(message = "Scores cannot be empty") Score> scores,
        @Size(max = LmsSubmissionGrade.MAX_FEEDBACK_LENGTH, message = "Feedback must be at most 10,000 characters")
        String feedback) {

    public record Score(
            @NotNull(message = "Every score needs its criterion")
            Long criterionId,
            @Schema(example = "8")
            @NotNull(message = "Every criterion needs points")
            @DecimalMin(value = "0", message = "Points cannot be negative")
            @Digits(integer = 6, fraction = 2, message = "Points can have at most 2 decimal places")
            BigDecimal points,
            @Size(max = LmsRubricScore.MAX_COMMENT_LENGTH, message = "Comments must be at most 2,000 characters")
            String comment) {
    }

    List<LmsAssignmentGradingService.RubricScoreInput> inputs() {
        return scores.stream().map(score -> new LmsAssignmentGradingService.RubricScoreInput(score.criterionId(), score.points(),
                score.comment())).toList();
    }
}
