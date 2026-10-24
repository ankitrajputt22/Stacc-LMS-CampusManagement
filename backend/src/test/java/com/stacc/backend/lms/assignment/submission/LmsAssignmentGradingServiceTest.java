package com.stacc.backend.lms.assignment.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentNotifications;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Faculty A (account 21) and co-teacher C (account 23) teach LMS course 7; faculty B (account 22) teaches
 * course 8 only. The real faculty access service decides, over stand-in repositories, so an inactive profile,
 * an inactive teaching assignment, and an archived course are shown to stop grading too. Assignment 5 is
 * published with 20 marks. The clock is fixed.
 */
class LmsAssignmentGradingServiceTest {

    private static final Long FACULTY_A = 21L;
    private static final Long FACULTY_B = 22L;
    private static final Long FACULTY_C = 23L;
    private static final Instant NOW = Instant.parse("2026-10-21T04:00:00.123456789Z");
    private static final Instant NOW_STORED = Instant.parse("2026-10-21T04:00:00.123456Z");
    private static final Instant LATER = Instant.parse("2026-10-22T09:15:00Z");

    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final FacultyProfileRepository facultyProfiles = mock(FacultyProfileRepository.class);
    private final LmsSubmissionAttemptRepository attempts = mock(LmsSubmissionAttemptRepository.class);
    private final LmsSubmissionGradeRepository grades = mock(LmsSubmissionGradeRepository.class);
    private final LmsAssignmentNotifications notifications = mock(LmsAssignmentNotifications.class);

    private final LmsCourse course = SubmissionFixtures.course(7);
    private final LmsCourse otherCourse = SubmissionFixtures.course(8);
    private final FacultyProfile facultyA = SubmissionFixtures.faculty(course, FACULTY_A, 61);
    private final FacultyProfile facultyB = SubmissionFixtures.faculty(otherCourse, FACULTY_B, 62);
    private final FacultyProfile facultyC = SubmissionFixtures.faculty(course, FACULTY_C, 63);
    private final TeachingAssignment teachingA = new TeachingAssignment(facultyA, course.getCourseOffering());
    private final LmsAssignment assignment = SubmissionFixtures.assignment(course, 5, LmsContentStatus.PUBLISHED);
    private final LmsAssignmentSubmission submission =
            new LmsAssignmentSubmission(assignment, SubmissionFixtures.membership(course, 12, 40).getCourseEnrollment());
    private final LmsSubmissionAttempt first = attempt(submission, 301, 1, "2026-10-20T17:00:00Z");
    private final LmsSubmissionAttempt secondLate = attempt(submission, 302, 2, "2026-10-20T19:00:00Z");

    private final Map<Long, LmsSubmissionGrade> storedGrades = new HashMap<>();
    private final AtomicLong nextGradeId = new AtomicLong(900);

    private LmsAssignmentGradingService serviceAt(Instant now) {
        return new LmsAssignmentGradingService(attempts, grades, new LmsFacultyAccessService(lmsCourses, teachingAssignments),
                facultyProfiles, Clock.fixed(now, ZoneOffset.UTC), notifications);
    }

    private final LmsAssignmentGradingService service = serviceAt(NOW);

    private LmsSubmissionAttempt attempt(LmsAssignmentSubmission parent, long id, int number, String submittedAt) {
        LmsSubmissionAttempt created = new LmsSubmissionAttempt(parent, number, "Work " + number,
                Instant.parse(submittedAt), SubmissionFixtures.DUE);
        ReflectionTestUtils.setField(created, "id", id);
        known(created);
        return created;
    }

