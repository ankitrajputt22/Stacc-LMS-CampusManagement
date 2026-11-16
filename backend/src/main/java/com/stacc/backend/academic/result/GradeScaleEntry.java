package com.stacc.backend.academic.result;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One grade of the college's scale (reference data, from V144): its points and the lowest percentage for it. */
@Entity
@Table(name = "grade_scale")
public class GradeScaleEntry {

    @Id
    @Column(name = "grade_code", length = 5)
    private String gradeCode;

    @Column(name = "grade_points", nullable = false, precision = 4, scale = 2)
    private BigDecimal gradePoints;

    @Column(name = "min_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal minPercent;

    @Column(nullable = false)
    private boolean passing;

    @Column(nullable = false)
    private int position;

    protected GradeScaleEntry() {
    }

    public String getGradeCode() {
        return gradeCode;
    }

    public BigDecimal getGradePoints() {
        return gradePoints;
    }

    public BigDecimal getMinPercent() {
        return minPercent;
    }

    public boolean isPassing() {
        return passing;
    }

    public int getPosition() {
        return position;
    }
}
