package com.stacc.backend.lms.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Faculty member A (ACCOUNT_ID) teaches LMS course 7; faculty member B (OTHER_ACCOUNT_ID) teaches course 8.
 * The real faculty access service decides, over stand-in repositories, so an inactive profile, an inactive
 * teaching assignment, and an archived course are each shown to stop every change. The clock is fixed.
 */
class LmsAssignmentManagementServiceTest {

    private static final Long ACCOUNT_ID = 21L;
    private static final Long OTHER_ACCOUNT_ID = 22L;
    // The clock has nanoseconds; the database keeps microseconds.
    private static final Instant NOW = Instant.parse("2026-10-15T08:00:00.123456789Z");
    private static final Instant NOW_STORED = Instant.parse("2026-10-15T08:00:00.123456Z");
    private static final Instant DUE = Instant.parse("2026-10-20T18:29:00Z");
    private static final BigDecimal MARKS = new BigDecimal("20");

    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final LmsAssignmentRepository assignments = mock(LmsAssignmentRepository.class);
    private final LmsAssignmentNotifications notifications = mock(LmsAssignmentNotifications.class);
    private final com.stacc.backend.lms.outcome.LmsCourseOutcomeRepository outcomes =
            org.mockito.Mockito.mock(com.stacc.backend.lms.outcome.LmsCourseOutcomeRepository.class);
    private final LmsAssignmentManagementService service = new LmsAssignmentManagementService(
            lmsCourses, assignments, new LmsFacultyAccessService(lmsCourses, teachingAssignments), Clock.fixed(NOW, ZoneOffset.UTC),
            notifications, outcomes);

