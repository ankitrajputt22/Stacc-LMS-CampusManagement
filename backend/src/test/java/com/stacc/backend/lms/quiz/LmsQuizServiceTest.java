package com.stacc.backend.lms.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionRepository;
import com.stacc.backend.lms.question.QuestionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class LmsQuizServiceTest {

    private static final Long FACULTY = 21L;
    private static final Long OTHER = 22L;
    private static final Long COURSE_ID = 7L;
    private static final Long QUIZ_ID = 501L;
    private static final Instant NOW = Instant.parse("2026-11-10T05:00:00Z");

    private final LmsQuizRepository quizzes = mock(LmsQuizRepository.class);
    private final LmsQuizQuestionRepository links = mock(LmsQuizQuestionRepository.class);
    private final LmsQuestionRepository questions = mock(LmsQuestionRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsQuizService service = new LmsQuizService(quizzes, links, questions, lmsCourses, facultyAccess,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final LmsCourse course = ContentFixtures.lmsCourse(COURSE_ID);
    private final List<LmsQuizQuestion> stored = new ArrayList<>();
    private LmsQuiz quiz;

    private static void refusedWith(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }

    private LmsQuestion question(long id, LmsCourse of) {
        LmsQuestion question = new LmsQuestion(of, FACULTY, QuestionFixtures.singleChoice("Question " + id));
        ReflectionTestUtils.setField(question, "id", id);
        when(questions.findById(id)).thenReturn(Optional.of(question));
        return question;
    }

    private LmsQuizQuestion link(long id, LmsQuestion question) {
        LmsQuizQuestion link = new LmsQuizQuestion(quiz, question, stored.size() + 1, question.getMarks());
        ReflectionTestUtils.setField(link, "id", id);
        stored.add(link);
        when(links.findById(id)).thenReturn(Optional.of(link));
        return link;
    }

    @BeforeEach
    void setUp() {
        quiz = new LmsQuiz(course, FACULTY, QuizFixtures.graded("Unit test"));
        ReflectionTestUtils.setField(quiz, "id", QUIZ_ID);
        when(facultyAccess.canAccessCourse(FACULTY, COURSE_ID)).thenReturn(true);
        when(lmsCourses.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(quizzes.findLmsCourseIdById(QUIZ_ID)).thenReturn(Optional.of(COURSE_ID));
        when(quizzes.findByIdForUpdate(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(quizzes.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(quizzes.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(links.findAllByQuizIdOrderByPosition(QUIZ_ID)).thenAnswer(call -> List.copyOf(stored));
        when(links.findAllForQuiz(QUIZ_ID)).thenAnswer(call -> List.copyOf(stored));
        when(links.saveAndFlush(any())).thenAnswer(call -> {
            stored.add(call.getArgument(0));
            return call.getArgument(0);
        });
    }

    @Test
    void aQuizIsAddedAsADraftByTheTeacher() {
        LmsQuiz added = service.create(FACULTY, COURSE_ID, QuizFixtures.graded("Unit test"));

        assertThat(added.getStatus()).isEqualTo(LmsContentStatus.DRAFT);
        assertThat(added.getCreatedByAccountId()).isEqualTo(FACULTY);
        refusedWith(() -> service.create(FACULTY, COURSE_ID, QuizFixtures.settings("Q", QuizType.GRADED,
                NOW, null, NOW)), HttpStatus.BAD_REQUEST, "The closing time must be after the opening time.");
    }

    @Test
    void someoneWhoDoesNotTeachTheCourseSeesNoQuiz() {
        refusedWith(() -> service.detail(OTHER, QUIZ_ID), HttpStatus.NOT_FOUND, LmsQuizService.QUIZ_UNAVAILABLE);
        refusedWith(() -> service.publish(OTHER, QUIZ_ID), HttpStatus.NOT_FOUND, LmsQuizService.QUIZ_UNAVAILABLE);
        refusedWith(() -> service.list(OTHER, COURSE_ID), HttpStatus.NOT_FOUND, LmsQuizService.COURSE_UNAVAILABLE);
        verify(quizzes, never()).findByIdForUpdate(any());
    }

    @Test
    void aQuestionGoesAtTheEndWithTheBankMarksByDefault() {
        LmsQuestion own = question(41, course);

        LmsQuizService.QuizDetail detail = service.addQuestion(FACULTY, QUIZ_ID, 41L, null);
        assertThat(detail.questions()).singleElement().satisfies(link -> {
            assertThat(link.getQuestion()).isSameAs(own);
            assertThat(link.getPosition()).isEqualTo(1);
            assertThat(link.getMarks()).isEqualByComparingTo("2.00");
        });
    }

    @Test
    void addingRefusesForeignArchivedAndRepeatedQuestions() {
        question(41, course);
        question(42, ContentFixtures.lmsCourse(8));
        question(43, course).archive();
        service.addQuestion(FACULTY, QUIZ_ID, 41L, new BigDecimal("3"));

        refusedWith(() -> service.addQuestion(FACULTY, QUIZ_ID, 41L, null), HttpStatus.CONFLICT, LmsQuizService.ALREADY_IN_QUIZ);
        refusedWith(() -> service.addQuestion(FACULTY, QUIZ_ID, 42L, null), HttpStatus.CONFLICT, LmsQuizService.QUESTION_INVALID);
        refusedWith(() -> service.addQuestion(FACULTY, QUIZ_ID, 43L, null), HttpStatus.CONFLICT, LmsQuizService.QUESTION_INVALID);
        refusedWith(() -> service.addQuestion(FACULTY, QUIZ_ID, 99L, null), HttpStatus.CONFLICT, LmsQuizService.QUESTION_INVALID);
        assertThat(stored).singleElement().extracting(LmsQuizQuestion::getMarks).isEqualTo(new BigDecimal("3.00"));
    }

    @Test
    void questionsChangeOnlyWhileTheQuizIsADraft() {
        link(601, question(41, course));
        quiz.publish(NOW);

        refusedWith(() -> service.addQuestion(FACULTY, QUIZ_ID, 41L, null), HttpStatus.CONFLICT, LmsQuizService.NOT_DRAFT);
        refusedWith(() -> service.changeMarks(FACULTY, QUIZ_ID, 601L, BigDecimal.ONE), HttpStatus.CONFLICT, LmsQuizService.NOT_DRAFT);
        refusedWith(() -> service.removeQuestion(FACULTY, QUIZ_ID, 601L), HttpStatus.CONFLICT, LmsQuizService.NOT_DRAFT);
        refusedWith(() -> service.reorder(FACULTY, QUIZ_ID, List.of(601L)), HttpStatus.CONFLICT, LmsQuizService.NOT_DRAFT);
        quiz.archive();
        refusedWith(() -> service.update(FACULTY, QUIZ_ID, QuizFixtures.graded("New")), HttpStatus.CONFLICT, LmsQuizService.QUIZ_ARCHIVED);
    }

    @Test
    void aReorderNamesEveryQuestionOnce() {
        LmsQuizQuestion first = link(601, question(41, course));
        LmsQuizQuestion second = link(602, question(42, course));

        for (List<Long> wrong : List.of(List.of(601L), List.of(601L, 601L), List.of(601L, 603L), List.of(601L, 602L, 603L))) {
            refusedWith(() -> service.reorder(FACULTY, QUIZ_ID, wrong), HttpStatus.BAD_REQUEST, LmsQuizService.ORDER_INVALID);
        }
        service.reorder(FACULTY, QUIZ_ID, List.of(602L, 601L));
        assertThat(second.getPosition()).isEqualTo(1);
        assertThat(first.getPosition()).isEqualTo(2);
    }

    @Test
    void removingAQuestionMovesTheLaterOnesUp() {
        LmsQuizQuestion first = link(601, question(41, course));
        LmsQuizQuestion second = link(602, question(42, course));
        doAnswer(call -> stored.remove(first)).when(links).delete(first);

        service.removeQuestion(FACULTY, QUIZ_ID, 601L);
        assertThat(second.getPosition()).isEqualTo(1);
        refusedWith(() -> service.removeQuestion(FACULTY, QUIZ_ID, 999L), HttpStatus.NOT_FOUND, LmsQuizService.QUIZ_QUESTION_UNAVAILABLE);
    }

    @Test
    void publishingNeedsQuestionsThatAreReviewedAndForPracticeQuizzesOfferedForPractice() {
        refusedWith(() -> service.publish(FACULTY, QUIZ_ID), HttpStatus.CONFLICT, LmsQuizService.NO_QUESTIONS);

        LmsQuestion question = question(41, course);
        link(601, question);
        when(questions.findAllByIdForShare(anyCollection())).thenReturn(List.of(question));
        refusedWith(() -> service.publish(FACULTY, QUIZ_ID), HttpStatus.CONFLICT,
                "Question 1 needs review before the quiz can be published.");

        question.markReviewed();
        quiz.edit(QuizFixtures.practice("Practice"));
        refusedWith(() -> service.publish(FACULTY, QUIZ_ID), HttpStatus.CONFLICT,
                "Question 1 is not offered for practice, so it cannot be in a practice quiz.");

        question.allowPractice(true);
        LmsQuizService.QuizDetail published = service.publish(FACULTY, QUIZ_ID);
        assertThat(published.quiz().getStatus()).isEqualTo(LmsContentStatus.PUBLISHED);
        assertThat(published.quiz().getPublishedAt()).isEqualTo(NOW);
        assertThat(published.totalMarks()).isEqualByComparingTo("2.00");
    }

    @Test
    void anArchivedQuestionKeepsTheQuizFromBeingPublished() {
        LmsQuestion question = question(41, course);
        link(601, question);
        question.markReviewed();
        question.archive();
        when(questions.findAllByIdForShare(anyCollection())).thenReturn(List.of(question));

        refusedWith(() -> service.publish(FACULTY, QUIZ_ID), HttpStatus.CONFLICT, "Question 1 is archived. Remove it before publishing.");
    }

    @Test
    void aPublishedQuizKeepsItsTypeShufflingAndNegativeMarking() {
        quiz.publish(NOW);

        refusedWith(() -> service.update(FACULTY, QUIZ_ID, QuizFixtures.practice("Q")), HttpStatus.CONFLICT, LmsQuiz.FIXED_AFTER_PUBLISHING);
        assertThat(service.update(FACULTY, QUIZ_ID, QuizFixtures.graded("Renamed")).quiz().getTitle()).isEqualTo("Renamed");
    }
}
