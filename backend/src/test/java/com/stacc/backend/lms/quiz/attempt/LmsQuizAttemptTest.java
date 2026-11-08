package com.stacc.backend.lms.quiz.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.lms.assignment.submission.SubmissionFixtures;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizSettings;
import com.stacc.backend.lms.quiz.QuizType;
import org.junit.jupiter.api.Test;

class LmsQuizAttemptTest {

    private static final Instant START = Instant.parse("2026-11-10T05:00:00Z");
    private final LmsCourse course = SubmissionFixtures.course(7);
    private final CourseEnrollment enrollment = SubmissionFixtures.enrollment(course, 31, 41);

    private LmsQuiz quiz(Integer minutes, Instant due, Instant until) {
        return new LmsQuiz(course, 21L, new LmsQuizSettings("Q", null, LmsTextFormat.PLAIN, QuizType.GRADED, minutes, null,
                due, until, null, false, false, BigDecimal.ZERO));
    }

    @Test
    void theDeadlineIsTheEarlierOfTheTimeLimitAndTheClosingTime() {
        assertThat(new LmsQuizAttempt(quiz(20, null, null), enrollment, 1, START).getDeadlineAt())
                .isEqualTo(START.plusSeconds(1200));
        assertThat(new LmsQuizAttempt(quiz(20, null, START.plusSeconds(600)), enrollment, 1, START).getDeadlineAt())
                .isEqualTo(START.plusSeconds(600));
        assertThat(new LmsQuizAttempt(quiz(null, null, START.plusSeconds(600)), enrollment, 1, START).getDeadlineAt())
                .isEqualTo(START.plusSeconds(600));
        assertThat(new LmsQuizAttempt(quiz(null, null, null), enrollment, 1, START).getDeadlineAt()).isNull();
    }

    @Test
    void anOpenAttemptPastItsDeadlineShowsAsTimeUpAndTakesAnswersOnlyDuringTheGrace() {
        LmsQuizAttempt attempt = new LmsQuizAttempt(quiz(10, null, null), enrollment, 1, START);
        Instant deadline = START.plusSeconds(600);

        assertThat(attempt.stateAt(deadline.minusSeconds(1))).isEqualTo(QuizAttemptState.IN_PROGRESS);
        assertThat(attempt.stateAt(deadline)).isEqualTo(QuizAttemptState.TIME_UP);
        assertThat(attempt.acceptsAnswersAt(deadline.plus(LmsQuizAttempt.GRACE))).isTrue();
        assertThat(attempt.acceptsAnswersAt(deadline.plus(LmsQuizAttempt.GRACE).plusMillis(1))).isFalse();
    }

    @Test
    void handingInBeforeTheDeadlineSubmitsNowAndAfterItExpiresAtTheDeadline() {
        LmsQuizAttempt onTime = new LmsQuizAttempt(quiz(10, null, null), enrollment, 1, START);
        onTime.submit(START.plusSeconds(300));
        assertThat(onTime.getStatus()).isEqualTo(QuizAttemptStatus.SUBMITTED);
        assertThat(onTime.getSubmittedAt()).isEqualTo(START.plusSeconds(300));
        onTime.submit(START.plusSeconds(900));
        assertThat(onTime.getSubmittedAt()).isEqualTo(START.plusSeconds(300));

        LmsQuizAttempt inGrace = new LmsQuizAttempt(quiz(10, null, null), enrollment, 2, START);
        inGrace.submit(START.plusSeconds(605));
        assertThat(inGrace.getStatus()).isEqualTo(QuizAttemptStatus.SUBMITTED);
        assertThat(inGrace.getSubmittedAt()).isEqualTo(START.plusSeconds(600));

        LmsQuizAttempt left = new LmsQuizAttempt(quiz(10, null, null), enrollment, 3, START);
        left.submit(START.plusSeconds(3600));
        assertThat(left.getStatus()).isEqualTo(QuizAttemptStatus.EXPIRED);
        assertThat(left.getSubmittedAt()).isEqualTo(START.plusSeconds(600));
        assertThat(left.stateAt(START.plusSeconds(3600))).isEqualTo(QuizAttemptState.EXPIRED);
    }

    @Test
    void lateComesFromTheDueDateWhenTheAttemptStarted() {
        LmsQuiz quiz = quiz(null, START.plusSeconds(60), null);
        LmsQuizAttempt attempt = new LmsQuizAttempt(quiz, enrollment, 1, START);
        attempt.submit(START.plusSeconds(120));
        assertThat(attempt.isLate()).isTrue();

        LmsQuizAttempt onTime = new LmsQuizAttempt(quiz, enrollment, 2, START);
        onTime.submit(START.plusSeconds(30));
        assertThat(onTime.isLate()).isFalse();
    }

    @Test
    void shuffledOrdersAreTheSameOnEveryReadAndDifferBetweenAttempts() {
        List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        assertThat(QuizAttemptOrder.questions(5, items, false)).isEqualTo(items);
        assertThat(QuizAttemptOrder.questions(5, items, true)).isEqualTo(QuizAttemptOrder.questions(5, items, true))
                .containsExactlyInAnyOrderElementsOf(items);
        assertThat(QuizAttemptOrder.questions(5, items, true)).isNotEqualTo(QuizAttemptOrder.questions(6, items, true));
        assertThat(QuizAttemptOrder.options(5, 9, items, true)).isEqualTo(QuizAttemptOrder.options(5, 9, items, true))
                .containsExactlyInAnyOrderElementsOf(items);
        assertThat(QuizAttemptOrder.options(5, 9, items, false)).isEqualTo(items);
    }
}