    private void known(LmsSubmissionAttempt attempt) {
        when(attempts.findWithAssignmentById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attempts.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
    }

    @BeforeEach
    void facultyWhoTeachTheirOwnCourses() {
        when(lmsCourses.findById(7L)).thenReturn(Optional.of(course));
        when(lmsCourses.findById(8L)).thenReturn(Optional.of(otherCourse));
        Long offering = course.getCourseOffering().getId();
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_A, offering)).thenReturn(List.of(teachingA));
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_C, offering))
                .thenReturn(List.of(new TeachingAssignment(facultyC, course.getCourseOffering())));
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_B, otherCourse.getCourseOffering().getId()))
                .thenReturn(List.of(new TeachingAssignment(facultyB, otherCourse.getCourseOffering())));
        when(facultyProfiles.findByUserAccountId(FACULTY_A)).thenReturn(Optional.of(facultyA));
        when(facultyProfiles.findByUserAccountId(FACULTY_B)).thenReturn(Optional.of(facultyB));
        when(facultyProfiles.findByUserAccountId(FACULTY_C)).thenReturn(Optional.of(facultyC));
        // The database stand-in: at most one grade per attempt, an ID given on first save.
        when(grades.findForUpdateByAttemptId(anyLong())).thenAnswer(call -> Optional.ofNullable(storedGrades.get(call.<Long>getArgument(0))));
        when(grades.saveAndFlush(any(LmsSubmissionGrade.class))).thenAnswer(call -> {
            LmsSubmissionGrade grade = call.getArgument(0);
            if (grade.getId() == null) {
                ReflectionTestUtils.setField(grade, "id", nextGradeId.incrementAndGet());
            }
            storedGrades.put(grade.getAttempt().getId(), grade);
            return grade;
        });
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    private void assertEveryGradeOfAIsRefused() {
        assertRefused(HttpStatus.NOT_FOUND, LmsAssignmentGradingService.ATTEMPT_UNAVAILABLE,
                () -> service.grade(FACULTY_A, 301L, BigDecimal.TEN, null));
        verify(grades, never()).saveAndFlush(any());
    }

    @Test
    void firstGradeKeepsTheAssignmentsMaximumTheGraderAndTheServerTime() {
        LmsSubmissionGrade grade = service.grade(FACULTY_A, 301L, new BigDecimal("18"), "Good solution.");

        assertSame(first, grade.getAttempt());
        assertEquals(new BigDecimal("18.00"), grade.getMarksAwarded());
        assertEquals(new BigDecimal("20.00"), grade.getMaxMarksSnapshot());
        assertSame(facultyA, grade.getGradedByFacultyProfile());
        assertEquals(NOW_STORED, grade.getGradedAt());
        assertEquals("Good solution.", grade.getFeedback());
    }

    @Test
    void theStudentHearsAboutTheFirstGradeAndAboutRealChangesOnly() {
        LmsSubmissionGrade grade = service.grade(FACULTY_A, 301L, new BigDecimal("18"), "Good solution.");
        verify(notifications).graded(grade, true);

        serviceAt(LATER).grade(FACULTY_C, 301L, new BigDecimal("18.00"), "Good solution.");
        verify(notifications, never()).graded(grade, false);

        serviceAt(LATER).grade(FACULTY_C, 301L, new BigDecimal("19"), "Good solution.");
        serviceAt(LATER).grade(FACULTY_C, 301L, new BigDecimal("19"), "Better after the fix.");
        verify(notifications, times(2)).graded(grade, false);
    }

    @Test
    void refusedGradesNotifyNobody() {
        assertThrows(ApiException.class, () -> service.grade(FACULTY_B, 301L, new BigDecimal("18"), null));
        assertThrows(ApiException.class, () -> service.grade(FACULTY_A, 301L, new BigDecimal("21"), null));

        verifyNoInteractions(notifications);
    }

    @Test
    void revisingUpdatesTheSameGradeKeepsItsMaximumAndRecordsTheLatestGrader() {
        LmsSubmissionGrade original = service.grade(FACULTY_A, 301L, new BigDecimal("18"), "First look.");
        Long originalId = original.getId();

        LmsSubmissionGrade revised = serviceAt(LATER).grade(FACULTY_C, 301L, new BigDecimal("19"), null);

        assertSame(original, revised);
        assertEquals(originalId, revised.getId());
        assertEquals(new BigDecimal("19.00"), revised.getMarksAwarded());
        assertEquals(new BigDecimal("20.00"), revised.getMaxMarksSnapshot());
        assertSame(facultyC, revised.getGradedByFacultyProfile());
        assertEquals(LATER, revised.getGradedAt());
        assertNull(revised.getFeedback());
        assertEquals(1, storedGrades.size());
    }

    @Test
    void changingTheAssignmentsMaximumLaterNeverChangesAnExistingGrade() {
        service.grade(FACULTY_A, 301L, new BigDecimal("18"), null);
        assignment.edit(assignment.getTitle(), assignment.getInstructions(), assignment.getDueAt(), new BigDecimal("25"));

        assertEquals(new BigDecimal("20.00"), storedGrades.get(301L).getMaxMarksSnapshot());
        LmsSubmissionGrade revised = service.grade(FACULTY_A, 301L, new BigDecimal("19"), null);
        assertEquals(new BigDecimal("19.00"), revised.getMarksAwarded());
        assertEquals(new BigDecimal("20.00"), revised.getMaxMarksSnapshot());
        assertRefused(HttpStatus.BAD_REQUEST, "Marks cannot exceed 20.",
                () -> service.grade(FACULTY_A, 301L, new BigDecimal("22"), null));
        // A first grade after the change is out of the new maximum.
        assertEquals(new BigDecimal("25.00"), service.grade(FACULTY_A, 302L, new BigDecimal("22"), null).getMaxMarksSnapshot());
    }

    @Test
    void marksOutsideZeroToTheMaximumAreRefusedAndZeroIsAccepted() {
        assertRefused(HttpStatus.BAD_REQUEST, "Marks cannot exceed 20.",
                () -> service.grade(FACULTY_A, 301L, new BigDecimal("20.5"), null));
        assertRefused(HttpStatus.BAD_REQUEST, LmsAssignmentGradingService.NEGATIVE_MARKS,
                () -> service.grade(FACULTY_A, 301L, new BigDecimal("-1"), null));
        assertRefused(HttpStatus.BAD_REQUEST, LmsAssignmentGradingService.NEGATIVE_MARKS,
                () -> service.grade(FACULTY_A, 301L, null, null));
        assertRefused(HttpStatus.BAD_REQUEST, LmsAssignmentGradingService.FEEDBACK_TOO_LONG,
                () -> service.grade(FACULTY_A, 301L, BigDecimal.ONE, "x".repeat(LmsSubmissionGrade.MAX_FEEDBACK_LENGTH + 1)));
        verify(grades, never()).saveAndFlush(any());

        assertEquals(new BigDecimal("0.00"), service.grade(FACULTY_A, 301L, BigDecimal.ZERO, null).getMarksAwarded());
    }

    @Test
    void eachAttemptKeepsItsOwnGradeAfterAResubmission() {
        service.grade(FACULTY_A, 301L, new BigDecimal("14"), "First attempt.");
        assertNull(storedGrades.get(302L));

        service.grade(FACULTY_A, 302L, new BigDecimal("18"), "Better.");

        assertEquals(new BigDecimal("14.00"), storedGrades.get(301L).getMarksAwarded());
        assertSame(first, storedGrades.get(301L).getAttempt());
        assertEquals(new BigDecimal("18.00"), storedGrades.get(302L).getMarksAwarded());
        assertSame(secondLate, storedGrades.get(302L).getAttempt());
    }

    @Test
    void lateAttemptGetsExactlyTheMarksTheFacultyMemberGives() {
        assertTrue(secondLate.isLate());

        assertEquals(new BigDecimal("20.00"), service.grade(FACULTY_A, 302L, new BigDecimal("20"), null).getMarksAwarded());
    }

    @Test
    void attemptRowIsLockedBeforeTheGradeIsReadWithALock() {
        service.grade(FACULTY_A, 301L, BigDecimal.TEN, null);

        InOrder order = inOrder(attempts, grades);
        order.verify(attempts).findByIdForUpdate(301L);
        order.verify(grades).findForUpdateByAttemptId(301L);
        order.verify(grades).saveAndFlush(any(LmsSubmissionGrade.class));
    }

    @Test
    void facultyWhoDoNotTeachTheCourseAndMissingAttemptsLookTheSame() {
        for (Executable action : List.<Executable>of(
                () -> service.grade(FACULTY_B, 301L, BigDecimal.TEN, null),
                () -> service.grade(99L, 301L, BigDecimal.TEN, null),
                () -> service.grade(FACULTY_A, 999L, BigDecimal.TEN, null),
                () -> service.grade(FACULTY_A, null, BigDecimal.TEN, null))) {
            assertRefused(HttpStatus.NOT_FOUND, LmsAssignmentGradingService.ATTEMPT_UNAVAILABLE, action);
        }
        verify(grades, never()).saveAndFlush(any());
    }

    @Test
    void inactiveFacultyProfileStopsGrading() {
        facultyA.deactivate();

        assertEveryGradeOfAIsRefused();
    }

    @Test
    void inactiveTeachingAssignmentStopsGrading() {
        teachingA.deactivate();

        assertEveryGradeOfAIsRefused();
    }

    @Test
    void archivedLmsCourseStopsGrading() {
        course.archive();

        assertEveryGradeOfAIsRefused();
    }

    @Test
    void attemptsOfDraftOrArchivedAssignmentsAreNotGradedInThisWorkflow() {
        LmsAssignmentSubmission draftSubmission = new LmsAssignmentSubmission(
                SubmissionFixtures.assignment(course, 6, LmsContentStatus.DRAFT), submission.getCourseEnrollment());
        LmsAssignmentSubmission archivedSubmission = new LmsAssignmentSubmission(
                SubmissionFixtures.assignment(course, 9, LmsContentStatus.ARCHIVED), submission.getCourseEnrollment());
        attempt(draftSubmission, 401, 1, "2026-10-20T17:00:00Z");
        attempt(archivedSubmission, 402, 1, "2026-10-20T17:00:00Z");

        for (Long attemptId : List.of(401L, 402L)) {
            assertRefused(HttpStatus.NOT_FOUND, LmsAssignmentGradingService.ATTEMPT_UNAVAILABLE,
                    () -> service.grade(FACULTY_A, attemptId, BigDecimal.TEN, null));
        }
        verify(grades, never()).saveAndFlush(any());
    }
}
