package com.stacc.backend.lms.gradebook;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.quiz.LmsQuiz;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * What the gradebook itself keeps about a column: a manual item's title and maximum marks, or, for an assignment or
 * quiz column, only that it exists once its grades are published. Publishing records the moment once and is never
 * undone.
 */
@Entity
@Table(name = "lms_grade_items")
public class LmsGradeItem {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final BigDecimal MAX_MARKS = new BigDecimal("999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20, updatable = false)
    private GradeItemType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lms_assignment_id", updatable = false)
    private LmsAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lms_quiz_id", updatable = false)
    private LmsQuiz quiz;

    @Column(length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "max_marks", precision = 8, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdByAccountId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsGradeItem() {
        // Required by JPA.
    }

    private LmsGradeItem(LmsCourse lmsCourse, GradeItemType type, Long createdByAccountId) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        this.type = type;
        this.createdByAccountId = Require.notNull(createdByAccountId, "createdByAccountId");
    }

    public static LmsGradeItem manual(LmsCourse lmsCourse, Long createdByAccountId, String title, BigDecimal maxMarks) {
        LmsGradeItem item = new LmsGradeItem(lmsCourse, GradeItemType.MANUAL, createdByAccountId);
        item.edit(title, maxMarks);
        return item;
    }

    /** The record of an assignment column, made when its grades are first published. */
    public static LmsGradeItem forAssignment(LmsAssignment assignment, Long createdByAccountId) {
        LmsGradeItem item = new LmsGradeItem(assignment.getLmsCourse(), GradeItemType.ASSIGNMENT, createdByAccountId);
        item.assignment = assignment;
        return item;
    }

    /** The record of a quiz column, made when its grades are first published. */
    public static LmsGradeItem forQuiz(LmsQuiz quiz, Long createdByAccountId) {
        LmsGradeItem item = new LmsGradeItem(quiz.getLmsCourse(), GradeItemType.QUIZ, createdByAccountId);
        item.quiz = quiz;
        return item;
    }

    /** Renames a manual item or changes its maximum marks: above 0 and below 1,000,000, with two decimals. */
    public void edit(String title, BigDecimal maxMarks) {
        if (type != GradeItemType.MANUAL) {
            throw new IllegalStateException("Only a manual item has its own title and marks.");
        }
        String validTitle = Require.text(title, "Title", MAX_TITLE_LENGTH);
        Require.notNull(maxMarks, "Maximum marks");
        if (maxMarks.signum() <= 0 || maxMarks.compareTo(MAX_MARKS) > 0 || maxMarks.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(
                    "Maximum marks must be greater than zero and below 1,000,000, with at most 2 decimal places.");
        }
        this.title = validTitle;
        this.maxMarks = maxMarks.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Shows the column's grades to students from now on. Publishing again changes nothing. */
    public void publish(Instant at) {
        if (publishedAt == null) {
            this.publishedAt = Require.notNull(at, "at");
        }
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public Long getId() {
        return id;
    }

    public LmsCourse getLmsCourse() {
        return lmsCourse;
    }

    public GradeItemType getType() {
        return type;
    }

    public LmsAssignment getAssignment() {
        return assignment;
    }

    public LmsQuiz getQuiz() {
        return quiz;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
