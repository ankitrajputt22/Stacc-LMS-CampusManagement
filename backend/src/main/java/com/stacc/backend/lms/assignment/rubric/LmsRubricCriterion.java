package com.stacc.backend.lms.assignment.rubric;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/** One criterion of a rubric, part of it: what is judged, the most points it gives, and optional levels. */
@Entity
@Table(name = "lms_rubric_criteria")
public class LmsRubricCriterion {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 1_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_rubric_id", nullable = false, updatable = false)
    private LmsRubric rubric;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(name = "max_points", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxPoints;

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "lms_rubric_levels", joinColumns = @JoinColumn(name = "lms_rubric_criterion_id"))
    @OrderBy("position")
    private List<LmsRubricLevel> levels = new ArrayList<>();

    protected LmsRubricCriterion() {
        // Required by JPA.
    }

    LmsRubricCriterion(LmsRubric rubric, int position, String title, String description, BigDecimal maxPoints,
            List<LmsRubricLevel> levels) {
        this.rubric = rubric;
        this.position = position;
        this.title = title;
        this.description = description;
        this.maxPoints = maxPoints;
        this.levels.addAll(levels);
    }

    public Long getId() {
        return id;
    }

    public LmsRubric getRubric() {
        return rubric;
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

    public BigDecimal getMaxPoints() {
        return maxPoints;
    }

    public List<LmsRubricLevel> getLevels() {
        return List.copyOf(levels);
    }
}
