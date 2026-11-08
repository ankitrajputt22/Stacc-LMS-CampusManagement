package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.lms.assignment.rubric.LmsRubric;
import com.stacc.backend.lms.assignment.rubric.LmsRubricCriterion;
import com.stacc.backend.lms.assignment.rubric.LmsRubricLevel;
import com.stacc.backend.lms.assignment.rubric.RubricContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A rubric with its criteria and levels, for adding or editing one. Plain text throughout. */
public record LmsRubricRequest(
        @Schema(example = "Database design")
        @NotBlank(message = "Title is required")
        @Size(max = LmsRubric.MAX_TITLE_LENGTH, message = "Title must be at most 200 characters")
        String title,
        @Size(max = LmsRubric.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 5,000 characters")
        String description,
        @NotEmpty(message = "Add at least one criterion")
        @Size(max = LmsRubric.MAX_CRITERIA, message = "A rubric can have at most 20 criteria")
        List<@Valid @NotNull(message = "Criteria cannot be empty") CriterionRequest> criteria) {

    public record CriterionRequest(
            @Schema(example = "Normalization")
            @NotBlank(message = "Every criterion needs a title")
            @Size(max = LmsRubricCriterion.MAX_TITLE_LENGTH, message = "Criterion titles must be at most 200 characters")
            String title,
            @Size(max = LmsRubricCriterion.MAX_DESCRIPTION_LENGTH, message = "Criterion descriptions must be at most 1,000 characters")
            String description,
            @Schema(example = "10")
            @NotNull(message = "Every criterion needs maximum points")
            @DecimalMin(value = "0", inclusive = false, message = "Maximum points must be greater than zero")
            @Digits(integer = 6, fraction = 2, message = "Points can have at most 2 decimal places")
            BigDecimal maxPoints,
            @Size(max = LmsRubric.MAX_LEVELS, message = "A criterion can have at most 6 levels")
            List<@Valid @NotNull(message = "Levels cannot be empty") LevelRequest> levels) {
    }

    public record LevelRequest(
            @Schema(example = "Excellent")
            @NotBlank(message = "Every level needs a title")
            @Size(max = LmsRubricLevel.MAX_TITLE_LENGTH, message = "Level titles must be at most 100 characters")
            String title,
            @Size(max = LmsRubricLevel.MAX_DESCRIPTION_LENGTH, message = "Level descriptions must be at most 500 characters")
            String description,
            @Schema(example = "10")
            @NotNull(message = "Every level needs points")
            @DecimalMin(value = "0", message = "Level points cannot be negative")
            @Digits(integer = 6, fraction = 2, message = "Points can have at most 2 decimal places")
            BigDecimal points) {
    }

    RubricContent content() {
        return new RubricContent(title, description, criteria.stream()
                .map(criterion -> new RubricContent.Criterion(criterion.title(), criterion.description(), criterion.maxPoints(),
                        criterion.levels() == null ? List.of() : criterion.levels().stream()
                                .map(level -> new RubricContent.Level(level.title(), level.description(), level.points())).toList()))
                .toList());
    }
}
