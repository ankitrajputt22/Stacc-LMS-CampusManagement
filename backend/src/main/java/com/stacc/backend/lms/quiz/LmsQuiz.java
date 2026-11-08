package com.stacc.backend.lms.quiz;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
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
 * A quiz of one LMS course: its title, instructions, type, and the rules students take it under. Its questions are
 * {@link LmsQuizQuestion} rows. A new quiz is a draft; publishing records the first publication; an archived quiz is
 * kept and can no longer change. After publishing, the type, shuffling, and negative marking stay as they are, and the
 * questions are fixed; the title, instructions, availability, time limit, and attempt limit may still be corrected.
 */
@Entity
@Table(name = "lms_quizzes")
public class LmsQuiz {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_INSTRUCTIONS_LENGTH = 20_000;
    public static final int MAX_TIME_LIMIT_MINUTES = 600;
    public static final int MAX_ATTEMPTS = 100;
    public static final int MAX_QUESTIONS = 100;
    public static final BigDecimal MAX_NEGATIVE_PERCENT = new BigDecimal("100");
    /** The range of moments accepted: wide enough for any real course, and storable in MySQL. */
    public static final Instant EARLIEST_MOMENT = Instant.parse("2000-01-01T00:00:00Z");
    public static final Instant LATEST_MOMENT = Instant.parse("9999-12-31T00:00:00Z");

