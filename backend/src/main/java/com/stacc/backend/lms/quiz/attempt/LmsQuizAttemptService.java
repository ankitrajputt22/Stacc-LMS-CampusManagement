package com.stacc.backend.lms.quiz.attempt;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionOption;
import com.stacc.backend.lms.question.QuestionType;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizQuestion;
import com.stacc.backend.lms.quiz.LmsQuizQuestionRepository;
import com.stacc.backend.lms.quiz.LmsQuizRepository;
import com.stacc.backend.lms.quiz.QuizAvailability;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Students take the published quizzes of courses they may use now. The student is always the signed-in account, and
 * the course enrollment is found on the server through {@link LmsStudentAccessService}; an attempt someone else made,
 * or one in a course the student can no longer use, is the same 404 as a missing one.
 *
 * <p>Starting locks the course enrollment (still enrolled) and then the enrollment's attempts at the quiz, so a second
 * start at the same moment resumes the first attempt instead of making another, and the attempt limit holds. Saving an
 * answer and handing in lock the attempt row, so an answer is never taken after the attempt was handed in. The server's
 * clock decides every deadline; reading never changes anything, so an attempt past its deadline shows as time up until
 * it is handed in or a new one starts.
 */
@Service
public class LmsQuizAttemptService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    public static final String QUIZ_UNAVAILABLE = "Quiz not found or unavailable.";
    public static final String ATTEMPT_UNAVAILABLE = "Quiz attempt not found or unavailable.";
    public static final String QUESTION_UNAVAILABLE = "This question is not part of the quiz.";
    public static final String NOT_OPEN = "This quiz is not open yet.";
    public static final String CLOSED = "This quiz has closed.";
    public static final String NO_ATTEMPTS_LEFT = "You have used every attempt this quiz allows.";
    public static final String TIME_UP = "Time is up for this attempt. Answers saved before the deadline count.";
    public static final String HANDED_IN = "This attempt has already been handed in.";
    public static final String ANSWER_INVALID = "This answer does not fit the question.";
    public static final int MAX_SHORT_ANSWER = 500;
    public static final int MAX_WRITTEN_ANSWER = 20_000;

    private final LmsStudentAccessService studentAccess;
    private final LmsQuizRepository quizzes;
    private final LmsQuizQuestionRepository quizQuestions;
    private final LmsQuizAttemptRepository attempts;
    private final LmsQuizAnswerRepository answers;
    private final CourseEnrollmentRepository courseEnrollments;
    private final Clock clock;
    private final QuizScores scores;

    public LmsQuizAttemptService(LmsStudentAccessService studentAccess, LmsQuizRepository quizzes,
            LmsQuizQuestionRepository quizQuestions, LmsQuizAttemptRepository attempts, LmsQuizAnswerRepository answers,
            CourseEnrollmentRepository courseEnrollments, Clock clock, QuizScores scores) {
        this.studentAccess = studentAccess;
        this.quizzes = quizzes;
        this.quizQuestions = quizQuestions;
        this.attempts = attempts;
        this.answers = answers;
        this.courseEnrollments = courseEnrollments;
        this.clock = clock;
        this.scores = scores;
    }

    /** A published quiz as one student sees it: its size, and that student's attempts, newest first. */
    public record StudentQuiz(LmsQuiz quiz, long questionCount, BigDecimal totalMarks, List<LmsQuizAttempt> attempts,
            Map<Long, QuizScoring.AttemptScore> scores, Instant now) {

        /** Whether the student may see this attempt's result now, and if not, why. */
        public QuizResultStatus resultStatus(LmsQuizAttempt attempt) {
            return QuizResultStatus.of(attempt, scores.get(attempt.getId()), now);
        }

        /** The attempt's score once its result is released; null before. */
        public BigDecimal releasedScore(LmsQuizAttempt attempt) {
            return resultStatus(attempt) == QuizResultStatus.RELEASED ? scores.get(attempt.getId()).score() : null;
        }

        public QuizAvailability availability() {
            return quiz.availabilityAt(now);
        }

        /** The attempt still open for answers, if any. */
        public LmsQuizAttempt openAttempt() {
            return attempts.stream().filter(attempt -> attempt.stateAt(now) == QuizAttemptState.IN_PROGRESS)
                    .findFirst().orElse(null);
        }

        /** Attempts the student may still start; null when unlimited. */
        public Integer attemptsLeft() {
            return quiz.getMaxAttempts() == null ? null : Math.max(0, quiz.getMaxAttempts() - attempts.size());
        }

        /** Whether Start (or Resume) would work now. */
        public boolean startable() {
            return openAttempt() != null
                    || availability() == QuizAvailability.OPEN && (attemptsLeft() == null || attemptsLeft() > 0);
        }
    }

    /** An attempt with its quiz, its questions in the attempt's order, and the answers saved so far by question. */
    public record AttemptView(LmsQuizAttempt attempt, List<LmsQuizQuestion> questions, Map<Long, LmsQuizAnswer> answers,
            QuizScoring.AttemptScore score, Instant now) {

        public QuizResultStatus resultStatus() {
            return QuizResultStatus.of(attempt, score, now);
        }

        public BigDecimal totalMarks() {
            return questions.stream().map(LmsQuizQuestion::getMarks).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        }

        /** The question's choices in the attempt's order; none for questions whose options are their answers. */
        public List<LmsQuestionOption> choicesOf(LmsQuizQuestion quizQuestion) {
            LmsQuestion question = quizQuestion.getQuestion();
            if (question.getType() != QuestionType.SINGLE_CHOICE && question.getType() != QuestionType.MULTIPLE_CHOICE) {
                return List.of();
            }
            return QuizAttemptOrder.options(attempt.getId(), quizQuestion.getId(), question.getOptions(),
                    attempt.getQuiz().isShuffleOptions());
        }
    }

    /** What a student sends for one question; the parts its type does not take must be empty. */
    public record AnswerInput(Collection<Integer> selectedOptions, Boolean trueFalseAnswer, String text) {
    }

    /** The published quizzes of a course the student may use now, newest first, each with the student's attempts. */
    @Transactional(readOnly = true)
    public List<StudentQuiz> list(Long userAccountId, Long lmsCourseId) {
        CourseEnrollment enrollment = studentAccess.findAccessGrantingEnrollment(userAccountId, lmsCourseId)
                .orElseThrow(() -> notFound(COURSE_UNAVAILABLE));
        return studentQuizzes(enrollment, quizzes.findAllByLmsCourseIdAndStatusOrderByIdDesc(lmsCourseId, LmsContentStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public StudentQuiz quiz(Long userAccountId, Long lmsCourseId, Long quizId) {
        CourseEnrollment enrollment = studentAccess.findAccessGrantingEnrollment(userAccountId, lmsCourseId)
                .orElseThrow(() -> notFound(QUIZ_UNAVAILABLE));
        return studentQuizzes(enrollment, List.of(visibleQuiz(lmsCourseId, quizId))).getFirst();
    }

    /**
     * Starts a new attempt, or returns the one still open. The quiz must be open, and the student must have an attempt
     * left. An attempt left open past its deadline is closed as expired first.
     */
    @Transactional
    public LmsQuizAttempt start(Long userAccountId, Long lmsCourseId, Long quizId) {
        CourseEnrollment enrollment = studentAccess.findAccessGrantingEnrollment(userAccountId, lmsCourseId)
                .orElseThrow(() -> notFound(QUIZ_UNAVAILABLE));
        LmsQuiz quiz = visibleQuiz(lmsCourseId, quizId);
        // A drop or withdrawal that finished meanwhile ends access here too.
        CourseEnrollment locked = courseEnrollments.findByIdForUpdate(enrollment.getId())
                .filter(current -> current.getStatus() == CourseEnrollmentStatus.ENROLLED)
                .orElseThrow(() -> notFound(QUIZ_UNAVAILABLE));
        List<LmsQuizAttempt> earlier = attempts.findAllForUpdate(quizId, locked.getId());
        Instant now = now();
        for (LmsQuizAttempt attempt : earlier) {
            if (attempt.stateAt(now) == QuizAttemptState.IN_PROGRESS) {
                return attempt;
            }
            if (attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS) {
                attempt.submit(now);
                attempts.saveAndFlush(attempt);
            }
        }
        switch (quiz.availabilityAt(now)) {
            case NOT_OPEN -> throw new ApiException(HttpStatus.CONFLICT, NOT_OPEN);
            case CLOSED -> throw new ApiException(HttpStatus.CONFLICT, CLOSED);
            default -> { }
        }
        if (quiz.getMaxAttempts() != null && earlier.size() >= quiz.getMaxAttempts()) {
            throw new ApiException(HttpStatus.CONFLICT, NO_ATTEMPTS_LEFT);
        }
        int number = earlier.stream().mapToInt(LmsQuizAttempt::getAttemptNumber).max().orElse(0) + 1;
        return attempts.saveAndFlush(new LmsQuizAttempt(quiz, locked, number, now));
    }

    /** The student's own attempt with its questions (never their answers' keys) and the answers saved so far. */
    @Transactional(readOnly = true)
    public AttemptView attempt(Long userAccountId, Long attemptId) {
        requireOwn(userAccountId, attemptId);
        LmsQuizAttempt attempt = attempts.findById(attemptId).orElseThrow(() -> notFound(ATTEMPT_UNAVAILABLE));
        return viewOf(attempt);
    }

    /**
     * Saves the answer to one question while the attempt is open, replacing an earlier one. Times are the server's:
     * after the deadline (and its short grace) the answer is refused.
     */
    @Transactional
    public LmsQuizAnswer saveAnswer(Long userAccountId, Long attemptId, Long quizQuestionId, AnswerInput input) {
        requireOwn(userAccountId, attemptId);
        LmsQuizAttempt attempt = attempts.findByIdForUpdate(attemptId).orElseThrow(() -> notFound(ATTEMPT_UNAVAILABLE));
        Instant now = now();
        if (!attempt.acceptsAnswersAt(now)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS ? TIME_UP : HANDED_IN);
        }
        LmsQuizQuestion quizQuestion = (quizQuestionId == null ? null : quizQuestions.findById(quizQuestionId).orElse(null));
        if (quizQuestion == null || !quizQuestion.getQuiz().getId().equals(attempt.getQuiz().getId())) {
            throw notFound(QUESTION_UNAVAILABLE);
        }
        Checked checked = checked(quizQuestion.getQuestion(), input == null ? new AnswerInput(null, null, null) : input);
        LmsQuizAnswer answer = answers.findForUpdate(attemptId, quizQuestionId)
                .orElseGet(() -> new LmsQuizAnswer(attempt, quizQuestion));
        answer.replace(checked.positions(), checked.trueFalse(), checked.text(), now);
        return answers.saveAndFlush(answer);
    }

    /** Hands the attempt in: submitted before its deadline, expired at the deadline after it. Again changes nothing. */
    @Transactional
    public AttemptView submit(Long userAccountId, Long attemptId) {
        requireOwn(userAccountId, attemptId);
        LmsQuizAttempt attempt = attempts.findByIdForUpdate(attemptId).orElseThrow(() -> notFound(ATTEMPT_UNAVAILABLE));
        attempt.submit(now());
        attempts.saveAndFlush(attempt);
        return viewOf(attempt);
    }

    private record Checked(Set<Integer> positions, Boolean trueFalse, String text) {
    }

    // The parts the question's type takes, with chosen options that exist; anything else is refused (400).
    private static Checked checked(LmsQuestion question, AnswerInput input) {
        List<Integer> chosen = input.selectedOptions() == null ? List.of() : List.copyOf(input.selectedOptions());
        String text = input.text() == null || input.text().isBlank() ? null : input.text();
        boolean choice = question.getType() == QuestionType.SINGLE_CHOICE || question.getType() == QuestionType.MULTIPLE_CHOICE;
        boolean written = question.getType() == QuestionType.SHORT_ANSWER || question.getType() == QuestionType.DESCRIPTIVE;
        Set<Integer> known = question.getOptions().stream().map(LmsQuestionOption::getPosition).collect(Collectors.toSet());
        boolean valid = (choice || chosen.isEmpty())
                && (question.getType() == QuestionType.TRUE_FALSE || input.trueFalseAnswer() == null)
                && (written || text == null)
                && new HashSet<>(chosen).size() == chosen.size()
                && known.containsAll(chosen)
                && (question.getType() != QuestionType.SINGLE_CHOICE || chosen.size() <= 1)
                && (text == null || text.length() <= (question.getType() == QuestionType.SHORT_ANSWER ? MAX_SHORT_ANSWER : MAX_WRITTEN_ANSWER));
        if (!valid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ANSWER_INVALID);
        }
        return new Checked(new TreeSet<>(chosen), input.trueFalseAnswer(), text);
    }

    private AttemptView viewOf(LmsQuizAttempt attempt) {
        LmsQuiz quiz = attempt.getQuiz();
        List<LmsQuizQuestion> inQuizOrder = quizQuestions.findAllForQuiz(quiz.getId());
        Map<Long, LmsQuizAnswer> saved = answers.findAllByAttemptId(attempt.getId()).stream()
                .collect(Collectors.toMap(answer -> answer.getQuizQuestion().getId(), Function.identity()));
        return new AttemptView(attempt, QuizAttemptOrder.questions(attempt.getId(), inQuizOrder, quiz.isShuffleQuestions()),
                saved, scores.of(attempt), now());
    }

    // The attempt must have been made through the enrollment that gives this account the course now, for a quiz that
    // is still published. Only IDs are read first, so a following locking read is the first to load the attempt.
    private void requireOwn(Long userAccountId, Long attemptId) {
        LmsQuizAttemptRepository.AttemptOwner owner = attemptId == null ? null : attempts.findOwnerById(attemptId).orElse(null);
        boolean own = owner != null && owner.getQuizStatus() == LmsContentStatus.PUBLISHED
                && studentAccess.findAccessGrantingEnrollment(userAccountId, owner.getLmsCourseId())
                        .map(enrollment -> enrollment.getId().equals(owner.getCourseEnrollmentId()))
                        .orElse(false);
        if (!own) {
            throw notFound(ATTEMPT_UNAVAILABLE);
        }
    }

    private List<StudentQuiz> studentQuizzes(CourseEnrollment enrollment, List<LmsQuiz> published) {
        if (published.isEmpty()) {
            return List.of();
        }
        List<Long> quizIds = published.stream().map(LmsQuiz::getId).toList();
        Map<Long, LmsQuizQuestionRepository.QuizTotals> totals = quizQuestions.totalsFor(quizIds).stream()
                .collect(Collectors.toMap(LmsQuizQuestionRepository.QuizTotals::getQuizId, Function.identity()));
        Map<Long, List<LmsQuizAttempt>> mine = attempts
                .findAllByCourseEnrollmentIdAndQuizIdInOrderByAttemptNumberDesc(enrollment.getId(), quizIds).stream()
                .collect(Collectors.groupingBy(attempt -> attempt.getQuiz().getId()));
        Map<Long, QuizScoring.AttemptScore> scored = scores.of(mine.values().stream().flatMap(List::stream).toList());
        Instant now = now();
        return published.stream().map(quiz -> {
            LmsQuizQuestionRepository.QuizTotals total = totals.get(quiz.getId());
            return new StudentQuiz(quiz, total == null ? 0 : total.getQuestionCount(),
                    total == null ? BigDecimal.ZERO.setScale(2) : total.getTotalMarks(),
                    mine.getOrDefault(quiz.getId(), List.of()), scored, now);
        }).toList();
    }

    // Published, and in the course of the request; the course is checked from the stored quiz.
    private LmsQuiz visibleQuiz(Long lmsCourseId, Long quizId) {
        LmsQuiz quiz = quizId == null ? null : quizzes.findById(quizId).orElse(null);
        if (quiz == null || quiz.getStatus() != LmsContentStatus.PUBLISHED || !quiz.getLmsCourse().getId().equals(lmsCourseId)) {
            throw notFound(QUIZ_UNAVAILABLE);
        }
        return quiz;
    }

    // At the precision the database keeps, so an answer shows the same moment as every later read.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }
}
