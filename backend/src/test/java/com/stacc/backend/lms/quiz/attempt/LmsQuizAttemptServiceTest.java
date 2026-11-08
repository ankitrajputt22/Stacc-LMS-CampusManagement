package com.stacc.backend.lms.quiz.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.assignment.submission.SubmissionFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionContent;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionFixtures;
import com.stacc.backend.lms.question.QuestionType;
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

class LmsQuizAttemptServiceTest {

    private static final Long STUDENT = 31L;
    private static final Long OTHER = 32L;
    private static final Long COURSE_ID = 7L;
    private static final Long QUIZ_ID = 501L;
    private static final Instant NOW = Instant.parse("2026-11-10T05:00:00Z");

    /** A clock the test moves. */
    private static final class TestClock extends Clock {
        private Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final LmsStudentAccessService studentAccess = mock(LmsStudentAccessService.class);
    private final LmsQuizRepository quizzes = mock(LmsQuizRepository.class);
    private final LmsQuizQuestionRepository quizQuestions = mock(LmsQuizQuestionRepository.class);
    private final LmsQuizAttemptRepository attempts = mock(LmsQuizAttemptRepository.class);
    private final LmsQuizAnswerRepository answers = mock(LmsQuizAnswerRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final TestClock clock = new TestClock();
    private final LmsQuizAnswerMarkRepository marks = mock(LmsQuizAnswerMarkRepository.class);
    private final LmsQuizAttemptService service = new LmsQuizAttemptService(studentAccess, quizzes, quizQuestions, attempts,
            answers, courseEnrollments, clock, new QuizScores(quizQuestions, answers, marks));
    private final LmsCourse course = SubmissionFixtures.course(COURSE_ID);
    private final CourseEnrollment enrollment = SubmissionFixtures.enrollment(course, STUDENT, 41);
    private final List<LmsQuizAttempt> stored = new ArrayList<>();
    private LmsQuiz quiz;
    private LmsQuizQuestion single;
    private LmsQuizQuestion written;

    private static void refusedWith(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }

    private LmsQuiz quiz(Integer minutes, Instant from, Instant until, Integer maxAttempts) {
        LmsQuiz made = new LmsQuiz(course, 21L, new LmsQuizSettings("Unit test", null, LmsTextFormat.PLAIN, QuizType.GRADED,
                minutes, from, null, until, maxAttempts, false, false, BigDecimal.ZERO));
        ReflectionTestUtils.setField(made, "id", QUIZ_ID);
        made.publish(NOW.minusSeconds(3600));
        return made;
    }

    private LmsQuizQuestion link(long id, LmsQuestionContent content, int position) {
        LmsQuestion question = new LmsQuestion(course, 21L, content);
        LmsQuizQuestion link = new LmsQuizQuestion(quiz, question, position, BigDecimal.ONE);
        ReflectionTestUtils.setField(link, "id", id);
        when(quizQuestions.findById(id)).thenReturn(Optional.of(link));
        return link;
    }

    private void owned(long attemptId, LmsQuizAttempt attempt) {
        ReflectionTestUtils.setField(attempt, "id", attemptId);
        LmsQuizAttemptRepository.AttemptOwner owner = mock(LmsQuizAttemptRepository.AttemptOwner.class);
        when(owner.getCourseEnrollmentId()).thenReturn(41L);
        when(owner.getQuizId()).thenReturn(QUIZ_ID);
        when(owner.getLmsCourseId()).thenReturn(COURSE_ID);
        when(owner.getQuizStatus()).thenReturn(LmsContentStatus.PUBLISHED);
        when(attempts.findOwnerById(attemptId)).thenReturn(Optional.of(owner));
        when(attempts.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(attempts.findById(attemptId)).thenReturn(Optional.of(attempt));
    }

    private void useQuiz(LmsQuiz chosen) {
        quiz = chosen;
        when(quizzes.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
    }

    @BeforeEach
    void setUp() {
        useQuiz(quiz(20, null, null, 2));
        single = link(601, QuestionFixtures.singleChoice("Which?"), 1);
        written = link(602, new LmsQuestionContent(QuestionType.SHORT_ANSWER, "Name it.", null, null,
                List.of(QuestionFixtures.option("Queue", true)), null, null, null, QuestionDifficulty.EASY, BigDecimal.ONE,
                null, null), 2);
        when(studentAccess.findAccessGrantingEnrollment(STUDENT, COURSE_ID)).thenReturn(Optional.of(enrollment));
        when(courseEnrollments.findByIdForUpdate(41L)).thenReturn(Optional.of(enrollment));
        when(attempts.findAllForUpdate(QUIZ_ID, 41L)).thenAnswer(call -> List.copyOf(stored));
        when(attempts.saveAndFlush(any())).thenAnswer(call -> {
            LmsQuizAttempt attempt = call.getArgument(0);
            if (!stored.contains(attempt)) {
                stored.add(attempt);
            }
            return attempt;
        });
        when(answers.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(answers.findForUpdate(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(quizQuestions.findAllForQuiz(QUIZ_ID)).thenReturn(List.of(single, written));
        when(quizQuestions.findAllForQuizzes(any())).thenReturn(List.of(single, written));
    }

    @Test
    void aStartMakesTheFirstAttemptWithItsDeadlineAndASecondStartResumesIt() {
        LmsQuizAttempt first = service.start(STUDENT, COURSE_ID, QUIZ_ID);

        assertThat(first.getAttemptNumber()).isEqualTo(1);
        assertThat(first.getDeadlineAt()).isEqualTo(NOW.plusSeconds(1200));
        assertThat(service.start(STUDENT, COURSE_ID, QUIZ_ID)).isSameAs(first);
        assertThat(stored).hasSize(1);
    }

    @Test
    void anAttemptLeftPastItsDeadlineIsClosedBeforeTheNextStarts() {
        LmsQuizAttempt first = service.start(STUDENT, COURSE_ID, QUIZ_ID);
        clock.now = NOW.plusSeconds(7200);

        LmsQuizAttempt second = service.start(STUDENT, COURSE_ID, QUIZ_ID);
        assertThat(first.getStatus()).isEqualTo(QuizAttemptStatus.EXPIRED);
        assertThat(first.getSubmittedAt()).isEqualTo(NOW.plusSeconds(1200));
        assertThat(second.getAttemptNumber()).isEqualTo(2);

        second.submit(clock.now);
        refusedWith(() -> service.start(STUDENT, COURSE_ID, QUIZ_ID), HttpStatus.CONFLICT, LmsQuizAttemptService.NO_ATTEMPTS_LEFT);
    }

    @Test
    void aQuizStartsOnlyWhileItIsOpen() {
        useQuiz(quiz(null, NOW.plusSeconds(60), null, null));
        refusedWith(() -> service.start(STUDENT, COURSE_ID, QUIZ_ID), HttpStatus.CONFLICT, LmsQuizAttemptService.NOT_OPEN);
        useQuiz(quiz(null, null, NOW, null));
        refusedWith(() -> service.start(STUDENT, COURSE_ID, QUIZ_ID), HttpStatus.CONFLICT, LmsQuizAttemptService.CLOSED);
        assertThat(stored).isEmpty();
    }

    @Test
    void aStudentWithoutTheCourseOrADraftQuizGetsTheSame404() {
        refusedWith(() -> service.start(OTHER, COURSE_ID, QUIZ_ID), HttpStatus.NOT_FOUND, LmsQuizAttemptService.QUIZ_UNAVAILABLE);
        LmsQuiz draft = new LmsQuiz(course, 21L,
                new LmsQuizSettings("Draft", null, null, QuizType.GRADED, null, null, null, null, null, false, false, null));
        ReflectionTestUtils.setField(draft, "id", QUIZ_ID);
        when(quizzes.findById(QUIZ_ID)).thenReturn(Optional.of(draft));
        refusedWith(() -> service.start(STUDENT, COURSE_ID, QUIZ_ID), HttpStatus.NOT_FOUND, LmsQuizAttemptService.QUIZ_UNAVAILABLE);
        refusedWith(() -> service.start(STUDENT, 8L, QUIZ_ID), HttpStatus.NOT_FOUND, LmsQuizAttemptService.QUIZ_UNAVAILABLE);
        verify(courseEnrollments, never()).findByIdForUpdate(any());
    }

    @Test
    void answersMustFitTheirQuestion() {
        LmsQuizAttempt attempt = new LmsQuizAttempt(quiz, enrollment, 1, NOW);
        owned(9, attempt);

        LmsQuizAnswer saved = service.saveAnswer(STUDENT, 9L, 601L, new LmsQuizAttemptService.AnswerInput(List.of(2), null, null));
        assertThat(saved.getSelectedPositions()).containsExactly(2);
        assertThat(saved.getAnsweredAt()).isEqualTo(NOW);
        assertThat(service.saveAnswer(STUDENT, 9L, 602L, new LmsQuizAttemptService.AnswerInput(null, null, "queue")).getTextAnswer())
                .isEqualTo("queue");

        for (LmsQuizAttemptService.AnswerInput wrong : List.of(
                new LmsQuizAttemptService.AnswerInput(List.of(1, 2), null, null),
                new LmsQuizAttemptService.AnswerInput(List.of(3), null, null),
                new LmsQuizAttemptService.AnswerInput(List.of(1), true, null),
                new LmsQuizAttemptService.AnswerInput(null, null, "Text"))) {
            refusedWith(() -> service.saveAnswer(STUDENT, 9L, 601L, wrong), HttpStatus.BAD_REQUEST, LmsQuizAttemptService.ANSWER_INVALID);
        }
        refusedWith(() -> service.saveAnswer(STUDENT, 9L, 602L, new LmsQuizAttemptService.AnswerInput(List.of(1), null, null)),
                HttpStatus.BAD_REQUEST, LmsQuizAttemptService.ANSWER_INVALID);
        refusedWith(() -> service.saveAnswer(STUDENT, 9L, 602L, new LmsQuizAttemptService.AnswerInput(null, null, "x".repeat(501))),
                HttpStatus.BAD_REQUEST, LmsQuizAttemptService.ANSWER_INVALID);
        refusedWith(() -> service.saveAnswer(STUDENT, 9L, 999L, new LmsQuizAttemptService.AnswerInput(null, null, null)),
                HttpStatus.NOT_FOUND, LmsQuizAttemptService.QUESTION_UNAVAILABLE);
    }

    @Test
    void noAnswerIsTakenAfterTheDeadlineOrHandingIn() {
        LmsQuizAttempt attempt = new LmsQuizAttempt(quiz, enrollment, 1, NOW);
        owned(9, attempt);
        clock.now = NOW.plusSeconds(1200).plus(LmsQuizAttempt.GRACE).plusSeconds(1);
        refusedWith(() -> service.saveAnswer(STUDENT, 9L, 601L, new LmsQuizAttemptService.AnswerInput(List.of(1), null, null)),
                HttpStatus.CONFLICT, LmsQuizAttemptService.TIME_UP);

        LmsQuizAttemptService.AttemptView handedIn = service.submit(STUDENT, 9L);
        assertThat(handedIn.attempt().getStatus()).isEqualTo(QuizAttemptStatus.EXPIRED);
        refusedWith(() -> service.saveAnswer(STUDENT, 9L, 601L, new LmsQuizAttemptService.AnswerInput(List.of(1), null, null)),
                HttpStatus.CONFLICT, LmsQuizAttemptService.HANDED_IN);
        verify(answers, never()).saveAndFlush(any());
    }

    @Test
    void anotherStudentsAttemptIsUnavailable() {
        owned(9, new LmsQuizAttempt(quiz, enrollment, 1, NOW));
        when(studentAccess.findAccessGrantingEnrollment(OTHER, COURSE_ID))
                .thenReturn(Optional.of(SubmissionFixtures.enrollment(course, OTHER, 42)));

        refusedWith(() -> service.attempt(OTHER, 9L), HttpStatus.NOT_FOUND, LmsQuizAttemptService.ATTEMPT_UNAVAILABLE);
        refusedWith(() -> service.submit(OTHER, 9L), HttpStatus.NOT_FOUND, LmsQuizAttemptService.ATTEMPT_UNAVAILABLE);
        verify(attempts, never()).findByIdForUpdate(9L);
    }

    @Test
    void anAttemptShowsItsQuestionsWithChoicesButNeverForShortAnswers() {
        owned(9, new LmsQuizAttempt(quiz, enrollment, 1, NOW));

        LmsQuizAttemptService.AttemptView view = service.attempt(STUDENT, 9L);
        assertThat(view.questions()).containsExactly(single, written);
        assertThat(view.choicesOf(single)).hasSize(2);
        assertThat(view.choicesOf(written)).isEmpty();
        assertThat(view.totalMarks()).isEqualByComparingTo("2");
    }

    @Test
    void theStudentsQuizShowsAttemptsLeftAndWhetherItCanStart() {
        when(quizzes.findAllByLmsCourseIdAndStatusOrderByIdDesc(COURSE_ID, LmsContentStatus.PUBLISHED)).thenReturn(List.of(quiz));
        LmsQuizAttempt done = new LmsQuizAttempt(quiz, enrollment, 1, NOW.minusSeconds(4000));
        done.submit(NOW.minusSeconds(3900));
        when(attempts.findAllByCourseEnrollmentIdAndQuizIdInOrderByAttemptNumberDesc(any(), any())).thenReturn(List.of(done));

        LmsQuizAttemptService.StudentQuiz seen = service.list(STUDENT, COURSE_ID).getFirst();
        assertThat(seen.attemptsLeft()).isEqualTo(1);
        assertThat(seen.openAttempt()).isNull();
        assertThat(seen.startable()).isTrue();
        refusedWith(() -> service.list(OTHER, COURSE_ID), HttpStatus.NOT_FOUND, LmsQuizAttemptService.COURSE_UNAVAILABLE);
    }
}
