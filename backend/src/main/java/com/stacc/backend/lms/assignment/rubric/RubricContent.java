package com.stacc.backend.lms.assignment.rubric;

import java.math.BigDecimal;
import java.util.List;

public record RubricContent(String title, String description, List<Criterion> criteria) {

    public record Criterion(String title, String description, BigDecimal maxPoints, List<Level> levels) {
    }

    public record Level(String title, String description, BigDecimal points) {
    }
}
