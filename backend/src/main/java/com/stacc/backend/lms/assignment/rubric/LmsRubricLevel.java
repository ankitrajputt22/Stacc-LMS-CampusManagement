package com.stacc.backend.lms.assignment.rubric;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One level of a criterion, part of it: a short title, an optional description, and its points. */
@Embeddable
public class LmsRubricLevel {

    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal points;

    protected LmsRubricLevel() {
        // Required by JPA.
    }

    LmsRubricLevel(int position, String title, String description, BigDecimal points) {
        this.position = position;
        this.title = title;
        this.description = description;
        this.points = points;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPoints() {
        return points;
    }
}
