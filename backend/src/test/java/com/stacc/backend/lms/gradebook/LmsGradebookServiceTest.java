package com.stacc.backend.lms.gradebook;

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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmission;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGrade;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGradeRepository;
import com.stacc.backend.lms.assignment.submission.SubmissionFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.quiz.LmsQuizQuestionRepository;
import com.stacc.backend.lms.quiz.LmsQuizRepository;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAnswerMarkRepository;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAnswerRepository;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttemptRepository;
import com.stacc.backend.lms.quiz.attempt.QuizScores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class LmsGradebookServiceTest {

    private static final Long FACULTY = 21L;
    private static final Long OTHER = 22L;
    private static final Long STUDENT = 31L;
    private static final Long COURSE_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-11-10T05:00:00Z");

    private final LmsAssignmentRepository assignments = mock(LmsAssignmentRepository.class);
    private final LmsAssignmentSubmissionRepository submissions = mock(LmsAssignmentSubmissionRepository.class);
    private final LmsSubmissionGradeRepository grades = mock(LmsSubmissionGradeRepository.class);
    private final LmsQuizRepository quizzes = mock(LmsQuizRepository.class);
    private final LmsQuizQuestionRepository quizQuestions = mock(LmsQuizQuestionRepository.class);
    private final LmsQuizAttemptRepository attempts = mock(LmsQuizAttemptRepository.class);
    private final LmsGradeItemRepository items = mock(LmsGradeItemRepository.class);
    private final LmsGradeEntryRepository entries = mock(LmsGradeEntryRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsStudentAccessService studentAccess = mock(LmsStudentAccessService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final GradebookBuilder builder = new GradebookBuilder(assignments, submissions, grades, quizzes, quizQuestions, attempts,
            new QuizScores(quizQuestions, mock(LmsQuizAnswerRepository.class), mock(LmsQuizAnswerMarkRepository.class)), items, entries);
    private final LmsGradebookService service = new LmsGradebookService(builder, items, entries, lmsCourses, assignments, quizzes,
            courseEnrollments, facultyAccess, clock);
    private final LmsStudentGradesService studentGrades = new LmsStudentGradesService(studentAccess, builder, clock);
    private final LmsCourse course = SubmissionFixtures.course(COURSE_ID);
    private final CourseEnrollment enrollment = SubmissionFixtures.enrollment(course, STUDENT, 41);
    private final List<LmsGradeItem> stored = new ArrayList<>();
    private LmsAssignment assignment;

    private static void refusedWith(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }

    private LmsGradeItem manual(long id, String max) {
        LmsGradeItem item = LmsGradeItem.manual(course, FACULTY, "Class test", new BigDecimal(max));
        ReflectionTestUtils.setField(item, "id", id);
        stored.add(item);
        when(items.findLmsCourseIdById(id)).thenReturn(Optional.of(COURSE_ID));
        when(items.findByIdForUpdate(id)).thenReturn(Optional.of(item));
        return item;
    }

    @BeforeEach
    void setUp() {
        assignment = SubmissionFixtures.assignment(course, 12, LmsContentStatus.PUBLISHED);
        when(facultyAccess.canAccessCourse(FACULTY, COURSE_ID)).thenReturn(true);
        when(lmsCourses.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(lmsCourses.findByIdForUpdate(COURSE_ID)).thenReturn(Optional.of(course));
        when(assignments.findById(12L)).thenReturn(Optional.of(assignment));
        when(assignments.findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(any(), any())).thenReturn(List.of(assignment));
        when(quizzes.findAllByLmsCourseIdAndStatusOrderByIdDesc(any(), any())).thenReturn(List.of());
        when(items.findAllByLmsCourseIdOrderByIdAsc(COURSE_ID)).thenAnswer(call -> List.copyOf(stored));
        when(items.findAllForUpdate(COURSE_ID)).thenAnswer(call -> List.copyOf(stored));
        when(items.saveAndFlush(any())).thenAnswer(call -> {
            LmsGradeItem item = call.getArgument(0);
            if (!stored.contains(item)) {
                stored.add(item);
            }
            return item;
        });
        when(entries.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(entries.findForUpdate(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(courseEnrollments.findAllCurrentByCourseOfferingId(any())).thenReturn(List.of(enrollment));
        when(courseEnrollments.findAllCurrentWithAccountByCourseOfferingId(any())).thenReturn(List.of(enrollment));
        when(studentAccess.findAccessGrantingEnrollment(STUDENT, COURSE_ID)).thenReturn(Optional.of(enrollment));
    }

    @Test
    void theGradebookShowsTheLatestAttemptsGradeAndManualMarksWithTheTotal() {
        LmsAssignmentSubmission submission = new LmsAssignmentSubmission(assignment, enrollment);
        submission.takeNextAttemptNumber();
        LmsSubmissionAttempt attempt = new LmsSubmissionAttempt(submission, 1, "Done", NOW, assignment.getDueAt());
        LmsSubmissionGrade grade = new LmsSubmissionGrade(attempt, SubmissionFixtures.faculty(course, FACULTY, 5), new BigDecimal("17"),
                assignment.getMaxMarks(), "Good", NOW);
        when(submissions.findAllSubmitted(any(), any())).thenReturn(List.of(submission));
        when(grades.findLatestAttemptGrades(any(), any())).thenReturn(List.of(grade));
        LmsGradeItem test = manual(70, "25");
        LmsGradeEntry entry = new LmsGradeEntry(test, enrollment);
        entry.grade(new BigDecimal("20"), null, FACULTY, NOW);
        when(entries.findAllByItemIdIn(any())).thenReturn(List.of(entry));

        LmsGradebookService.FacultyGradebook book = service.gradebook(FACULTY, COURSE_ID);
        assertThat(book.gradebook().columns()).extracting(GradebookBuilder.Column::type)
                .containsExactly(GradeItemType.ASSIGNMENT, GradeItemType.MANUAL);
        assertThat(book.gradebook().cells().get(41L)).extracting(GradebookBuilder.Cell::status)
                .containsExactly(GradebookBuilder.CellStatus.GRADED, GradebookBuilder.CellStatus.GRADED);
        GradebookBuilder.Total total = book.gradebook().total(41L, column -> true);
        assertThat(total.marks()).isEqualByComparingTo("37");
        assertThat(total.outOf()).isEqualByComparingTo("45");
    }

    @Test
    void studentsSeeOnlyPublishedColumns() {
        manual(70, "25");
        when(entries.findAllByItemIdIn(any())).thenReturn(List.of());
        when(submissions.findAllSubmitted(any(), any())).thenReturn(List.of());
        when(grades.findLatestAttemptGrades(any(), any())).thenReturn(List.of());

        assertThat(studentGrades.grades(STUDENT, COURSE_ID).gradebook().columns()).isEmpty();
        service.publishColumn(FACULTY, COURSE_ID, GradeItemType.ASSIGNMENT, 12L);
        assertThat(studentGrades.grades(STUDENT, COURSE_ID).gradebook().columns()).singleElement()
                .satisfies(column -> assertThat(column.title()).isEqualTo(assignment.getTitle()));
        refusedWith(() -> studentGrades.grades(OTHER, COURSE_ID), HttpStatus.NOT_FOUND, LmsStudentGradesService.COURSE_UNAVAILABLE);
    }

    @Test
    void publishingAColumnMakesOneRecordAndPublishingAgainChangesNothing() {
        LmsGradeItem first = service.publishColumn(FACULTY, COURSE_ID, GradeItemType.ASSIGNMENT, 12L);
        LmsGradeItem again = service.publishColumn(FACULTY, COURSE_ID, GradeItemType.ASSIGNMENT, 12L);

        assertThat(again).isSameAs(first);
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(stored).hasSize(1);
        refusedWith(() -> service.publishColumn(FACULTY, COURSE_ID, GradeItemType.QUIZ, 99L), HttpStatus.CONFLICT,
                LmsGradebookService.COLUMN_UNAVAILABLE);
        refusedWith(() -> service.publishColumn(FACULTY, COURSE_ID, GradeItemType.MANUAL, 99L), HttpStatus.CONFLICT,
                LmsGradebookService.COLUMN_UNAVAILABLE);
        refusedWith(() -> service.publishColumn(OTHER, COURSE_ID, GradeItemType.ASSIGNMENT, 12L), HttpStatus.NOT_FOUND,
                LmsGradebookService.COURSE_UNAVAILABLE);
    }

    @Test
    void manualMarksStayWithinTheMaximumForCurrentStudentsOnly() {
        manual(70, "25");
        LmsGradeEntry saved = service.enterMarks(FACULTY, 70L, 41L, new BigDecimal("18.5"), "Well done");
        assertThat(saved.getMarks()).isEqualByComparingTo("18.50");

        refusedWith(() -> service.enterMarks(FACULTY, 70L, 41L, new BigDecimal("26"), null), HttpStatus.BAD_REQUEST,
                "Marks must be from 0 to 25, with at most 2 decimal places.");
        refusedWith(() -> service.enterMarks(FACULTY, 70L, 99L, BigDecimal.ONE, null), HttpStatus.NOT_FOUND,
                LmsGradebookService.STUDENT_UNAVAILABLE);
        refusedWith(() -> service.enterMarks(OTHER, 70L, 41L, BigDecimal.ONE, null), HttpStatus.NOT_FOUND,
                LmsGradebookService.ITEM_UNAVAILABLE);
    }

    @Test
    void aManualItemsMaximumCannotFallBelowMarksAlreadyEntered() {
        LmsGradeItem item = manual(70, "25");
        LmsGradeEntry entry = new LmsGradeEntry(item, enrollment);
        entry.grade(new BigDecimal("20"), null, FACULTY, NOW);
        when(entries.findAllForUpdate(70L)).thenReturn(List.of(entry));

        refusedWith(() -> service.editManualItem(FACULTY, 70L, "Class test", new BigDecimal("15")), HttpStatus.CONFLICT,
                LmsGradebookService.MARKS_ABOVE_MAXIMUM);
        assertThat(service.editManualItem(FACULTY, 70L, "Class test 1", new BigDecimal("30")).getTitle()).isEqualTo("Class test 1");
        verify(items, never()).delete(any());
    }
}
