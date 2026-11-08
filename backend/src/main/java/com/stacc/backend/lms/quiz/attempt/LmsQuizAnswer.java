package com.stacc.backend.lms.quiz.attempt;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.quiz.LmsQuizQuestion;
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
import jakarta.persistence.Table;

/**
 * A student's answer to one question of one attempt: the chosen options (by their position in the question), a true or
 * false answer, or written text, as the question's type takes. Saving again replaces it while the attempt is open; an
 * empty answer means the question is left unanswered.
 */
@Entity
@Table(name = "lms_quiz_answers")
public class LmsQuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_quiz_attempt_id", nullable = false, updatable = false)
    private LmsQuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_quiz_question_id", nullable = false, updatable = false)
    private LmsQuizQuestion quizQuestion;

    // Part of the answer: replaced with it, never shared.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lms_quiz_answer_options", joinColumns = @JoinColumn(name = "lms_quiz_answer_id"))
    @Column(name = "position", nullable = false)
    private Set<Integer> selectedPositions = new TreeSet<>();

    @Column(name = "true_false_answer")
    private Boolean trueFalseAnswer;

    @Column(name = "text_answer", columnDefinition = "MEDIUMTEXT")
    private String textAnswer;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    protected LmsQuizAnswer() {
        // Required by JPA.
    }

    public LmsQuizAnswer(LmsQuizAttempt attempt, LmsQuizQuestion quizQuestion) {
        this.attempt = Require.notNull(attempt, "attempt");
        this.quizQuestion = Require.notNull(quizQuestion, "quizQuestion");
    }

    /** Replaces the answer; the service has checked it fits the question. */
    public void replace(Set<Integer> positions, Boolean trueFalse, String text, Instant at) {
        this.selectedPositions.clear();
        this.selectedPositions.addAll(positions);
        this.trueFalseAnswer = trueFalse;
        this.textAnswer = text;
        this.answeredAt = Require.notNull(at, "at");
    }

    public Long getId() {
        return id;
    }

    public LmsQuizAttempt getAttempt() {
        return attempt;
    }

    public LmsQuizQuestion getQuizQuestion() {
        return quizQuestion;
    }

    /** The chosen options' positions, smallest first. */
    public Set<Integer> getSelectedPositions() {
        return new TreeSet<>(selectedPositions);
    }

    public Boolean getTrueFalseAnswer() {
        return trueFalseAnswer;
    }

    public String getTextAnswer() {
        return textAnswer;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}
