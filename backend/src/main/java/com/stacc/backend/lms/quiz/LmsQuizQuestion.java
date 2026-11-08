package com.stacc.backend.lms.quiz;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.question.LmsQuestion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One bank question in a quiz, at a position, with the marks it carries in this quiz (the bank's marks unless faculty
 * chose others). The same bank question can be used by several quizzes, once each. The question itself stays in the
 * bank: nothing is copied.
 */
@Entity
@Table(name = "lms_quiz_questions")
public class LmsQuizQuestion {

    public static final BigDecimal MAX_MARKS = new BigDecimal("999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No cascade: neither the quiz nor the bank question is created or deleted through this link.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_quiz_id", nullable = false, updatable = false)
    private LmsQuiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_question_id", nullable = false, updatable = false)
    private LmsQuestion question;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal marks;

    protected LmsQuizQuestion() {
        // Required by JPA.
    }

    public LmsQuizQuestion(LmsQuiz quiz, LmsQuestion question, int position, BigDecimal marks) {
        this.quiz = Require.notNull(quiz, "quiz");
        this.question = Require.notNull(question, "question");
        moveTo(position);
        changeMarks(marks);
    }

    public void moveTo(int position) {
        this.position = Require.range(position, "position", 1, LmsQuiz.MAX_QUESTIONS);
    }

    /** Marks greater than zero, below 1,000,000, with at most two decimal places. */
    public void changeMarks(BigDecimal marks) {
        Require.notNull(marks, "Marks");
        if (marks.signum() <= 0 || marks.compareTo(MAX_MARKS) > 0 || marks.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(
                    "Marks must be greater than zero and below 1,000,000, with at most 2 decimal places.");
        }
        this.marks = marks.setScale(2, RoundingMode.UNNECESSARY);
    }

    public Long getId() {
        return id;
    }

    public LmsQuiz getQuiz() {
        return quiz;
    }

    public LmsQuestion getQuestion() {
        return question;
    }

    public int getPosition() {
        return position;
    }

    public BigDecimal getMarks() {
        return marks;
    }
}
