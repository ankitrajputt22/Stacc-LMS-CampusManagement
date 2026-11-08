package com.stacc.backend.lms.quiz.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.assignment.submission.SubmissionFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.QuestionFixtures;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizQuestion;
import com.stacc.backend.lms.quiz.LmsQuizQuestionRepository;
import com.stacc.backend.lms.quiz.LmsQuizRepository;
import com.stacc.backend.lms.quiz.LmsQuizSettings;
import com.stacc.backend.lms.quiz.QuizType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class LmsQuizGradingServiceTest {

    private static final Long FACULTY = 21L;
    private static final Long OTHER = 22L;
    private static final Long COURSE_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-11-10T05:00:00Z");

    private final LmsQuizRepository quizzes = mock(LmsQuizRepository.class);
    private final LmsQuizQuestionRepository quizQuestions = mock(LmsQuizQuestionRepository.class);
    private final LmsQuizAttemptRepository attempts = mock(LmsQuizAttemptRepository.class);
    private final LmsQuizAnswerRepository answers = mock(LmsQuizAnswerRepository.class);
    private final LmsQuizAnswerMarkRepository marks = mock(LmsQuizAnswerMarkRepository.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsQuizGradingService service = new LmsQuizGradingService(quizzes, quizQuestions, attempts, answers, marks,
            facultyAccess, new QuizScores(quizQuestions, answers, marks), Clock.fixed(NOW, ZoneOffset.UTC));
    private final LmsCourse course = SubmissionFixtures.course(COURSE_ID);
    private final CourseEnrollment enrollment = SubmissionFixtures.enrollment(course, 31, 41);
    private LmsQuiz quiz;
    private LmsQuizQuestion question;
    private LmsQuizAttempt attempt;
    private LmsQuizAnswer answer;

    private static void refusedWith(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }

    private void owner(LmsContentStatus status) {
        LmsQuizAttemptRepository.AttemptOwner owner = mock(LmsQuizAttemptRepository.AttemptOwner.class);
        when(owner.getLmsCourseId()).thenReturn(COURSE_ID);
        when(owner.getQuizStatus()).thenReturn(status);
        when(attempts.findOwnerById(9L)).thenReturn(Optional.of(owner));
    }

    @BeforeEach
    void setUp() {
        quiz = new LmsQuiz(course, FACULTY, new LmsQuizSettings("Q", null, LmsTextFormat.PLAIN, QuizType.GRADED, 10, null, null,
                null, null, false, false, BigDecimal.ZERO));
        ReflectionTestUtils.setField(quiz, "id", 501L);
        quiz.publish(NOW.minusSeconds(7200));
        question = new LmsQuizQuestion(quiz, new LmsQuestion(course, FACULTY, QuestionFixtures.singleChoice("Which?")), 1,
                new BigDecimal("2"));
        ReflectionTestUtils.setField(question, "id", 601L);
        attempt = new LmsQuizAttempt(quiz, enrollment, 1, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(attempt, "id", 9L);
        answer = new LmsQuizAnswer(attempt, question);
        ReflectionTestUtils.setField(answer, "id", 77L);
        answer.replace(Set.of(1), null, null, NOW.minusSeconds(3500));

        owner(LmsContentStatus.PUBLISHED);
        when(facultyAccess.canAccessCourse(FACULTY, COURSE_ID)).thenReturn(true);
        when(attempts.findByIdForUpdate(9L)).thenReturn(Optional.of(attempt));
        when(attempts.findWithStudentById(9L)).thenReturn(Optional.of(attempt));
        when(quizQuestions.findById(601L)).thenReturn(Optional.of(question));
        when(quizQuestions.findAllForQuizzes(any())).thenReturn(List.of(question));
        when(answers.findForUpdate(9L, 601L)).thenReturn(Optional.of(answer));
        when(answers.findAllByAttemptIdIn(any())).thenReturn(List.of(answer));
        when(marks.findByAnswerIdForUpdate(77L)).thenReturn(Optional.empty());
    }

    @Test
    void anOpenAttemptIsMarkedOnlyOnceItsTimeIsUpAndItIsClosed() {
        attempt = new LmsQuizAttempt(quiz, enrollment, 1, NOW.minusSeconds(60));
        ReflectionTestUtils.setField(attempt, "id", 9L);
        when(attempts.findByIdForUpdate(9L)).thenReturn(Optional.of(attempt));
        refusedWith(() -> service.mark(FACULTY, 9L, 601L, BigDecimal.ONE, null), HttpStatus.CONFLICT, LmsQuizGradingService.NOT_HANDED_IN);
        refusedWith(() -> service.close(FACULTY, 9L), HttpStatus.CONFLICT, LmsQuizGradingService.STILL_OPEN);
    }

    @Test
    void anAttemptLeftOpenPastItsDeadlineIsClosedAsExpired() {
        service.close(FACULTY, 9L);
        assertThat(attempt.getStatus()).isEqualTo(QuizAttemptStatus.EXPIRED);
        assertThat(attempt.getSubmittedAt()).isEqualTo(NOW.minusSeconds(3000));
    }

    @Test
    void aRegradeReplacesTheAutomaticMarksWithTheTeachersAndKeepsThemInRange() {
        attempt.submit(NOW.minusSeconds(3400));
        when(marks.saveAndFlush(any())).thenAnswer(call -> {
            LmsQuizAnswerMark saved = call.getArgument(0);
            when(marks.findAllByAnswerIdIn(any())).thenReturn(List.of(saved));
            return saved;
        });

        LmsQuizGradingService.AttemptReview review = service.mark(FACULTY, 9L, 601L, new BigDecimal("1.5"), "Half right.");
        assertThat(review.score().questions().getFirst().mark().getMarkedByAccountId()).isEqualTo(FACULTY);
        assertThat(review.score().score()).isEqualByComparingTo("1.50");

        refusedWith(() -> service.mark(FACULTY, 9L, 601L, new BigDecimal("2.5"), null), HttpStatus.BAD_REQUEST,
                "Marks must be from 0 to 2, with at most 2 decimal places.");
    }

    @Test
    void anUnansweredQuestionNeedsNoMarks() {
        attempt.submit(NOW.minusSeconds(3400));
        answer.replace(Set.of(), null, null, NOW.minusSeconds(3450));
        refusedWith(() -> service.mark(FACULTY, 9L, 601L, BigDecimal.ONE, null), HttpStatus.CONFLICT, LmsQuizGradingService.UNANSWERED);
        verify(marks, never()).saveAndFlush(any());
    }

    @Test
    void someoneWhoDoesNotTeachTheCourseOrAnArchivedQuizCannotBeMarked() {
        refusedWith(() -> service.review(OTHER, 9L), HttpStatus.NOT_FOUND, LmsQuizGradingService.ATTEMPT_UNAVAILABLE);
        owner(LmsContentStatus.ARCHIVED);
        assertThat(service.review(FACULTY, 9L).attempt()).isSameAs(attempt);
        refusedWith(() -> service.mark(FACULTY, 9L, 601L, BigDecimal.ONE, null), HttpStatus.NOT_FOUND,
                LmsQuizGradingService.ATTEMPT_UNAVAILABLE);
        verify(attempts, never()).findByIdForUpdate(9L);
    }

    @Test
    void aDraftQuizHasNoResults() {
        LmsQuiz draft = new LmsQuiz(course, FACULTY, new LmsQuizSettings("D", null, null, QuizType.GRADED, null, null, null, null,
                null, false, false, null));
        when(quizzes.findById(502L)).thenReturn(Optional.of(draft));
        when(quizzes.findById(501L)).thenReturn(Optional.of(quiz));
        when(attempts.findAllWithStudentByQuizId(501L)).thenReturn(List.of(attempt));
        when(quizQuestions.totalsFor(any())).thenReturn(List.of());

        refusedWith(() -> service.results(FACULTY, 502L), HttpStatus.NOT_FOUND, LmsQuizGradingService.QUIZ_UNAVAILABLE);
        LmsQuizGradingService.QuizResults results = service.results(FACULTY, 501L);
        assertThat(results.attempts()).containsExactly(attempt);
        assertThat(results.scores()).containsKey(9L);
    }
}
