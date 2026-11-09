package com.stacc.backend.lms.assignment.submission;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The points a grader gave for one rubric criterion, with an optional comment, and the criterion's title and maximum as
 * they were when graded.
 */
@Embeddable
public class LmsRubricScore {

    public static final int MAX_COMMENT_LENGTH = 2_000;

    @Column(nullable = false)
    private int position;

    @Column(name = "lms_rubric_criterion_id", nullable = false)
    private Long criterionId;

    @Column(name = "criterion_title", nullable = false, length = 200)
    private String criterionTitle;

    @Column(name = "max_points", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxPoints;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal points;

    @Column(columnDefinition = "TEXT")
    private String comment;

    protected LmsRubricScore() {
        // Required by JPA.
    }

    public LmsRubricScore(int position, Long criterionId, String criterionTitle, BigDecimal maxPoints, BigDecimal points,
            String comment) {
        this.position = position;
        this.criterionId = criterionId;
        this.criterionTitle = criterionTitle;
        this.maxPoints = maxPoints;
        this.points = points;
        this.comment = comment;
    }

    public int getPosition() {
        return position;
    }

    public Long getCriterionId() {
        return criterionId;
    }

    public String getCriterionTitle() {
        return criterionTitle;
    }

    public BigDecimal getMaxPoints() {
        return maxPoints;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public String getComment() {
        return comment;
    }
}
