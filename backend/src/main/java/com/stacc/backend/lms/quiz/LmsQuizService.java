package com.stacc.backend.lms.quiz;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionRepository;
import com.stacc.backend.lms.question.QuestionReviewStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LmsQuizService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    public static final String QUIZ_UNAVAILABLE = "Quiz not found or unavailable.";
    public static final String QUIZ_QUESTION_UNAVAILABLE = "This question is not in the quiz.";
    public static final String QUIZ_ARCHIVED = "This quiz is archived and can no longer be changed.";
    public static final String NOT_DRAFT = "Questions can be changed only while the quiz is a draft.";
    public static final String QUESTION_INVALID = "Choose an active question from this course’s question bank.";
    public static final String ALREADY_IN_QUIZ = "This question is already in the quiz.";
    public static final String TOO_MANY = "A quiz can hold at most " + LmsQuiz.MAX_QUESTIONS + " questions.";
    public static final String ORDER_INVALID = "List every question of the quiz exactly once.";
    public static final String NO_QUESTIONS = "Add at least one question before publishing.";

    private final LmsQuizRepository quizzes;
    private final LmsQuizQuestionRepository links;
    private final LmsQuestionRepository questions;
    private final LmsCourseRepository lmsCourses;
    private final LmsFacultyAccessService facultyAccess;
    private final Clock clock;
    private final LmsQuizNotifications notifications;

    public LmsQuizService(LmsQuizRepository quizzes, LmsQuizQuestionRepository links, LmsQuestionRepository questions,
            LmsCourseRepository lmsCourses, LmsFacultyAccessService facultyAccess, Clock clock,
            LmsQuizNotifications notifications) {
        this.quizzes = quizzes;
        this.links = links;
        this.questions = questions;
        this.lmsCourses = lmsCourses;
        this.facultyAccess = facultyAccess;
        this.clock = clock;
        this.notifications = notifications;
    }

    public record QuizSummary(LmsQuiz quiz, long questionCount, BigDecimal totalMarks) {
    }

    public record QuizDetail(LmsQuiz quiz, List<LmsQuizQuestion> questions) {

        public BigDecimal totalMarks() {
            return questions.stream().map(LmsQuizQuestion::getMarks).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        }
    }

    @Transactional(readOnly = true)
    public List<QuizSummary> list(Long userAccountId, Long lmsCourseId) {
        requireCourse(userAccountId, lmsCourseId);
        List<LmsQuiz> found = quizzes.findAllByLmsCourseIdOrderByIdDesc(lmsCourseId);
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, LmsQuizQuestionRepository.QuizTotals> totals = links.totalsFor(found.stream().map(LmsQuiz::getId).toList())
                .stream().collect(Collectors.toMap(LmsQuizQuestionRepository.QuizTotals::getQuizId, Function.identity()));
        return found.stream().map(quiz -> {
            LmsQuizQuestionRepository.QuizTotals total = totals.get(quiz.getId());
            return total == null
                    ? new QuizSummary(quiz, 0, BigDecimal.ZERO.setScale(2))
                    : new QuizSummary(quiz, total.getQuestionCount(), total.getTotalMarks());
        }).toList();
    }

    @Transactional
    public LmsQuiz create(Long userAccountId, Long lmsCourseId, LmsQuizSettings settings) {
        requireCourse(userAccountId, lmsCourseId);
        LmsCourse lmsCourse = lmsCourses.findById(lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        return quizzes.saveAndFlush(valid(() -> new LmsQuiz(lmsCourse, userAccountId, settings)));
    }

    @Transactional(readOnly = true)
    public QuizDetail detail(Long userAccountId, Long quizId) {
        requireQuiz(userAccountId, quizId);
        LmsQuiz quiz = quizzes.findById(quizId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, QUIZ_UNAVAILABLE));
        return detailOf(quiz);
    }

    @Transactional
    public QuizDetail update(Long userAccountId, Long quizId, LmsQuizSettings settings) {
        LmsQuiz quiz = lockedNotArchived(userAccountId, quizId);
        valid(() -> {
            quiz.edit(settings);
            return quiz;
        });
        quizzes.saveAndFlush(quiz);
        return detailOf(quiz);
    }

    @Transactional
    public QuizDetail addQuestion(Long userAccountId, Long quizId, Long questionId, BigDecimal marks) {
        LmsQuiz quiz = lockedDraft(userAccountId, quizId);
        LmsQuestion question = questionId == null ? null : questions.findById(questionId).orElse(null);
        if (question == null || !question.isActive() || !question.getLmsCourse().getId().equals(quiz.getLmsCourse().getId())) {
            throw new ApiException(HttpStatus.CONFLICT, QUESTION_INVALID);
        }
        List<LmsQuizQuestion> current = links.findAllForUpdate(quizId);
        if (current.stream().anyMatch(link -> link.getQuestion().getId().equals(questionId))) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_IN_QUIZ);
        }
        if (current.size() >= LmsQuiz.MAX_QUESTIONS) {
            throw new ApiException(HttpStatus.CONFLICT, TOO_MANY);
        }
        links.saveAndFlush(valid(() -> new LmsQuizQuestion(quiz, question, current.size() + 1,
                marks == null ? question.getMarks() : marks)));
        return detailOf(quiz);
    }

    @Transactional
    public QuizDetail changeMarks(Long userAccountId, Long quizId, Long quizQuestionId, BigDecimal marks) {
        LmsQuiz quiz = lockedDraft(userAccountId, quizId);
        LmsQuizQuestion link = linkOf(quizId, quizQuestionId);
        valid(() -> {
            link.changeMarks(marks);
            return link;
        });
        links.saveAndFlush(link);
        return detailOf(quiz);
    }

    @Transactional
    public QuizDetail removeQuestion(Long userAccountId, Long quizId, Long quizQuestionId) {
        LmsQuiz quiz = lockedDraft(userAccountId, quizId);
        LmsQuizQuestion removed = linkOf(quizId, quizQuestionId);
        links.delete(removed);
        links.flush();
        List<LmsQuizQuestion> remaining = links.findAllForUpdate(quizId);
        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).moveTo(index + 1);
        }
        links.saveAllAndFlush(remaining);
        return detailOf(quiz);
    }

    @Transactional
    public QuizDetail reorder(Long userAccountId, Long quizId, List<Long> quizQuestionIds) {
        LmsQuiz quiz = lockedDraft(userAccountId, quizId);
        Map<Long, LmsQuizQuestion> current = links.findAllForUpdate(quizId).stream()
                .collect(Collectors.toMap(LmsQuizQuestion::getId, Function.identity()));
        if (quizQuestionIds == null || quizQuestionIds.size() != current.size()
                || new HashSet<>(quizQuestionIds).size() != quizQuestionIds.size()
                || !current.keySet().containsAll(quizQuestionIds)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ORDER_INVALID);
        }
        for (int index = 0; index < quizQuestionIds.size(); index++) {
            current.get(quizQuestionIds.get(index)).moveTo(index + 1);
        }
        links.saveAllAndFlush(current.values());
        return detailOf(quiz);
    }

    @Transactional
    public QuizDetail publish(Long userAccountId, Long quizId) {
        LmsQuiz quiz = lockedNotArchived(userAccountId, quizId);
        if (!quiz.isDraft()) {
            return detailOf(quiz);
        }
        List<LmsQuizQuestion> current = links.findAllForUpdate(quizId);
        if (current.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, NO_QUESTIONS);
        }
        Map<Long, LmsQuestion> locked = questions.findAllByIdForShare(
                current.stream().map(link -> link.getQuestion().getId()).toList()).stream()
                .collect(Collectors.toMap(LmsQuestion::getId, Function.identity()));
        for (LmsQuizQuestion link : current) {
            LmsQuestion question = locked.get(link.getQuestion().getId());
            String number = "Question " + link.getPosition();
            if (!question.isActive()) {
                throw new ApiException(HttpStatus.CONFLICT, number + " is archived. Remove it before publishing.");
            }
            if (question.getReviewStatus() != QuestionReviewStatus.REVIEWED) {
                throw new ApiException(HttpStatus.CONFLICT, number + " needs review before the quiz can be published.");
            }
            if (quiz.getType() == QuizType.PRACTICE && !question.isPracticeAllowed()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        number + " is not offered for practice, so it cannot be in a practice quiz.");
            }
        }
        quiz.publish(clock.instant().truncatedTo(ChronoUnit.MICROS));
        quizzes.saveAndFlush(quiz);
        notifications.published(quiz);
        return detailOf(quiz);
    }

    @Transactional
    public QuizDetail archive(Long userAccountId, Long quizId) {
        LmsQuiz quiz = locked(userAccountId, quizId);
        quiz.archive();
        quizzes.saveAndFlush(quiz);
        return detailOf(quiz);
    }

    private QuizDetail detailOf(LmsQuiz quiz) {
        return new QuizDetail(quiz, links.findAllForQuiz(quiz.getId()));
    }

    private LmsQuizQuestion linkOf(Long quizId, Long quizQuestionId) {
        return (quizQuestionId == null ? null : links.findById(quizQuestionId))
                .filter(link -> link.getQuiz().getId().equals(quizId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, QUIZ_QUESTION_UNAVAILABLE));
    }

    private LmsQuiz lockedDraft(Long userAccountId, Long quizId) {
        LmsQuiz quiz = lockedNotArchived(userAccountId, quizId);
        if (!quiz.isDraft()) {
            throw new ApiException(HttpStatus.CONFLICT, NOT_DRAFT);
        }
        return quiz;
    }

    private LmsQuiz lockedNotArchived(Long userAccountId, Long quizId) {
        LmsQuiz quiz = locked(userAccountId, quizId);
        if (quiz.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, QUIZ_ARCHIVED);
        }
        return quiz;
    }

    private LmsQuiz locked(Long userAccountId, Long quizId) {
        Long lmsCourseId = requireQuiz(userAccountId, quizId);
        lmsCourses.findByIdForUpdate(lmsCourseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, QUIZ_UNAVAILABLE));
        return quizzes.findByIdForUpdate(quizId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, QUIZ_UNAVAILABLE));
    }

    private Long requireQuiz(Long userAccountId, Long quizId) {
        Long lmsCourseId = quizId == null ? null : quizzes.findLmsCourseIdById(quizId).orElse(null);
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, QUIZ_UNAVAILABLE);
        }
        return lmsCourseId;
    }

    private void requireCourse(Long userAccountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }

    private static <T> T valid(Supplier<T> make) {
        try {
            return make.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        } catch (IllegalStateException conflict) {
            throw new ApiException(HttpStatus.CONFLICT, conflict.getMessage());
        }
    }
}
