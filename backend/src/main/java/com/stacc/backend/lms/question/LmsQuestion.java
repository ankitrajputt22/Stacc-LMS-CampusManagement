package com.stacc.backend.lms.question;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.outcome.BloomLevel;
import com.stacc.backend.lms.outcome.LmsCourseOutcome;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One reusable question of an LMS course's question bank, with its answers. Each type keeps its answers its own way:
 * choice questions as options with the correct ones marked, true/false as one answer, short answer as the accepted
 * answers, and descriptive questions as optional guidance for the marker.
 *
 * <p>A new or edited question needs review; only a reviewed question may be offered for practice. An archived question
 * can no longer change. Nothing deletes a question.
 */
@Entity
@Table(name = "lms_questions")
public class LmsQuestion {

    public static final int MAX_TEXT_LENGTH = 20_000;
    public static final int MAX_EXPLANATION_LENGTH = 20_000;
    public static final int MAX_GUIDANCE_LENGTH = 5_000;
    public static final int MAX_TOPIC_LENGTH = 100;
    public static final int MAX_OPTIONS = 10;
    public static final int MARK_DECIMAL_PLACES = 2;
    public static final BigDecimal MAX_MARKS = new BigDecimal("999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No cascade: the course is never created or deleted through its questions.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionType type;

    @Column(name = "question_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String text;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(name = "text_format", nullable = false, length = 10)
    private LmsTextFormat textFormat = LmsTextFormat.PLAIN;

    // Small and always shown with the question, so it is loaded with it.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lms_question_options", joinColumns = @JoinColumn(name = "lms_question_id"))
    @OrderBy("position")
    private List<LmsQuestionOption> options = new ArrayList<>();

    @Column(name = "true_false_answer")
    private Boolean trueFalseAnswer;

    @Column(name = "answer_guidance", length = MAX_GUIDANCE_LENGTH)
    private String answerGuidance;

    @Column(length = MAX_TOPIC_LENGTH)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QuestionDifficulty difficulty;

    @Column(nullable = false, precision = 8, scale = MARK_DECIMAL_PLACES)
    private BigDecimal marks;

    @Enumerated(EnumType.STRING)
    @Column(name = "bloom_level", length = 20)
    private BloomLevel bloomLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lms_course_outcome_id")
    private LmsCourseOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private QuestionReviewStatus reviewStatus = QuestionReviewStatus.NEEDS_REVIEW;

    @Column(name = "practice_allowed", nullable = false)
    private boolean practiceAllowed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionStatus status = QuestionStatus.ACTIVE;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdByAccountId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsQuestion() {
        // Required by JPA.
    }

    public LmsQuestion(LmsCourse lmsCourse, Long createdByAccountId, LmsQuestionContent content) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        this.createdByAccountId = Require.notNull(createdByAccountId, "createdByAccountId");
        write(content);
    }

    /**
     * Replaces everything written about the question. The type may change too, as nothing uses the question yet. An
     * edited question needs a new review and is no longer offered for practice.
     */
    public void edit(LmsQuestionContent content) {
        requireActive();
        write(content);
        this.reviewStatus = QuestionReviewStatus.NEEDS_REVIEW;
        this.practiceAllowed = false;
    }

    /** Marks the question reviewed; reviewing again changes nothing. */
    public void markReviewed() {
        requireActive();
        this.reviewStatus = QuestionReviewStatus.REVIEWED;
    }

    /** Offers the question for practice or stops offering it. Only a reviewed question may be offered. */
    public void allowPractice(boolean allowed) {
        requireActive();
        if (allowed && reviewStatus != QuestionReviewStatus.REVIEWED) {
            throw new IllegalStateException("Only a reviewed question may be offered for practice");
        }
        this.practiceAllowed = allowed;
    }

    /** Archives the question and stops offering it for practice; archiving again changes nothing, and nothing restores it. */
    public void archive() {
        this.status = QuestionStatus.ARCHIVED;
        this.practiceAllowed = false;
    }

    public boolean isActive() {
        return status == QuestionStatus.ACTIVE;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("An archived question cannot be changed");
        }
    }

