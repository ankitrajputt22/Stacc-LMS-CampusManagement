package com.stacc.backend.lms.assignment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
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
 * The definition of a piece of work set in one LMS course: a title, plain-text instructions, a deadline,
 * and the maximum marks. It belongs to the course itself, not to a module or topic. It says what must be
 * done; what students hand in and the marks they get are not part of it.
 *
 * <p>A new assignment is a draft. Publishing shows it to students and records when that first happened.
 * It stays visible after its deadline. An assignment that is no longer wanted is archived, never deleted.
 */
@Entity
@Table(name = "lms_assignments")
public class LmsAssignment {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_INSTRUCTIONS_LENGTH = 20_000;
    public static final int MARK_DECIMAL_PLACES = 2;
    /** The largest value the max_marks column holds, DECIMAL(8,2). */
    public static final BigDecimal MAX_MARKS = new BigDecimal("999999.99");
    /** The range of deadlines accepted: wide enough for any real course, and storable in MySQL. */
    public static final Instant EARLIEST_DUE_AT = Instant.parse("2000-01-01T00:00:00Z");
    public static final Instant LATEST_DUE_AT = Instant.parse("9999-12-31T00:00:00Z");

    /** How assignments are listed everywhere: earliest deadline first, then in the order they were added. */
    public static final Comparator<LmsAssignment> BY_DUE_AT =
            Comparator.comparing(LmsAssignment::getDueAt).thenComparing(LmsAssignment::getId);

    // No cascade: the LMS course is never created or deleted through one of its assignments.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    // Plain text, line breaks kept. It is never treated as HTML.
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String instructions;

    // An absolute moment, stored in UTC. Each browser shows it in its own time zone.
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "max_marks", nullable = false, precision = 8, scale = MARK_DECIMAL_PLACES)
    private BigDecimal maxMarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LmsContentStatus status = LmsContentStatus.DRAFT;

    // When the assignment was first published. It never changes afterwards, not even when the assignment
    // is edited or archived.
    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsAssignment() {
        // Required by JPA.
    }

    public LmsAssignment(LmsCourse lmsCourse, String title, String instructions, Instant dueAt, BigDecimal maxMarks) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        edit(title, instructions, dueAt, maxMarks);
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

    public String getInstructions() {
        return instructions;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    /** The maximum marks, always with two decimal places. */
    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public LmsContentStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isArchived() {
        return status == LmsContentStatus.ARCHIVED;
    }

    /** Whether this deadline can be set: within {@link #EARLIEST_DUE_AT} and {@link #LATEST_DUE_AT}. */
    public static boolean isSupportedDueAt(Instant dueAt) {
        return dueAt != null && !dueAt.isBefore(EARLIEST_DUE_AT) && !dueAt.isAfter(LATEST_DUE_AT);
    }

    /**
     * Changes the definition, as a draft or a correction after publishing, such as a moved deadline. A
     * published assignment stays published and keeps its publication time. An archived one can no longer
     * be changed.
     */
    public void edit(String title, String instructions, Instant dueAt, BigDecimal maxMarks) {
        requireNotArchived();
        String validTitle = Require.text(title, "title", MAX_TITLE_LENGTH);
        String validInstructions = Require.text(instructions, "instructions", MAX_INSTRUCTIONS_LENGTH);
        Instant validDueAt = validDueAt(dueAt);
        BigDecimal validMaxMarks = validMaxMarks(maxMarks);
        this.title = validTitle;
        this.instructions = validInstructions;
        this.dueAt = validDueAt;
        this.maxMarks = validMaxMarks;
    }

    /**
     * Shows the assignment to students. The first publication records the time; publishing an already
     * published assignment changes nothing. An archived assignment cannot be published again.
     */
    public void publish(Instant at) {
        requireNotArchived();
        if (status != LmsContentStatus.PUBLISHED) {
            Require.notNull(at, "at");
            this.status = LmsContentStatus.PUBLISHED;
            if (publishedAt == null) {
                this.publishedAt = at;
            }
        }
    }

    /** Hides the assignment for good and keeps it, with its publication time. Archiving again changes nothing. */
    public void archive() {
        this.status = LmsContentStatus.ARCHIVED;
    }

    private void requireNotArchived() {
        if (isArchived()) {
            throw new IllegalStateException("An archived assignment cannot be changed");
        }
    }

    private static Instant validDueAt(Instant dueAt) {
        Require.notNull(dueAt, "dueAt");
        if (!isSupportedDueAt(dueAt)) {
            throw new IllegalArgumentException("dueAt must be between " + EARLIEST_DUE_AT + " and " + LATEST_DUE_AT);
        }
        return dueAt;
    }

    // Marks may be fractional, such as 12.5. A value with more decimal places than can be stored is
    // rejected, never rounded.
    private static BigDecimal validMaxMarks(BigDecimal maxMarks) {
        Require.notNull(maxMarks, "maxMarks");
        if (maxMarks.signum() <= 0) {
            throw new IllegalArgumentException("maxMarks must be greater than zero");
        }
        if (maxMarks.compareTo(MAX_MARKS) > 0) {
            throw new IllegalArgumentException("maxMarks must be at most " + MAX_MARKS);
        }
        if (maxMarks.stripTrailingZeros().scale() > MARK_DECIMAL_PLACES) {
            throw new IllegalArgumentException("maxMarks must have at most " + MARK_DECIMAL_PLACES + " decimal places");
        }
        return maxMarks.setScale(MARK_DECIMAL_PLACES);
    }
}