    static final String FIXED_AFTER_PUBLISHING =
            "After publishing, the quiz type, shuffling, and negative marking stay as they are.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No cascade: the LMS course is never created or deleted through a quiz.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "instructions_format", nullable = false, length = 10)
    private LmsTextFormat instructionsFormat = LmsTextFormat.PLAIN;

    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_type", nullable = false, length = 10)
    private QuizType type;

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "available_from")
    private Instant availableFrom;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "available_until")
    private Instant availableUntil;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions;

    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions;

    @Column(name = "negative_mark_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal negativeMarkPercent = BigDecimal.ZERO.setScale(2);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LmsContentStatus status = LmsContentStatus.DRAFT;

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

    protected LmsQuiz() {
        // Required by JPA.
    }

    public LmsQuiz(LmsCourse lmsCourse, Long createdByAccountId, LmsQuizSettings settings) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        this.createdByAccountId = Require.notNull(createdByAccountId, "createdByAccountId");
        write(settings);
    }

    /**
     * Changes the settings. A draft may change anything; a published quiz keeps its type, shuffling, and negative
     * marking (an {@link IllegalStateException} otherwise). An archived quiz can no longer change. Without a format the
     * instructions keep their current one.
     */
    public void edit(LmsQuizSettings settings) {
        requireNotArchived();
        Require.notNull(settings, "settings");
        if (status == LmsContentStatus.PUBLISHED && (settings.type() != type
                || settings.shuffleQuestions() != shuffleQuestions || settings.shuffleOptions() != shuffleOptions
                || validNegative(settings.negativeMarkPercent()).compareTo(negativeMarkPercent) != 0)) {
            throw new IllegalStateException(FIXED_AFTER_PUBLISHING);
        }
        write(settings);
    }

    /** Publishes a draft now. Publishing a published quiz changes nothing; an archived one cannot be published. */
    public void publish(Instant at) {
        requireNotArchived();
        if (status != LmsContentStatus.PUBLISHED) {
            this.publishedAt = Require.notNull(at, "at");
            this.status = LmsContentStatus.PUBLISHED;
        }
    }

    /** Keeps the quiz for history only. Archiving again changes nothing; nothing restores it. */
    public void archive() {
        this.status = LmsContentStatus.ARCHIVED;
    }

    /** Whether an attempt may start now: from the opening time (or publication) until the closing time. */
    public QuizAvailability availabilityAt(Instant now) {
        if (availableFrom != null && now.isBefore(availableFrom)) {
            return QuizAvailability.NOT_OPEN;
        }
        if (availableUntil != null && !now.isBefore(availableUntil)) {
            return QuizAvailability.CLOSED;
        }
        return QuizAvailability.OPEN;
    }

    public boolean isDraft() {
        return status == LmsContentStatus.DRAFT;
    }

    public boolean isArchived() {
        return status == LmsContentStatus.ARCHIVED;
    }

    private void requireNotArchived() {
        if (isArchived()) {
            throw new IllegalStateException("An archived quiz can no longer be changed");
        }
    }

    private void write(LmsQuizSettings settings) {
        Require.notNull(settings, "settings");
        String validTitle = Require.text(settings.title(), "Title", MAX_TITLE_LENGTH);
        String validInstructions = Require.optionalText(settings.instructions(), "Instructions", MAX_INSTRUCTIONS_LENGTH);
        QuizType validType = Require.notNull(settings.type(), "Quiz type");
        Integer validTimeLimit = optionalRange(settings.timeLimitMinutes(), "The time limit", MAX_TIME_LIMIT_MINUTES, "minutes");
        Integer validAttempts = optionalRange(settings.maxAttempts(), "The attempt limit", MAX_ATTEMPTS, "attempts");
        Instant from = supported(settings.availableFrom(), "The opening time");
        Instant due = supported(settings.dueAt(), "The due date");
        Instant until = supported(settings.availableUntil(), "The closing time");
        if (from != null && until != null && !from.isBefore(until)) {
            throw new IllegalArgumentException("The closing time must be after the opening time.");
        }
        if (due != null && from != null && due.isBefore(from)) {
            throw new IllegalArgumentException("The due date cannot be before the opening time.");
        }
        if (due != null && until != null && due.isAfter(until)) {
            throw new IllegalArgumentException("The due date cannot be after the closing time.");
        }
        BigDecimal validNegative = validNegative(settings.negativeMarkPercent());

        this.title = validTitle;
        this.instructions = validInstructions;
        this.instructionsFormat = LmsTextFormat.orKeep(settings.instructionsFormat(), instructionsFormat);
        this.type = validType;
        this.timeLimitMinutes = validTimeLimit;
        this.availableFrom = from;
        this.dueAt = due;
        this.availableUntil = until;
        this.maxAttempts = validAttempts;
        this.shuffleQuestions = settings.shuffleQuestions();
        this.shuffleOptions = settings.shuffleOptions();
        this.negativeMarkPercent = validNegative;
    }

    private static Integer optionalRange(Integer value, String name, int max, String unit) {
        if (value != null && (value < 1 || value > max)) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max + " " + unit + ".");
        }
        return value;
    }

    private static Instant supported(Instant moment, String name) {
        if (moment != null && (moment.isBefore(EARLIEST_MOMENT) || moment.isAfter(LATEST_MOMENT))) {
            throw new IllegalArgumentException(name + " must be between the years 2000 and 9999.");
        }
        return moment;
    }

    // A percentage of a question's marks taken off for a wrong answer: 0 (none) to 100, with at most two decimals.
    private static BigDecimal validNegative(BigDecimal percent) {
        BigDecimal value = percent == null ? BigDecimal.ZERO : percent;
        if (value.signum() < 0 || value.compareTo(MAX_NEGATIVE_PERCENT) > 0 || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Negative marking must be a percentage from 0 to 100, with at most 2 decimal places.");
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

    public String getInstructions() {
        return instructions;
    }

    public LmsTextFormat getInstructionsFormat() {
        return instructionsFormat;
    }

    public QuizType getType() {
        return type;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public Instant getAvailableFrom() {
        return availableFrom;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getAvailableUntil() {
        return availableUntil;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public boolean isShuffleQuestions() {
        return shuffleQuestions;
    }

    public boolean isShuffleOptions() {
        return shuffleOptions;
    }

    /** The share of a question's marks taken off for a wrong objective answer, with two decimal places. */
    public BigDecimal getNegativeMarkPercent() {
        return negativeMarkPercent;
    }

    public LmsContentStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
