package com.stacc.backend.lms.question;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.outcome.LmsCourseOutcome;
import com.stacc.backend.lms.outcome.LmsCourseOutcomeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The question bank of a course, for the faculty who teach it. Every action checks the faculty access policy; for an
 * existing question the course comes from the stored question, never from the client. Changes lock the question row
 * first, so an edit and a review or practice change never overwrite each other. Students have no route here: a
 * question carries its answers.
 */
@Service
public class LmsQuestionBankService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    public static final String QUESTION_UNAVAILABLE = "Question not found or unavailable.";
    public static final String QUESTION_ARCHIVED = "This question is archived and can no longer be changed.";
    public static final String NOT_REVIEWED = "Mark the question reviewed before offering it for practice.";
    public static final String OUTCOME_INVALID = "Choose an active outcome of this course.";

    private final LmsQuestionRepository questions;
    private final LmsCourseRepository lmsCourses;
    private final LmsCourseOutcomeRepository outcomes;
    private final LmsFacultyAccessService facultyAccess;

    public LmsQuestionBankService(LmsQuestionRepository questions, LmsCourseRepository lmsCourses,
            LmsCourseOutcomeRepository outcomes, LmsFacultyAccessService facultyAccess) {
        this.questions = questions;
        this.lmsCourses = lmsCourses;
        this.outcomes = outcomes;
        this.facultyAccess = facultyAccess;
    }

    /** Every question of the course, archived ones included, newest first. */
    @Transactional(readOnly = true)
    public List<LmsQuestion> list(Long userAccountId, Long lmsCourseId) {
        requireCourse(userAccountId, lmsCourseId);
        return questions.findAllForCourse(lmsCourseId);
    }

    @Transactional
    public LmsQuestion add(Long userAccountId, Long lmsCourseId, LmsQuestionContent written, Long outcomeId) {
        requireCourse(userAccountId, lmsCourseId);
        LmsCourse lmsCourse = lmsCourses.findById(lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        LmsQuestionContent content = written.withOutcome(outcome(lmsCourseId, outcomeId, null));
        LmsQuestion question = valid(() -> new LmsQuestion(lmsCourse, userAccountId, content));
        return questions.saveAndFlush(question);
    }

    /** Replaces what the question says; it then needs a new review and is no longer offered for practice. */
    @Transactional
    public LmsQuestion edit(Long userAccountId, Long questionId, LmsQuestionContent written, Long outcomeId) {
        LmsQuestion question = lockedActive(userAccountId, questionId);
        LmsQuestionContent content = written.withOutcome(
                outcome(question.getLmsCourse().getId(), outcomeId, question.getOutcome()));
        change(question, current -> current.edit(content));
        return questions.saveAndFlush(question);
    }

    @Transactional
    public LmsQuestion markReviewed(Long userAccountId, Long questionId) {
        LmsQuestion question = lockedActive(userAccountId, questionId);
        question.markReviewed();
        return question;
    }

    @Transactional
    public LmsQuestion allowPractice(Long userAccountId, Long questionId, boolean allowed) {
        LmsQuestion question = lockedActive(userAccountId, questionId);
        if (allowed && question.getReviewStatus() != QuestionReviewStatus.REVIEWED) {
            throw new ApiException(HttpStatus.CONFLICT, NOT_REVIEWED);
        }
        question.allowPractice(allowed);
        return question;
    }

    /** Archives the question; archiving again changes nothing. Nothing deletes or restores a question. */
    @Transactional
    public LmsQuestion archive(Long userAccountId, Long questionId) {
        LmsQuestion question = locked(userAccountId, questionId);
        question.archive();
        return question;
    }

    // An active outcome of this course, or the question's current outcome when it was archived after being chosen.
    private LmsCourseOutcome outcome(Long lmsCourseId, Long outcomeId, LmsCourseOutcome current) {
        if (outcomeId == null) {
            return null;
        }
        if (current != null && outcomeId.equals(current.getId())) {
            return current;
        }
        LmsCourseOutcome outcome = outcomes.findWithCourseById(outcomeId).orElse(null);
        if (outcome == null || !outcome.isActive() || !outcome.getLmsCourse().getId().equals(lmsCourseId)) {
            throw new ApiException(HttpStatus.CONFLICT, OUTCOME_INVALID);
        }
        return outcome;
    }

    private LmsQuestion lockedActive(Long userAccountId, Long questionId) {
        LmsQuestion question = locked(userAccountId, questionId);
        if (!question.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, QUESTION_ARCHIVED);
        }
        return question;
    }

    // Checks access from the stored question's course before taking the lock. Only the course ID is read first, so the
    // locking read is the first to load the question and sees its latest committed state.
    private LmsQuestion locked(Long userAccountId, Long questionId) {
        Long lmsCourseId = questionId == null ? null : questions.findLmsCourseIdById(questionId).orElse(null);
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, QUESTION_UNAVAILABLE);
        }
        return questions.findByIdForUpdate(questionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, QUESTION_UNAVAILABLE));
    }

    private void requireCourse(Long userAccountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }

    // The type rules live on the question; their messages are written for people.
    private static LmsQuestion valid(Supplier<LmsQuestion> make) {
        try {
            return make.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }

    private static void change(LmsQuestion question, Consumer<LmsQuestion> change) {
        valid(() -> {
            change.accept(question);
            return question;
        });
    }
}
