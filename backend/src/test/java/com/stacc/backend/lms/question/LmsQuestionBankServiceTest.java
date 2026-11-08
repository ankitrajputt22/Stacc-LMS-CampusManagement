package com.stacc.backend.lms.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.outcome.LmsCourseOutcome;
import com.stacc.backend.lms.outcome.LmsCourseOutcomeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class LmsQuestionBankServiceTest {

    private static final Long FACULTY = 21L;
    private static final Long OTHER = 22L;
    private static final Long COURSE_ID = 7L;
    private static final Long QUESTION_ID = 301L;

    private final LmsQuestionRepository questions = mock(LmsQuestionRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsCourseOutcomeRepository outcomes = mock(LmsCourseOutcomeRepository.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsQuestionBankService service = new LmsQuestionBankService(questions, lmsCourses, outcomes, facultyAccess);
    private final LmsCourse course = ContentFixtures.lmsCourse(COURSE_ID);
    private LmsQuestion stored;

    private static void refusedWith(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }

    private LmsCourseOutcome outcome(long id, LmsCourse of) {
        LmsCourseOutcome outcome = new LmsCourseOutcome(of, 1, "Model data.");
        ReflectionTestUtils.setField(outcome, "id", id);
        when(outcomes.findWithCourseById(id)).thenReturn(Optional.of(outcome));
        return outcome;
    }

    @BeforeEach
    void setUp() {
        stored = new LmsQuestion(course, FACULTY, QuestionFixtures.singleChoice("Q"));
        ReflectionTestUtils.setField(stored, "id", QUESTION_ID);
        when(facultyAccess.canAccessCourse(FACULTY, COURSE_ID)).thenReturn(true);
        when(lmsCourses.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(lmsCourses.findByIdForUpdate(COURSE_ID)).thenReturn(Optional.of(course));
        when(questions.findLmsCourseIdById(QUESTION_ID)).thenReturn(Optional.of(COURSE_ID));
        when(questions.findByIdForUpdate(QUESTION_ID)).thenReturn(Optional.of(stored));
        when(questions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void aQuestionIsAddedByTheTeacherWithAnActiveOutcomeOfTheCourse() {
        LmsCourseOutcome outcome = outcome(91, course);

        LmsQuestion added = service.add(FACULTY, COURSE_ID, QuestionFixtures.singleChoice("Q"), 91L);

        assertThat(added.getCreatedByAccountId()).isEqualTo(FACULTY);
        assertThat(added.getOutcome()).isSameAs(outcome);
        assertThat(added.getReviewStatus()).isEqualTo(QuestionReviewStatus.NEEDS_REVIEW);
    }

    @Test
    void anOutcomeOfAnotherCourseOrAnArchivedOneIsRefused() {
        outcome(92, ContentFixtures.lmsCourse(8));
        outcome(93, course).archive();
        when(outcomes.findWithCourseById(94L)).thenReturn(Optional.empty());

        for (Long outcomeId : List.of(92L, 93L, 94L)) {
            refusedWith(() -> service.add(FACULTY, COURSE_ID, QuestionFixtures.singleChoice("Q"), outcomeId),
                    HttpStatus.CONFLICT, LmsQuestionBankService.OUTCOME_INVALID);
        }
        verify(questions, never()).saveAndFlush(any());
    }

    @Test
    void anArchivedOutcomeTheQuestionAlreadyHasMayStayWhenEditing() {
        LmsCourseOutcome outcome = outcome(93, course);
        stored.edit(QuestionFixtures.singleChoice("Q").withOutcome(outcome));
        outcome.archive();

        assertThat(service.edit(FACULTY, QUESTION_ID, QuestionFixtures.singleChoice("Edited"), 93L).getOutcome()).isSameAs(outcome);
    }

    @Test
    void typeRulesAnswerABadRequestWithTheirOwnMessage() {
        LmsQuestionContent noCorrect = QuestionFixtures.of(QuestionType.SINGLE_CHOICE,
                List.of(QuestionFixtures.option("A", false), QuestionFixtures.option("B", false)), null, null);

        refusedWith(() -> service.add(FACULTY, COURSE_ID, noCorrect, null), HttpStatus.BAD_REQUEST,
                "A single-choice question needs exactly one correct option.");
        refusedWith(() -> service.edit(FACULTY, QUESTION_ID, noCorrect, null), HttpStatus.BAD_REQUEST,
                "A single-choice question needs exactly one correct option.");
        assertThat(stored.getText()).isEqualTo("Q");
    }

    @Test
    void practiceNeedsAReviewAndAnEditEndsIt() {
        refusedWith(() -> service.allowPractice(FACULTY, QUESTION_ID, true), HttpStatus.CONFLICT, LmsQuestionBankService.NOT_REVIEWED);

        service.markReviewed(FACULTY, QUESTION_ID);
        assertThat(service.allowPractice(FACULTY, QUESTION_ID, true).isPracticeAllowed()).isTrue();

        LmsQuestion edited = service.edit(FACULTY, QUESTION_ID, QuestionFixtures.singleChoice("Edited"), null);
        assertThat(edited.isPracticeAllowed()).isFalse();
        assertThat(edited.getReviewStatus()).isEqualTo(QuestionReviewStatus.NEEDS_REVIEW);
        assertThat(service.allowPractice(FACULTY, QUESTION_ID, false).isPracticeAllowed()).isFalse();
    }

    @Test
    void anArchivedQuestionRefusesChangesButArchivingAgainIsQuiet() {
        service.archive(FACULTY, QUESTION_ID);

        refusedWith(() -> service.markReviewed(FACULTY, QUESTION_ID), HttpStatus.CONFLICT, LmsQuestionBankService.QUESTION_ARCHIVED);
        refusedWith(() -> service.edit(FACULTY, QUESTION_ID, QuestionFixtures.singleChoice("E"), null), HttpStatus.CONFLICT,
                LmsQuestionBankService.QUESTION_ARCHIVED);
        refusedWith(() -> service.allowPractice(FACULTY, QUESTION_ID, false), HttpStatus.CONFLICT,
                LmsQuestionBankService.QUESTION_ARCHIVED);
        assertThat(service.archive(FACULTY, QUESTION_ID).getStatus()).isEqualTo(QuestionStatus.ARCHIVED);
    }

    @Test
    void someoneWhoDoesNotTeachTheCourseGetsTheSameNotFoundAsForAMissingQuestion() {
        when(questions.findLmsCourseIdById(999L)).thenReturn(Optional.empty());

        refusedWith(() -> service.list(OTHER, COURSE_ID), HttpStatus.NOT_FOUND, LmsQuestionBankService.COURSE_UNAVAILABLE);
        refusedWith(() -> service.add(OTHER, COURSE_ID, QuestionFixtures.singleChoice("Q"), null), HttpStatus.NOT_FOUND,
                LmsQuestionBankService.COURSE_UNAVAILABLE);
        for (Long questionId : new Long[] {QUESTION_ID, 999L, null}) {
            refusedWith(() -> service.markReviewed(OTHER, questionId), HttpStatus.NOT_FOUND, LmsQuestionBankService.QUESTION_UNAVAILABLE);
            refusedWith(() -> service.archive(OTHER, questionId), HttpStatus.NOT_FOUND, LmsQuestionBankService.QUESTION_UNAVAILABLE);
        }
        verify(questions, never()).findByIdForUpdate(any());
    }

    @Test
    void aQuestionInAPublishedOrArchivedQuizCanNoLongerBeEditedButOneInADraftQuizCan() {
        when(questions.findQuizStatusesForShare(QUESTION_ID)).thenReturn(List.of("DRAFT", "PUBLISHED"));
        refusedWith(() -> service.edit(FACULTY, QUESTION_ID, QuestionFixtures.singleChoice("New"), null),
                HttpStatus.CONFLICT, LmsQuestionBankService.IN_PUBLISHED_QUIZ);
        when(questions.findQuizStatusesForShare(QUESTION_ID)).thenReturn(List.of("ARCHIVED"));
        refusedWith(() -> service.edit(FACULTY, QUESTION_ID, QuestionFixtures.singleChoice("New"), null),
                HttpStatus.CONFLICT, LmsQuestionBankService.IN_PUBLISHED_QUIZ);
        verify(questions, never()).findByIdForUpdate(QUESTION_ID);

        when(questions.findQuizStatusesForShare(QUESTION_ID)).thenReturn(List.of("DRAFT"));
        assertThat(service.edit(FACULTY, QUESTION_ID, QuestionFixtures.singleChoice("New"), null).getText()).isEqualTo("New");
    }

    @Test
    void someoneWhoDoesNotTeachTheCourseCannotLearnWhetherAQuestionIsInAQuiz() {
        refusedWith(() -> service.edit(OTHER, QUESTION_ID, QuestionFixtures.singleChoice("New"), null),
                HttpStatus.NOT_FOUND, LmsQuestionBankService.QUESTION_UNAVAILABLE);
        refusedWith(() -> service.inPublishedQuizzes(OTHER, COURSE_ID), HttpStatus.NOT_FOUND, LmsQuestionBankService.COURSE_UNAVAILABLE);
        verify(questions, never()).findQuizStatusesForShare(any());
    }
}
