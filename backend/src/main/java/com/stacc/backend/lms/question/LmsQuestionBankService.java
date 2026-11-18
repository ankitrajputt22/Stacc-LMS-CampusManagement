package com.stacc.backend.lms.question;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

@Service
public class LmsQuestionBankService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    public static final String QUESTION_UNAVAILABLE = "Question not found or unavailable.";
    public static final String QUESTION_ARCHIVED = "This question is archived and can no longer be changed.";
    public static final String NOT_REVIEWED = "Mark the question reviewed before offering it for practice.";
    public static final String OUTCOME_INVALID = "Choose an active outcome of this course.";
    public static final String IN_PUBLISHED_QUIZ =
            "This question is in a published quiz, so it can no longer be edited. Add a new question instead.";
    public static final String IN_FINAL_PAPER =
            "This question is in a final exam paper, so it can no longer be edited. Add a new question instead.";

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

    @Transactional(readOnly = true)
    public List<LmsQuestion> list(Long userAccountId, Long lmsCourseId) {
        requireCourse(userAccountId, lmsCourseId);
        return questions.findAllForCourse(lmsCourseId);
    }

    @Transactional(readOnly = true)
    public Set<Long> inPublishedQuizzes(Long userAccountId, Long lmsCourseId) {
        requireCourse(userAccountId, lmsCourseId);
        return new HashSet<>(questions.findIdsInPublishedQuizzes(lmsCourseId));
    }

    @Transactional(readOnly = true)
    public boolean isInPublishedQuiz(Long questionId) {
        return questions.countPublishedQuizUses(questionId) > 0;
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

    public record AiDraft(LmsQuestionContent content, List<LmsQuestionSource> sources, LmsQuestionAiChecks checks, Long outcomeId) {

        public AiDraft(LmsQuestionContent content, List<LmsQuestionSource> sources) {
            this(content, sources, LmsQuestionAiChecks.none(), null);
        }

        public AiDraft(LmsQuestionContent content, List<LmsQuestionSource> sources, LmsQuestionAiChecks checks) {
            this(content, sources, checks, null);
        }
    }

    public record CheckedQuestion(long id, String text, boolean inFinalPaper) {
    }

    @Transactional(readOnly = true)
    public List<CheckedQuestion> forQualityChecks(Long lmsCourseId, int limit) {
        Set<Long> inFinalPapers = new HashSet<>(questions.findIdsInFinalPapers(lmsCourseId));
        return questions.findActiveTexts(lmsCourseId, org.springframework.data.domain.PageRequest.of(0, limit)).stream()
                .map(row -> new CheckedQuestion(((Number) row[0]).longValue(), (String) row[1],
                        inFinalPapers.contains(((Number) row[0]).longValue())))
                .toList();
    }

    @Transactional
    public List<LmsQuestion> addAiDrafts(Long userAccountId, Long lmsCourseId, List<AiDraft> drafts) {
        requireCourse(userAccountId, lmsCourseId);
        LmsCourse lmsCourse = lmsCourses.findById(lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        List<LmsQuestion> added = new java.util.ArrayList<>();
        for (AiDraft draft : drafts) {
            LmsQuestion question;
            try {
                LmsQuestionContent content = draft.outcomeId() == null ? draft.content()
                        : draft.content().withOutcome(outcome(lmsCourseId, draft.outcomeId(), null));
                question = LmsQuestion.aiDraft(lmsCourse, userAccountId, content, draft.sources(), draft.checks());
            } catch (IllegalArgumentException | IllegalStateException refused) {
                continue;
            }
            added.add(questions.save(question));
        }
        questions.flush();
        return List.copyOf(added);
    }

    @Transactional
    public LmsQuestion edit(Long userAccountId, Long questionId, LmsQuestionContent written, Long outcomeId) {
        Long lmsCourseId = requireQuestion(userAccountId, questionId);
        lmsCourses.findByIdForUpdate(lmsCourseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, QUESTION_UNAVAILABLE));
        if (questions.findQuizStatusesForShare(questionId).stream().anyMatch(status -> !"DRAFT".equals(status))) {
            throw new ApiException(HttpStatus.CONFLICT, IN_PUBLISHED_QUIZ);
        }
        if (questions.findPaperStatusesForShare(questionId).stream().anyMatch(status -> !"DRAFT".equals(status))) {
            throw new ApiException(HttpStatus.CONFLICT, IN_FINAL_PAPER);
        }
        LmsQuestion question = active(lockedAfterAccess(questionId));
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

    @Transactional
    public LmsQuestion archive(Long userAccountId, Long questionId) {
        LmsQuestion question = locked(userAccountId, questionId);
        question.archive();
        return question;
    }

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
        return active(locked(userAccountId, questionId));
    }

    private static LmsQuestion active(LmsQuestion question) {
        if (!question.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, QUESTION_ARCHIVED);
        }
        return question;
    }

    private LmsQuestion locked(Long userAccountId, Long questionId) {
        requireQuestion(userAccountId, questionId);
        return lockedAfterAccess(questionId);
    }

    @Transactional(readOnly = true)
    public LmsQuestion find(Long userAccountId, Long questionId) {
        requireQuestion(userAccountId, questionId);
        return questions.findById(questionId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, QUESTION_UNAVAILABLE));
    }

    private Long requireQuestion(Long userAccountId, Long questionId) {
        Long lmsCourseId = questionId == null ? null : questions.findLmsCourseIdById(questionId).orElse(null);
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, QUESTION_UNAVAILABLE);
        }
        return lmsCourseId;
    }

    private LmsQuestion lockedAfterAccess(Long questionId) {
        return questions.findByIdForUpdate(questionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, QUESTION_UNAVAILABLE));
    }

    private void requireCourse(Long userAccountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }

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
