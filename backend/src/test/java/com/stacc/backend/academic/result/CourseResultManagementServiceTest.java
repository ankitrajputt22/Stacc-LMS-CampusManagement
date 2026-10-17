package com.stacc.backend.academic.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Course offering 17 has no LMS course. Faculty A (account 21) and co-teacher C (account 23) teach it; faculty B
 * (account 22) teaches nothing. Student A (enrollment 40) is enrolled, student B (41) has completed, student C (42)
 * has withdrawn; enrollment 50 belongs to offering 18. The real teaching access service decides over stand-in
 * repositories, which keep what is saved as the database would.
 */
class CourseResultManagementServiceTest {

    private static final Long FACULTY_A = 21L;
    private static final Long FACULTY_B = 22L;
    private static final Long FACULTY_C = 23L;
    private static final Long OFFERING = 17L;
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00.123456789Z");
    private static final Instant NOW_STORED = Instant.parse("2026-11-20T10:00:00.123456Z");
    private static final Instant LATER = Instant.parse("2026-11-25T09:00:00Z");

    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final CourseResultRepository results = mock(CourseResultRepository.class);

    private final CourseOffering offering = AttendanceFixtures.offering(OFFERING, "BCS301", "Data Structures");
    private final CourseOffering otherOffering = AttendanceFixtures.offering(18, "BCS401", "Operating Systems");
    private final FacultyProfile facultyA = AttendanceFixtures.faculty(FACULTY_A, 61);
    private final FacultyProfile facultyC = AttendanceFixtures.faculty(FACULTY_C, 63);
    private final TeachingAssignment teachingA = AttendanceFixtures.teaching(facultyA, offering);
    private final TeachingAssignment teachingC = AttendanceFixtures.teaching(facultyC, offering);
    private final CourseEnrollment studentA = AttendanceFixtures.enrollment(offering, 11, 40, "2408400100011");
    private final CourseEnrollment studentB = AttendanceFixtures.enrollment(offering, 12, 41, "2408400100012");
    private final CourseEnrollment studentC = AttendanceFixtures.enrollment(offering, 13, 42, "2408400100013");
    private final CourseEnrollment elsewhere = AttendanceFixtures.enrollment(otherOffering, 14, 50, "2408400100014");

    private final Map<Long, CourseResult> stored = new HashMap<>();
    private final AtomicLong nextId = new AtomicLong(300);

    private CourseResultManagementService serviceAt(Instant now) {
        return new CourseResultManagementService(results, courseEnrollments,
                new FacultyTeachingAccessService(teachingAssignments), Clock.fixed(now, ZoneOffset.UTC));
    }

