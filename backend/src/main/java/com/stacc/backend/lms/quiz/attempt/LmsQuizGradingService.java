package com.stacc.backend.lms.quiz.attempt;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizQuestion;
import com.stacc.backend.lms.quiz.LmsQuizQuestionRepository;
import com.stacc.backend.lms.quiz.LmsQuizRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faculty read the attempts at their quizzes and mark them. Every action checks the faculty access policy on the course
 * found from the stored quiz or attempt. Results can be read for published and archived quizzes; marks are given only
 * for published ones. Marking locks the attempt row first, needs the attempt handed in, and replaces an earlier mark
 * without history. The marker and time come from the server.
 */
@Service
public class LmsQuizGradingService {

    public static final String QUIZ_UNAVAILABLE = "Quiz not found or unavailable.";
    public static final String ATTEMPT_UNAVAILABLE = "Quiz attempt not found or unavailable.";
    public static final String QUESTION_UNAVAILABLE = "This question is not part of the quiz.";
    public static final String NOT_HANDED_IN = "Mark an attempt once it has been handed in.";
    public static final String UNANSWERED = "An unanswered question scores zero and needs no marks.";
    public static final String STILL_OPEN = "This attempt is still in progress. It can be closed once its time is up.";

    private final LmsQuizRepository quizzes;
    private final LmsQuizQuestionRepository quizQuestions;
    private final LmsQuizAttemptRepository attempts;
    private final LmsQuizAnswerRepository answers;
    private final LmsQuizAnswerMarkRepository marks;
    private final LmsFacultyAccessService facultyAccess;
    private final QuizScores scores;
    private final Clock clock;

    public LmsQuizGradingService(LmsQuizRepository quizzes, LmsQuizQuestionRepository quizQuestions,
            LmsQuizAttemptRepository attempts, LmsQuizAnswerRepository answers, LmsQuizAnswerMarkRepository marks,
            LmsFacultyAccessService facultyAccess, QuizScores scores, Clock clock) {
        this.quizzes = quizzes;
        this.quizQuestions = quizQuestions;
        this.attempts = attempts;
        this.answers = answers;
        this.marks = marks;
        this.facultyAccess = facultyAccess;
        this.scores = scores;
        this.clock = clock;
    }

    /** A quiz's attempts with their scores, by student login ID and attempt number. */
    public record QuizResults(LmsQuiz quiz, BigDecimal totalMarks, List<LmsQuizAttempt> attempts,
            Map<Long, QuizScoring.AttemptScore> scores, Instant now) {
    }

    /** One attempt with every question in the quiz's order, its answer, the automatic marks, and the teacher's mark. */
    public record AttemptReview(LmsQuizAttempt attempt, QuizScoring.AttemptScore score, Instant now) {
    }

    @Transactional(readOnly = true)
    public QuizResults results(Long userAccountId, Long quizId) {
        LmsQuiz quiz = quizId == null ? null : quizzes.findById(quizId).orElse(null);
        if (quiz == null || quiz.isDraft() || !facultyAccess.canAccessCourse(userAccountId, quiz.getLmsCourse().getId())) {
            throw notFound(QUIZ_UNAVAILABLE);
        }
        List<LmsQuizAttempt> found = attempts.findAllWithStudentByQuizId(quizId);
        BigDecimal total = quizQuestions.totalsFor(List.of(quizId)).stream().findFirst()
                .map(LmsQuizQuestionRepository.QuizTotals::getTotalMarks).orElse(BigDecimal.ZERO.setScale(2));
        return new QuizResults(quiz, total, found, scores.of(found), now());
    }

    @Transactional(readOnly = true)
    public AttemptReview review(Long userAccountId, Long attemptId) {
        requireAttempt(userAccountId, attemptId, false);
        LmsQuizAttempt attempt = attempts.findWithStudentById(attemptId).orElseThrow(() -> notFound(ATTEMPT_UNAVAILABLE));
        return new AttemptReview(attempt, scores.of(attempt), now());
    }

    /**
     * Gives an answer marks from 0 to the question's marks in the quiz, with optional feedback, replacing an earlier
     * mark. For a choice, true/false, or short answer this is a regrade that takes the place of the automatic marks.
     */
    @Transactional
    public AttemptReview mark(Long userAccountId, Long attemptId, Long quizQuestionId, BigDecimal given, String feedback) {
        requireAttempt(userAccountId, attemptId, true);
        LmsQuizAttempt attempt = attempts.findByIdForUpdate(attemptId).orElseThrow(() -> notFound(ATTEMPT_UNAVAILABLE));
        if (attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, NOT_HANDED_IN);
        }
        LmsQuizQuestion quizQuestion = quizQuestionId == null ? null : quizQuestions.findById(quizQuestionId).orElse(null);
        if (quizQuestion == null || !quizQuestion.getQuiz().getId().equals(attempt.getQuiz().getId())) {
            throw notFound(QUESTION_UNAVAILABLE);
        }
        LmsQuizAnswer answer = answers.findForUpdate(attemptId, quizQuestionId)
                .filter(QuizScoring::answered)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, UNANSWERED));
        LmsQuizAnswerMark mark = marks.findByAnswerIdForUpdate(answer.getId()).orElseGet(() -> new LmsQuizAnswerMark(answer));
        try {
            mark.mark(given, quizQuestion.getMarks(), feedback, userAccountId, now());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
        marks.saveAndFlush(mark);
        return review(userAccountId, attemptId);
    }

    /**
     * Closes an attempt the student left open after its time ran out, as handing it in would: its answers saved before
     * the deadline count. An attempt whose time is not up is refused (409); a handed-in one changes nothing.
     */
    @Transactional
    public AttemptReview close(Long userAccountId, Long attemptId) {
        requireAttempt(userAccountId, attemptId, true);
        LmsQuizAttempt attempt = attempts.findByIdForUpdate(attemptId).orElseThrow(() -> notFound(ATTEMPT_UNAVAILABLE));
        Instant now = now();
        if (attempt.stateAt(now) == QuizAttemptState.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, STILL_OPEN);
        }
        attempt.submit(now);
        attempts.saveAndFlush(attempt);
        return review(userAccountId, attemptId);
    }

    // The course comes from the stored attempt; only its ID is read first, so a following locking read is the first to
    // load the attempt. Marking needs a published quiz; reading also allows an archived one.
    private void requireAttempt(Long userAccountId, Long attemptId, boolean changing) {
        LmsQuizAttemptRepository.AttemptOwner owner = attemptId == null ? null : attempts.findOwnerById(attemptId).orElse(null);
        boolean allowed = owner != null
                && (owner.getQuizStatus() == LmsContentStatus.PUBLISHED
                        || !changing && owner.getQuizStatus() == LmsContentStatus.ARCHIVED)
                && facultyAccess.canAccessCourse(userAccountId, owner.getLmsCourseId());
        if (!allowed) {
            throw notFound(ATTEMPT_UNAVAILABLE);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }
}