    private final Department department = new Department("CSE", "Computer Science and Engineering");
    private final Semester semester = new Semester(
            new Program(department, "BTECH", "Bachelor of Technology", 8),
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30)),
            3);

    private final FacultyProfile facultyA = faculty(ACCOUNT_ID);
    private final FacultyProfile facultyB = faculty(OTHER_ACCOUNT_ID);
    private final LmsCourse course = lmsCourse(7, 50, "TST301");
    private final LmsCourse otherCourse = lmsCourse(8, 51, "TST302");
    private final TeachingAssignment teachingA = new TeachingAssignment(facultyA, course.getCourseOffering());
    private final LmsAssignment draft = assignment(course, 5);
    private final LmsAssignment foreign = assignment(otherCourse, 6);

    private FacultyProfile faculty(Long accountId) {
        UserAccount account = new UserAccount("TEST-FACULTY-" + accountId, "example-hash-value");
        ReflectionTestUtils.setField(account, "id", accountId);
        return new FacultyProfile(account, department);
    }

    private LmsCourse lmsCourse(long id, long offeringId, String courseCode) {
        Course catalogCourse = new Course(department, courseCode, "Test Course " + courseCode, new BigDecimal("4.00"));
        CourseOffering offering = new CourseOffering(catalogCourse, semester);
        ReflectionTestUtils.setField(offering, "id", offeringId);
        LmsCourse created = new LmsCourse(offering);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    private static LmsAssignment assignment(LmsCourse course, long id) {
        LmsAssignment created = new LmsAssignment(course, "Assignment " + id, "Instructions " + id, DUE, MARKS);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    @BeforeEach
    void eachFacultyMemberTeachesTheirOwnCourse() {
        when(lmsCourses.findById(7L)).thenReturn(Optional.of(course));
        when(lmsCourses.findById(8L)).thenReturn(Optional.of(otherCourse));
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(ACCOUNT_ID, 50L)).thenReturn(List.of(teachingA));
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(OTHER_ACCOUNT_ID, 51L))
                .thenReturn(List.of(new TeachingAssignment(facultyB, otherCourse.getCourseOffering())));
        when(assignments.findById(5L)).thenReturn(Optional.of(draft));
        when(assignments.findById(6L)).thenReturn(Optional.of(foreign));
        when(assignments.save(any(LmsAssignment.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    /** Every action faculty member A could try on course 7 and its assignment 5. */
    private List<Executable> everyActionOfA() {
        return List.of(
                () -> service.create(ACCOUNT_ID, 7L, "Title", "Text", DUE, MARKS),
                () -> service.update(ACCOUNT_ID, 5L, "Taken", "Taken", DUE, MARKS),
                () -> service.publish(ACCOUNT_ID, 5L),
                () -> service.archive(ACCOUNT_ID, 5L));
    }

    private void assertEveryActionOfAIsRefused() {
        for (Executable action : everyActionOfA()) {
            ApiException refused = assertThrows(ApiException.class, action);
            assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
        }
        verify(assignments, never()).save(any());
        assertEquals("Assignment 5", draft.getTitle());
        assertEquals(LmsContentStatus.DRAFT, draft.getStatus());
    }

    @Test
    void newAssignmentIsAnUnpublishedDraftOfTheCourse() {
        LmsAssignment created = service.create(ACCOUNT_ID, 7L, "Arrays practice", "Solve problems 1 to 5.", DUE, new BigDecimal("12.5"));

        assertSame(course, created.getLmsCourse());
        assertEquals(LmsContentStatus.DRAFT, created.getStatus());
        assertNull(created.getPublishedAt());
        assertEquals(DUE, created.getDueAt());
        assertEquals(new BigDecimal("12.50"), created.getMaxMarks());
        verify(assignments).save(created);
    }

    @Test
    void courseTheFacultyMemberDoesNotTeachIsUnavailable() {
        assertRefused(HttpStatus.NOT_FOUND, LmsAssignmentManagementService.COURSE_UNAVAILABLE,
                () -> service.create(ACCOUNT_ID, 8L, "Title", "Text", DUE, MARKS));
        assertRefused(HttpStatus.NOT_FOUND, LmsAssignmentManagementService.COURSE_UNAVAILABLE,
                () -> service.create(OTHER_ACCOUNT_ID, 7L, "Title", "Text", DUE, MARKS));
        assertRefused(HttpStatus.NOT_FOUND, LmsAssignmentManagementService.COURSE_UNAVAILABLE,
                () -> service.create(ACCOUNT_ID, 999L, "Title", "Text", DUE, MARKS));

        verify(assignments, never()).save(any());
    }

    @Test
    void guessedIdsOfOtherCoursesAndMissingAssignmentsLookTheSame() {
        for (Executable action : List.<Executable>of(
                () -> service.update(ACCOUNT_ID, 6L, "Taken", "Taken", DUE, MARKS),
                () -> service.publish(ACCOUNT_ID, 6L),
                () -> service.archive(ACCOUNT_ID, 6L),
                () -> service.update(OTHER_ACCOUNT_ID, 5L, "Taken", "Taken", DUE, MARKS),
                () -> service.publish(OTHER_ACCOUNT_ID, 5L),
                () -> service.archive(OTHER_ACCOUNT_ID, 5L),
                () -> service.publish(ACCOUNT_ID, 999L),
                () -> service.publish(ACCOUNT_ID, null))) {
            assertRefused(HttpStatus.NOT_FOUND, LmsAssignmentManagementService.ASSIGNMENT_UNAVAILABLE, action);
        }

        assertEquals("Assignment 6", foreign.getTitle());
        assertEquals(LmsContentStatus.DRAFT, foreign.getStatus());
        assertEquals(LmsContentStatus.DRAFT, draft.getStatus());
    }

    @Test
    void inactiveFacultyProfileStopsEveryChange() {
        facultyA.deactivate();

        assertEveryActionOfAIsRefused();
    }

    @Test
    void inactiveTeachingAssignmentStopsEveryChange() {
        teachingA.deactivate();

        assertEveryActionOfAIsRefused();
    }

    @Test
    void archivedLmsCourseStopsEveryChange() {
        course.archive();

        assertEveryActionOfAIsRefused();
    }

    @Test
    void onlyTheFirstPublicationNotifiesStudents() {
        service.publish(ACCOUNT_ID, 5L);
        service.publish(ACCOUNT_ID, 5L);

        verify(notifications, times(1)).published(draft);
    }

    @Test
    void creatingEditingAndArchivingNotifyNobody() {
        service.create(ACCOUNT_ID, 7L, "Title", "Text", DUE, MARKS);
        service.update(ACCOUNT_ID, 5L, "New title", "Text", DUE, MARKS);
        service.archive(ACCOUNT_ID, 5L);

        verifyNoInteractions(notifications);
    }

    @Test
    void publishingUsesTheCurrentTimeOnceAtTheStoredPrecisionAndCanBeRepeated() {
        service.publish(ACCOUNT_ID, 5L);
        LmsAssignment again = service.publish(ACCOUNT_ID, 5L);

        assertEquals(LmsContentStatus.PUBLISHED, again.getStatus());
        assertEquals(NOW_STORED, again.getPublishedAt());
    }

    @Test
    void publishedAssignmentCanBeCorrectedAndGetANewDeadline() {
        service.publish(ACCOUNT_ID, 5L);
        Instant moved = Instant.parse("2026-10-27T18:29:00Z");

        LmsAssignment changed = service.update(ACCOUNT_ID, 5L, "Arrays practice", "Problems 1 to 6.", moved, new BigDecimal("25"));

        assertEquals(moved, changed.getDueAt());
        assertEquals(new BigDecimal("25.00"), changed.getMaxMarks());
        assertEquals(LmsContentStatus.PUBLISHED, changed.getStatus());
        assertEquals(NOW_STORED, changed.getPublishedAt());
    }

    @Test
    void archivingCanBeRepeatedAndEndsEditingAndPublishing() {
        service.publish(ACCOUNT_ID, 5L);
        service.archive(ACCOUNT_ID, 5L);
        service.archive(ACCOUNT_ID, 5L);
        assertEquals(LmsContentStatus.ARCHIVED, draft.getStatus());
        assertEquals(NOW_STORED, draft.getPublishedAt());

        assertRefused(HttpStatus.CONFLICT, LmsAssignmentManagementService.ASSIGNMENT_ARCHIVED,
                () -> service.update(ACCOUNT_ID, 5L, "Back", "Back", DUE, MARKS));
        assertRefused(HttpStatus.CONFLICT, LmsAssignmentManagementService.ASSIGNMENT_ARCHIVED,
                () -> service.publish(ACCOUNT_ID, 5L));
        verify(assignments, never()).delete(any());
        verify(assignments, never()).deleteById(any());
    }

    // Alignment

    private com.stacc.backend.lms.outcome.LmsCourseOutcome outcome(LmsCourse lmsCourse, long id, int number, boolean active) {
        var created = new com.stacc.backend.lms.outcome.LmsCourseOutcome(lmsCourse, number, "Outcome " + number);
        ReflectionTestUtils.setField(created, "id", id);
        if (!active) {
            created.archive();
        }
        return created;
    }

    @Test
    void anAssignmentIsAlignedWithActiveOutcomesOfItsOwnCourseAndKeepsAnArchivedOneItAlreadyHad() {
        var co1 = outcome(course, 91, 1, true);
        var co2 = outcome(course, 92, 2, false);
        var foreignOutcome = outcome(otherCourse, 93, 1, true);
        when(outcomes.findAllByIdIn(java.util.Set.of(91L))).thenReturn(List.of(co1));
        when(outcomes.findAllByIdIn(java.util.Set.of(91L, 92L))).thenReturn(List.of(co1, co2));
        when(outcomes.findAllByIdIn(java.util.Set.of(93L))).thenReturn(List.of(foreignOutcome));
        when(outcomes.findAllByIdIn(java.util.Set.of(94L))).thenReturn(List.of());

        service.align(ACCOUNT_ID, 5L, com.stacc.backend.lms.outcome.BloomLevel.APPLY, List.of(91L));
        assertEquals(List.of(co1), draft.getOutcomes());
        assertEquals(com.stacc.backend.lms.outcome.BloomLevel.APPLY, draft.getBloomLevel());

        // An archived outcome cannot be newly added, another course's never, and an unknown one is refused too.
        for (List<Long> wanted : List.of(List.of(91L, 92L), List.of(93L), List.of(94L))) {
            assertRefused(HttpStatus.CONFLICT, LmsAssignmentManagementService.OUTCOMES_INVALID,
                    () -> service.align(ACCOUNT_ID, 5L, null, wanted));
        }

        draft.align(null, List.of(co1, co2));
        service.align(ACCOUNT_ID, 5L, null, List.of(91L, 92L));
        assertEquals(List.of(co1, co2), draft.getOutcomes(), "an outcome archived after aligning may stay");
        assertEquals(null, draft.getBloomLevel());
    }

}