    private void write(LmsQuestionContent content) {
        Require.notNull(content, "content");
        QuestionType validType = Require.notNull(content.type(), "type");
        String validText = Require.text(content.text(), "text", MAX_TEXT_LENGTH);
        String validExplanation = Require.optionalText(content.explanation(), "explanation", MAX_EXPLANATION_LENGTH);
        List<LmsQuestionOption> validOptions = validOptions(validType, content.options());
        Boolean validTrueFalse = validTrueFalse(validType, content.trueFalseAnswer());
        String validGuidance = Require.optionalText(content.answerGuidance(), "answerGuidance", MAX_GUIDANCE_LENGTH);
        if (validGuidance != null && validType != QuestionType.DESCRIPTIVE) {
            throw new IllegalArgumentException("Only a descriptive question takes answer guidance.");
        }
        LmsCourseOutcome validOutcome = content.outcome();
        if (validOutcome != null && !Objects.equals(validOutcome.getLmsCourse().getId(), lmsCourse.getId())) {
            throw new IllegalArgumentException("The outcome must belong to the question's course.");
        }

        this.type = validType;
        this.text = validText;
        // Left out, a new question is PLAIN and an edited one keeps its format.
        this.textFormat = content.textFormat() == null ? this.textFormat : content.textFormat();
        this.explanation = validExplanation;
        this.options.clear();
        this.options.addAll(validOptions);
        this.trueFalseAnswer = validTrueFalse;
        this.answerGuidance = validGuidance;
        this.topic = Require.optionalText(content.topic(), "topic", MAX_TOPIC_LENGTH);
        this.difficulty = Require.notNull(content.difficulty(), "difficulty");
        this.marks = validMarks(content.marks());
        this.bloomLevel = content.bloomLevel();
        this.outcome = validOutcome;
    }

    private static List<LmsQuestionOption> validOptions(QuestionType type, List<LmsQuestionContent.Option> given) {
        List<LmsQuestionContent.Option> options = given == null ? List.of() : given;
        switch (type) {
            case TRUE_FALSE, DESCRIPTIVE -> {
                if (!options.isEmpty()) {
                    throw new IllegalArgumentException("This type of question takes no options.");
                }
                return List.of();
            }
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
                if (options.size() < 2 || options.size() > MAX_OPTIONS) {
                    throw new IllegalArgumentException("A choice question needs 2 to " + MAX_OPTIONS + " options.");
                }
                long correct = options.stream().filter(LmsQuestionContent.Option::correct).count();
                if (type == QuestionType.SINGLE_CHOICE && correct != 1) {
                    throw new IllegalArgumentException("A single-choice question needs exactly one correct option.");
                }
                if (type == QuestionType.MULTIPLE_CHOICE && correct < 1) {
                    throw new IllegalArgumentException("A multiple-choice question needs at least one correct option.");
                }
            }
            case SHORT_ANSWER -> {
                if (options.isEmpty() || options.size() > MAX_OPTIONS) {
                    throw new IllegalArgumentException("A short-answer question needs 1 to " + MAX_OPTIONS + " accepted answers.");
                }
                if (options.stream().anyMatch(option -> !option.correct())) {
                    throw new IllegalArgumentException("Every accepted answer must be marked correct.");
                }
            }
        }
        List<LmsQuestionOption> valid = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (LmsQuestionContent.Option option : options) {
            LmsQuestionOption made = new LmsQuestionOption(valid.size() + 1, option == null ? null : option.text(),
                    option != null && option.correct());
            if (!seen.add(made.getText().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Options must differ from each other.");
            }
            valid.add(made);
        }
        return valid;
    }

    private static Boolean validTrueFalse(QuestionType type, Boolean answer) {
        if (type == QuestionType.TRUE_FALSE) {
            return Require.notNull(answer, "The true/false answer");
        }
        if (answer != null) {
            throw new IllegalArgumentException("Only a true/false question takes a true/false answer.");
        }
        return null;
    }

    private static BigDecimal validMarks(BigDecimal marks) {
        Require.notNull(marks, "marks");
        if (marks.signum() <= 0) {
            throw new IllegalArgumentException("marks must be greater than zero");
        }
        if (marks.compareTo(MAX_MARKS) > 0) {
            throw new IllegalArgumentException("marks must be at most " + MAX_MARKS);
        }
        if (marks.stripTrailingZeros().scale() > MARK_DECIMAL_PLACES) {
            throw new IllegalArgumentException("marks must have at most " + MARK_DECIMAL_PLACES + " decimal places");
        }
        return marks.setScale(MARK_DECIMAL_PLACES);
    }

    public Long getId() {
        return id;
    }

    public LmsCourse getLmsCourse() {
        return lmsCourse;
    }

    public QuestionType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public String getExplanation() {
        return explanation;
    }

    public LmsTextFormat getTextFormat() {
        return textFormat;
    }

    public List<LmsQuestionOption> getOptions() {
        return List.copyOf(options);
    }

    public Boolean getTrueFalseAnswer() {
        return trueFalseAnswer;
    }

    public String getAnswerGuidance() {
        return answerGuidance;
    }

    public String getTopic() {
        return topic;
    }

    public QuestionDifficulty getDifficulty() {
        return difficulty;
    }

    public BigDecimal getMarks() {
        return marks;
    }

    public BloomLevel getBloomLevel() {
        return bloomLevel;
    }

    public LmsCourseOutcome getOutcome() {
        return outcome;
    }

    public QuestionReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public boolean isPracticeAllowed() {
        return practiceAllowed;
    }

    public QuestionStatus getStatus() {
        return status;
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
