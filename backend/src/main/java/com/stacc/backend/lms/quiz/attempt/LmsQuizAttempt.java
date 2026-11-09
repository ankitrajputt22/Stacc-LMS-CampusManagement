package com.stacc.backend.lms.quiz.attempt;

import java.time.Duration;
import java.time.Instant;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.common.validation.Require;
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
 * One student's attempt at a published quiz, through their official course enrollment. Its deadline (the earlier of
 * the time limit from the start and the quiz's closing time) and the due date are fixed when it starts, so later
 * changes to the quiz never move them. The server's clock decides: answers are taken until the deadline, with a short
 * grace for answers already on their way, and an attempt handed in late, or closed after its deadline, is
 * {@link QuizAttemptStatus#EXPIRED} with the deadline as its end.
 */
@Entity
@Table(name = "lms_quiz_attempts")
public class LmsQuizAttempt {

    /** How long after the deadline an answer already on its way is still taken. */
    public static final Duration GRACE = Duration.ofSeconds(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_quiz_id", nullable = false, updatable = false)
    private LmsQuiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_enrollment_id", nullable = false, updatable = false)
    private CourseEnrollment courseEnrollment;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizAttemptStatus status = QuizAttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", updatable = false)
    private Instant deadlineAt;

    @Column(name = "due_at_snapshot", updatable = false)
    private Instant dueAtSnapshot;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsQuizAttempt() {
        // Required by JPA.
    }

    public LmsQuizAttempt(LmsQuiz quiz, CourseEnrollment courseEnrollment, int attemptNumber, Instant startedAt) {
        this.quiz = Require.notNull(quiz, "quiz");
        this.courseEnrollment = Require.notNull(courseEnrollment, "courseEnrollment");
        this.attemptNumber = Require.range(attemptNumber, "attemptNumber", 1, Integer.MAX_VALUE);
        this.startedAt = Require.notNull(startedAt, "startedAt");
        this.deadlineAt = deadlineFor(quiz, startedAt);
        this.dueAtSnapshot = quiz.getDueAt();
    }

    /** The earlier of the time limit counted from the start and the quiz's closing time; null when neither is set. */
    public static Instant deadlineFor(LmsQuiz quiz, Instant startedAt) {
        Instant limit = quiz.getTimeLimitMinutes() == null ? null : startedAt.plus(Duration.ofMinutes(quiz.getTimeLimitMinutes()));
        Instant closes = quiz.getAvailableUntil();
        if (limit == null) {
            return closes;
        }
        return closes == null || limit.isBefore(closes) ? limit : closes;
    }

    /** How the attempt looks now. */
    public QuizAttemptState stateAt(Instant now) {
        return switch (status) {
            case SUBMITTED -> QuizAttemptState.SUBMITTED;
            case EXPIRED -> QuizAttemptState.EXPIRED;
            case IN_PROGRESS -> deadlineAt == null || now.isBefore(deadlineAt) ? QuizAttemptState.IN_PROGRESS : QuizAttemptState.TIME_UP;
        };
    }

    /** Whether the attempt is still open for answers now, counting the short grace after the deadline. */
    public boolean acceptsAnswersAt(Instant now) {
        return status == QuizAttemptStatus.IN_PROGRESS && (deadlineAt == null || !now.isAfter(deadlineAt.plus(GRACE)));
    }

    /**
     * Hands the attempt in. Before the deadline (and its grace) it is submitted now; after it, it is closed as expired
     * at the deadline. Handing in a finished attempt changes nothing.
     */
    public void submit(Instant now) {
        if (status != QuizAttemptStatus.IN_PROGRESS) {
            return;
        }
        if (acceptsAnswersAt(now)) {
            this.status = QuizAttemptStatus.SUBMITTED;
            this.submittedAt = deadlineAt != null && now.isAfter(deadlineAt) ? deadlineAt : now;
        } else {
            this.status = QuizAttemptStatus.EXPIRED;
            this.submittedAt = deadlineAt;
        }
    }

    /** Whether it was handed in after the due date that applied when it started. */
    public boolean isLate() {
        return submittedAt != null && dueAtSnapshot != null && submittedAt.isAfter(dueAtSnapshot);
    }

    public Long getId() {
        return id;
    }

    public LmsQuiz getQuiz() {
        return quiz;
    }

    public CourseEnrollment getCourseEnrollment() {
        return courseEnrollment;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public QuizAttemptStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public Instant getDueAtSnapshot() {
        return dueAtSnapshot;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
