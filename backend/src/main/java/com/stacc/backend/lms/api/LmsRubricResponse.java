package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.lms.assignment.rubric.LmsRubric;
import com.stacc.backend.lms.assignment.rubric.RubricStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** A rubric with its criteria and levels. */
public record LmsRubricResponse(
        Long id,
        String title,
        String description,
        RubricStatus status,
        @Schema(description = "The sum of the criteria's maximum points.")
        BigDecimal totalPoints,
        @Schema(description = "Used for grading, so its criteria can no longer change.")
        boolean inUse,
        List<Criterion> criteria) {

    public record Criterion(Long id, String title, String description, BigDecimal maxPoints, List<Level> levels) {
    }

    public record Level(String title, String description, BigDecimal points) {
    }

    static LmsRubricResponse of(LmsRubric rubric, boolean inUse) {
        return new LmsRubricResponse(rubric.getId(), rubric.getTitle(), rubric.getDescription(), rubric.getStatus(),
                rubric.getTotalPoints(), inUse, rubric.getCriteria().stream()
                        .map(criterion -> new Criterion(criterion.getId(), criterion.getTitle(), criterion.getDescription(),
                                criterion.getMaxPoints(), criterion.getLevels().stream()
                                        .map(level -> new Level(level.getTitle(), level.getDescription(), level.getPoints())).toList()))
                        .toList());
    }

    /** The assignment's rubric, or null. */
    static LmsRubricResponse ofNullable(LmsRubric rubric) {
        return rubric == null ? null : of(rubric, false);
    }
}
