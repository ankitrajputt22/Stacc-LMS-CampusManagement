package com.stacc.backend.lms.assignment.rubric;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.course.LmsCourse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A rubric of one LMS course: criteria in order, each with its maximum points and optional levels. Its total is the sum
 * of the criteria's maximum points. The criteria are part of the rubric and are replaced together when it is edited;
 * the service refuses that once the rubric has been used for grading. Rubrics are archived, never deleted.
 */
@Entity
@Table(name = "lms_rubrics")
public class LmsRubric {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 5_000;
    public static final int MAX_CRITERIA = 20;
    public static final int MAX_LEVELS = 6;
    public static final BigDecimal MAX_TOTAL = new BigDecimal("999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RubricStatus status = RubricStatus.ACTIVE;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdByAccountId;

    // Part of the rubric: saved, replaced, and removed with it.
    @OneToMany(mappedBy = "rubric", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @OrderBy("position")
    private List<LmsRubricCriterion> criteria = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsRubric() {
        // Required by JPA.
    }

    public LmsRubric(LmsCourse lmsCourse, Long createdByAccountId, RubricContent content) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        this.createdByAccountId = Require.notNull(createdByAccountId, "createdByAccountId");
        write(content);
    }

    /** Replaces the title, description, and criteria. An archived rubric cannot change. */
    public void edit(RubricContent content) {
        if (status == RubricStatus.ARCHIVED) {
            throw new IllegalStateException("This rubric is archived and can no longer be changed.");
        }
        write(content);
    }

    public void archive() {
        this.status = RubricStatus.ARCHIVED;
    }

    public boolean isActive() {
        return status == RubricStatus.ACTIVE;
    }

    /** The sum of the criteria's maximum points. */
    public BigDecimal getTotalPoints() {
        return criteria.stream().map(LmsRubricCriterion::getMaxPoints).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private void write(RubricContent content) {
        Require.notNull(content, "content");
        String validTitle = Require.text(content.title(), "Title", MAX_TITLE_LENGTH);
        String validDescription = Require.optionalText(content.description(), "Description", MAX_DESCRIPTION_LENGTH);
        List<RubricContent.Criterion> given = content.criteria() == null ? List.of() : content.criteria();
        if (given.isEmpty() || given.size() > MAX_CRITERIA) {
            throw new IllegalArgumentException("A rubric needs 1 to " + MAX_CRITERIA + " criteria.");
        }
        List<LmsRubricCriterion> made = new ArrayList<>();
        Set<String> titles = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < given.size(); index++) {
            RubricContent.Criterion criterion = Require.notNull(given.get(index), "Criterion");
            String name = "Criterion " + (index + 1);
            String criterionTitle = Require.text(criterion.title(), name + " title", LmsRubricCriterion.MAX_TITLE_LENGTH);
            if (!titles.add(criterionTitle.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Criteria must have different titles.");
            }
            BigDecimal maxPoints = points(criterion.maxPoints(), name + " maximum points", false);
            List<LmsRubricLevel> levels = levels(name, criterion.levels(), maxPoints);
            total = total.add(maxPoints);
            made.add(new LmsRubricCriterion(this, index + 1, criterionTitle,
                    Require.optionalText(criterion.description(), name + " description", LmsRubricCriterion.MAX_DESCRIPTION_LENGTH),
                    maxPoints, levels));
        }
        if (total.compareTo(MAX_TOTAL) > 0) {
            throw new IllegalArgumentException("The rubric's total must be below 1,000,000 points.");
        }
        this.title = validTitle;
        this.description = validDescription;
        this.criteria.clear();
        this.criteria.addAll(made);
    }

    private static List<LmsRubricLevel> levels(String name, List<RubricContent.Level> given, BigDecimal maxPoints) {
        List<RubricContent.Level> levels = given == null ? List.of() : given;
        if (levels.size() > MAX_LEVELS) {
            throw new IllegalArgumentException(name + " can have at most " + MAX_LEVELS + " levels.");
        }
        List<LmsRubricLevel> made = new ArrayList<>();
        for (int index = 0; index < levels.size(); index++) {
            RubricContent.Level level = Require.notNull(levels.get(index), "Level");
            BigDecimal points = points(level.points(), name + " level points", true);
            if (points.compareTo(maxPoints) > 0) {
                throw new IllegalArgumentException(name + " has a level worth more than its maximum points.");
            }
            made.add(new LmsRubricLevel(index + 1, Require.text(level.title(), name + " level title", LmsRubricLevel.MAX_TITLE_LENGTH),
                    Require.optionalText(level.description(), name + " level description", LmsRubricLevel.MAX_DESCRIPTION_LENGTH),
                    points));
        }
        return made;
    }

    private static BigDecimal points(BigDecimal value, String name, boolean zeroAllowed) {
        Require.notNull(value, name);
        if (value.signum() < 0 || !zeroAllowed && value.signum() == 0 || value.compareTo(MAX_TOTAL) > 0
                || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(name + " must be " + (zeroAllowed ? "0" : "above 0")
                    + " and below 1,000,000, with at most 2 decimal places.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public Long getId() {
        return id;
    }

    public LmsCourse getLmsCourse() {
        return lmsCourse;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public RubricStatus getStatus() {
        return status;
    }

    public List<LmsRubricCriterion> getCriteria() {
        return List.copyOf(criteria);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