    @BeforeEach
    void setUp() {
        studentB.markCompleted();
        studentC.withdraw();
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(anyLong(), anyLong())).thenReturn(List.of());
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_A, OFFERING)).thenReturn(List.of(teachingA));
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_C, OFFERING)).thenReturn(List.of(teachingC));
        for (CourseEnrollment enrollment : List.of(studentA, studentB, studentC, elsewhere)) {
            when(courseEnrollments.findByIdForUpdate(enrollment.getId())).thenReturn(Optional.of(enrollment));
            when(courseEnrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));
        }
        when(results.findForUpdateByCourseEnrollmentId(anyLong()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.<Long>getArgument(0))));
        when(results.saveAndFlush(any(CourseResult.class))).thenAnswer(call -> {
            CourseResult result = call.getArgument(0);
            if (result.getId() == null) {
                ReflectionTestUtils.setField(result, "id", nextId.getAndIncrement());
            }
            stored.put(result.getCourseEnrollment().getId(), result);
            return result;
        });
    }

    private CourseResult save(Long account, CourseEnrollment enrollment, String obtained, String max, String grade) {
        return serviceAt(NOW).save(account, OFFERING, enrollment.getId(), new BigDecimal(obtained), new BigDecimal(max), grade, null);
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void savingForAnEnrolledStudentCreatesADraftRecordedByTheTeacherWithoutAnyLmsCourse() {
        CourseResult result = save(FACULTY_A, studentA, "82", "100", "A");

        assertEquals(CourseResultStatus.DRAFT, result.getStatus());
        assertNull(result.getPublishedAt());
        assertSame(facultyA, result.getRecordedByFacultyProfile());
        assertEquals(new BigDecimal("82.00"), result.getMarksObtained());
        assertEquals("A", result.getGradeCode());
    }

    @Test
    void aCompletedEnrollmentCanGetANewResult() {
        assertEquals(CourseResultStatus.DRAFT, save(FACULTY_A, studentB, "12.5", "20", null).getStatus());
    }

    @Test
    void aWithdrawnEnrollmentCannotGetANewResult() {
        assertRefused(HttpStatus.CONFLICT, CourseResultManagementService.WITHDRAWN, () -> save(FACULTY_A, studentC, "50", "100", null));
        assertEquals(Map.of(), stored);
    }

    @Test
    void anExistingResultIsKeptAndCanBeCorrectedAfterTheEnrollmentEnds() {
        CourseResult result = save(FACULTY_A, studentA, "82", "100", "A");
        studentA.withdraw();

        CourseResult corrected = save(FACULTY_A, studentA, "84", "100", "A");

        assertSame(result, corrected);
        assertEquals(new BigDecimal("84.00"), corrected.getMarksObtained());
        verify(results, never()).delete(any());
    }

    @Test
    void savingAgainUpdatesTheSameResult() {
        CourseResult first = save(FACULTY_A, studentA, "82", "100", "A");
        CourseResult second = save(FACULTY_C, studentA, "80", "100", "B+");

        assertSame(first, second);
        assertEquals(1, stored.size());
        assertEquals("B+", second.getGradeCode());
        assertSame(facultyC, second.getRecordedByFacultyProfile());
    }

    @Test
    void onlyCurrentTeachersOfTheOfferingCanRecordOrPublish() {
        assertRefused(HttpStatus.NOT_FOUND, CourseResultManagementService.COURSE_UNAVAILABLE, () -> save(FACULTY_B, studentA, "82", "100", null));
        assertRefused(HttpStatus.NOT_FOUND, CourseResultManagementService.COURSE_UNAVAILABLE,
                () -> serviceAt(NOW).publish(FACULTY_B, OFFERING, studentA.getId()));
        facultyA.deactivate();
        assertRefused(HttpStatus.NOT_FOUND, CourseResultManagementService.COURSE_UNAVAILABLE, () -> save(FACULTY_A, studentA, "82", "100", null));
        facultyA.activate();
        teachingA.deactivate();
        assertRefused(HttpStatus.NOT_FOUND, CourseResultManagementService.COURSE_UNAVAILABLE, () -> save(FACULTY_A, studentA, "82", "100", null));
        assertEquals(Map.of(), stored);
    }

    @Test
    void anEnrollmentOfAnotherOfferingOrAMissingOneAnswersTheSame404() {
        assertRefused(HttpStatus.NOT_FOUND, CourseResultManagementService.ENROLLMENT_UNAVAILABLE, () -> save(FACULTY_A, elsewhere, "82", "100", null));
        assertRefused(HttpStatus.NOT_FOUND, CourseResultManagementService.ENROLLMENT_UNAVAILABLE,
                () -> serviceAt(NOW).save(FACULTY_A, OFFERING, 999L, BigDecimal.ONE, BigDecimal.TEN, null, null));
        assertRefused(HttpStatus.NOT_FOUND, CourseResultManagementService.ENROLLMENT_UNAVAILABLE,
                () -> serviceAt(NOW).publish(FACULTY_A, OFFERING, elsewhere.getId()));
        assertEquals(Map.of(), stored);
    }

    @Test
    void invalidMarksAreRefusedWithClearMessagesAndNothingIsSaved() {
        assertRefused(HttpStatus.BAD_REQUEST, CourseResultManagementService.MARKS_OVER_MAX, () -> save(FACULTY_A, studentA, "101", "100", null));
        assertRefused(HttpStatus.BAD_REQUEST, CourseResultManagementService.NEGATIVE_MARKS, () -> save(FACULTY_A, studentA, "-1", "100", null));
        assertRefused(HttpStatus.BAD_REQUEST, CourseResultManagementService.MAX_NOT_POSITIVE, () -> save(FACULTY_A, studentA, "0", "0", null));
        assertRefused(HttpStatus.BAD_REQUEST, CourseResultManagementService.MARKS_TOO_PRECISE, () -> save(FACULTY_A, studentA, "1.555", "10", null));
        assertRefused(HttpStatus.BAD_REQUEST, CourseResultManagementService.GRADE_TOO_LONG,
                () -> save(FACULTY_A, studentA, "1", "10", "x".repeat(21)));
        assertRefused(HttpStatus.BAD_REQUEST, CourseResultManagementService.MARKS_REQUIRED,
                () -> serviceAt(NOW).save(FACULTY_A, OFFERING, studentA.getId(), null, BigDecimal.TEN, null, null));
        assertEquals(Map.of(), stored);
    }

    @Test
    void theEnrollmentIsLockedBeforeTheResultIsReadWithALock() {
        save(FACULTY_A, studentA, "82", "100", null);

        InOrder order = inOrder(teachingAssignments, courseEnrollments, results);
        order.verify(teachingAssignments).findAllByUserAccountIdAndCourseOfferingId(FACULTY_A, OFFERING);
        order.verify(courseEnrollments).findByIdForUpdate(studentA.getId());
        order.verify(results).findForUpdateByCourseEnrollmentId(studentA.getId());
    }

    @Test
    void publishingNeedsASavedResultAndCreatesNothing() {
        assertRefused(HttpStatus.NOT_FOUND, CourseResultManagementService.NO_RESULT,
                () -> serviceAt(NOW).publish(FACULTY_A, OFFERING, studentA.getId()));
        assertEquals(Map.of(), stored);
    }

    @Test
    void publishingIsKeptOnceAndACorrectionStaysPublishedWithItsFirstTime() {
        save(FACULTY_A, studentA, "82", "100", "A");

        CourseResult published = serviceAt(NOW).publish(FACULTY_A, OFFERING, studentA.getId());
        assertEquals(CourseResultStatus.PUBLISHED, published.getStatus());

        CourseResult again = serviceAt(LATER).publish(FACULTY_C, OFFERING, studentA.getId());
        assertEquals(NOW_STORED, again.getPublishedAt());
        // Publishing records no marks, so the recorder stays the one who saved them.
        assertSame(facultyA, again.getRecordedByFacultyProfile());

        CourseResult corrected = serviceAt(LATER).save(FACULTY_C, OFFERING, studentA.getId(),
                new BigDecimal("84"), new BigDecimal("100"), "A", "Rechecked");
        assertEquals(CourseResultStatus.PUBLISHED, corrected.getStatus());
        assertEquals(NOW_STORED, corrected.getPublishedAt());
        assertEquals(new BigDecimal("84.00"), corrected.getMarksObtained());
        assertSame(facultyC, corrected.getRecordedByFacultyProfile());
    }
}
