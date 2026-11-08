package com.stacc.backend.lms.assignment.rubric;

import java.math.BigDecimal;
import java.util.List;

/** What faculty write for a rubric: its title, an optional description, and its criteria in order. */
public record RubricContent(String title, String description, List<Criterion> criteria) {

    /** One criterion with its maximum points and optional levels, in order. */
    public record Criterion(String title, String description, BigDecimal maxPoints, List<Level> levels) {
    }

    /** One level of a criterion, such as "Excellent", with the points it gives. */
    public record Level(String title, String description, BigDecimal points) {
    }
}
