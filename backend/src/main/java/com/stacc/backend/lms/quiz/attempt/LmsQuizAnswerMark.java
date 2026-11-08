package com.stacc.backend.lms.quiz.attempt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * A teacher's marks for one answer, with optional feedback: needed for a descriptive answer, and a regrade that takes
 * the place of the automatic marks for any other. Marking again replaces it without history. The server sets who marked
 * it and when.
 */
@Entity
@Table(name = "lms_quiz_answer_marks")
public class LmsQuizAnswerMark {

    public static final int MAX_FEEDBACK_LENGTH = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_quiz_answer_id", nullable = false, updatable = false, unique = true)
    private LmsQuizAnswer answer;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal marks;

    @Column(length = MAX_FEEDBACK_LENGTH)
    private String feedback;

    @Column(name = "marked_by_account_id", nullable = false)
    private Long markedByAccountId;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;

    protected LmsQuizAnswerMark() {
        // Required by JPA.
    }

    public LmsQuizAnswerMark(LmsQuizAnswer answer) {
        this.answer = Require.notNull(answer, "answer");
    }

    /** Sets the marks (0 to the question's marks in the quiz, two decimals) and feedback, replacing earlier ones. */
    public void mark(BigDecimal marks, BigDecimal maximum, String feedback, Long markedByAccountId, Instant at) {
        Require.notNull(marks, "Marks");
        if (marks.signum() < 0 || marks.compareTo(maximum) > 0 || marks.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Marks must be from 0 to " + maximum.stripTrailingZeros().toPlainString()
                    + ", with at most 2 decimal places.");
        }
        this.marks = marks.setScale(2, RoundingMode.UNNECESSARY);
        this.feedback = Require.optionalText(feedback, "Feedback", MAX_FEEDBACK_LENGTH);
        this.markedByAccountId = Require.notNull(markedByAccountId, "markedByAccountId");
        this.markedAt = Require.notNull(at, "at");
    }

    public Long getId() {
        return id;
    }

    public LmsQuizAnswer getAnswer() {
        return answer;
    }

    public BigDecimal getMarks() {
        return marks;
    }

    public String getFeedback() {
        return feedback;
    }

    public Long getMarkedByAccountId() {
        return markedByAccountId;
    }

    public Instant getMarkedAt() {
        return markedAt;
    }
}
