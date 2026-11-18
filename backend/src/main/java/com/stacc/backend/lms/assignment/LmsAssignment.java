package com.stacc.backend.lms.assignment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.assignment.rubric.LmsRubric;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.outcome.BloomLevel;
import com.stacc.backend.lms.outcome.LmsCourseOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "lms_assignments")
public class LmsAssignment {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_INSTRUCTIONS_LENGTH = 20_000;
    public static final int MARK_DECIMAL_PLACES = 2;
    public static final BigDecimal MAX_MARKS = new BigDecimal("999999.99");
    public static final int MAX_ATTEMPTS = 100;
    public static final String DUE_OUTSIDE_WINDOW =
            "The due date must be between the opening time and the closing time. Change those first.";
    public static final String RUBRIC_TOTAL_FIXED =
            "The maximum marks must equal the rubric's total. Remove the rubric first to change them.";
    public static final Instant EARLIEST_DUE_AT = Instant.parse("2000-01-01T00:00:00Z");
    public static final Instant LATEST_DUE_AT = Instant.parse("9999-12-31T00:00:00Z");

    public static final Comparator<LmsAssignment> BY_DUE_AT =
            Comparator.comparing(LmsAssignment::getDueAt).thenComparing(LmsAssignment::getId);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "instructions_format", nullable = false, length = 20)
    private LmsTextFormat instructionsFormat = LmsTextFormat.PLAIN;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "available_from")
    private Instant availableFrom;

    @Column(name = "available_until")
    private Instant availableUntil;

    @Column(name = "allow_late_submissions", nullable = false)
    private boolean allowLateSubmissions = true;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "max_marks", nullable = false, precision = 8, scale = MARK_DECIMAL_PLACES)
    private BigDecimal maxMarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LmsContentStatus status = LmsContentStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "bloom_level", length = 20)
    private BloomLevel bloomLevel;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "lms_assignment_outcomes",
            joinColumns = @JoinColumn(name = "lms_assignment_id"),
            inverseJoinColumns = @JoinColumn(name = "lms_course_outcome_id"))
    private Set<LmsCourseOutcome> outcomes = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lms_rubric_id")
    private LmsRubric rubric;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsAssignment() {
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

    public static boolean isSupportedDueAt(Instant dueAt) {
        return dueAt != null && !dueAt.isBefore(EARLIEST_DUE_AT) && !dueAt.isAfter(LATEST_DUE_AT);
    }

    public void changeSubmissionRules(Instant opens, Instant closes, boolean allowLate, Integer attempts) {
        requireNotArchived();
        if (opens != null && opens.isAfter(dueAt)) {
            throw new IllegalArgumentException("The opening time cannot be after the due date.");
        }
        if (closes != null && closes.isBefore(dueAt)) {
            throw new IllegalArgumentException("The closing time cannot be before the due date.");
        }
        if (attempts != null && (attempts < 1 || attempts > MAX_ATTEMPTS)) {
            throw new IllegalArgumentException("The attempt limit must be between 1 and " + MAX_ATTEMPTS + ".");
        }
        if (opens != null && !isSupportedDueAt(opens) || closes != null && !isSupportedDueAt(closes)) {
            throw new IllegalArgumentException("Choose times between the years 2000 and 9999.");
        }
        this.availableFrom = opens;
        this.availableUntil = closes;
        this.allowLateSubmissions = allowLate;
        this.maxAttempts = attempts;
    }

    public AssignmentSubmissionWindow submissionWindowAt(Instant now) {
        if (availableFrom != null && now.isBefore(availableFrom)) {
            return AssignmentSubmissionWindow.NOT_OPEN;
        }
        boolean pastDue = now.isAfter(dueAt);
        if (pastDue && !allowLateSubmissions || availableUntil != null && now.isAfter(availableUntil)) {
            return AssignmentSubmissionWindow.CLOSED;
        }
        return AssignmentSubmissionWindow.OPEN;
    }

    public Instant getAvailableFrom() {
        return availableFrom;
    }

    public Instant getAvailableUntil() {
        return availableUntil;
    }

    public boolean isAllowLateSubmissions() {
        return allowLateSubmissions;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public LmsRubric getRubric() {
        return rubric;
    }

    public void useRubric(LmsRubric chosen) {
        requireNotArchived();
        if (chosen != null) {
            if (!chosen.getLmsCourse().getId().equals(lmsCourse.getId())) {
                throw new IllegalArgumentException("A rubric of another course cannot be used.");
            }
            if (chosen.getTotalPoints().compareTo(maxMarks) != 0) {
                throw new IllegalStateException("The rubric's total (" + chosen.getTotalPoints().stripTrailingZeros().toPlainString()
                        + ") must equal the assignment's maximum marks (" + maxMarks.stripTrailingZeros().toPlainString() + ").");
            }
        }
        this.rubric = chosen;
    }

    public LmsTextFormat getInstructionsFormat() {
        return instructionsFormat;
    }

    public void changeInstructionsFormat(LmsTextFormat format) {
        requireNotArchived();
        this.instructionsFormat = Require.notNull(format, "format");
    }

    public void edit(String title, String instructions, Instant dueAt, BigDecimal maxMarks) {
        requireNotArchived();
        String validTitle = Require.text(title, "title", MAX_TITLE_LENGTH);
        String validInstructions = Require.text(instructions, "instructions", MAX_INSTRUCTIONS_LENGTH);
        Instant validDueAt = validDueAt(dueAt);
        BigDecimal validMaxMarks = validMaxMarks(maxMarks);
        if (rubric != null && validMaxMarks.compareTo(rubric.getTotalPoints()) != 0) {
            throw new IllegalStateException(RUBRIC_TOTAL_FIXED);
        }
        if (availableFrom != null && validDueAt.isBefore(availableFrom) || availableUntil != null && validDueAt.isAfter(availableUntil)) {
            throw new IllegalStateException(DUE_OUTSIDE_WINDOW);
        }
        this.title = validTitle;
        this.instructions = validInstructions;
        this.dueAt = validDueAt;
        this.maxMarks = validMaxMarks;
    }

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

    public void archive() {
        this.status = LmsContentStatus.ARCHIVED;
    }

    public BloomLevel getBloomLevel() {
        return bloomLevel;
    }

    public List<LmsCourseOutcome> getOutcomes() {
        return outcomes.stream().sorted(Comparator.comparingInt(LmsCourseOutcome::getNumber)).toList();
    }

    public void align(BloomLevel bloomLevel, Collection<LmsCourseOutcome> outcomes) {
        requireNotArchived();
        for (LmsCourseOutcome outcome : outcomes) {
            if (!outcome.getLmsCourse().getId().equals(lmsCourse.getId())) {
                throw new IllegalArgumentException("An outcome of another course cannot be aligned");
            }
        }
        this.bloomLevel = bloomLevel;
        this.outcomes = new HashSet<>(outcomes);
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
