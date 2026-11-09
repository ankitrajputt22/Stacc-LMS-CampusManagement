package com.stacc.backend.academic.result.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.result.CourseResult;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.identity.faculty.FacultyProfile;
import org.junit.jupiter.api.Test;

/** Student A is account 11. The repository answers as the database query would. */
class StudentResultQueryServiceTest {

    private static final Long STUDENT_A = 11L;
    private static final Instant PUBLISHED = Instant.parse("2026-11-20T10:00:00Z");

    private final CourseResultRepository results = mock(CourseResultRepository.class);
    private final StudentResultQueryService service = new StudentResultQueryService(results);

    private final FacultyProfile faculty = AttendanceFixtures.faculty(21, 61);
    private final CourseOffering structures = AttendanceFixtures.offering(17, "BCS301", "Data Structures");
    private final CourseEnrollment enrollment = AttendanceFixtures.enrollment(structures, STUDENT_A, 40, "2408400100011");

    private CourseResult result(String marks, String grade, String remarks, boolean published) {
        CourseResult result = new CourseResult(enrollment, faculty, new BigDecimal(marks), new BigDecimal("100"), grade, remarks);
        if (published) {
            result.publish(PUBLISHED);
        }
        return result;
    }

    @Test
    void aPublishedResultIsShownWithItsCourseContextAndNoIds() {
        when(results.findAllPublishedByUserAccountId(STUDENT_A)).thenReturn(List.of(result("82", "A", "Good performance", true)));

        StudentResultsResponse.Result shown = service.getMyResults(STUDENT_A).results().getFirst();

        assertEquals(new StudentResultsResponse.Result("BCS301", "Data Structures", new BigDecimal("4.00"), 3, "BTECH", "2026-27",
                new BigDecimal("82.00"), new BigDecimal("100.00"), "A", "Good performance", PUBLISHED), shown);
    }

    @Test
    void gradeAndRemarksStayEmptyWhenNoneWereEntered() {
        when(results.findAllPublishedByUserAccountId(STUDENT_A)).thenReturn(List.of(result("82", null, null, true)));

        StudentResultsResponse.Result shown = service.getMyResults(STUDENT_A).results().getFirst();

        assertNull(shown.gradeCode());
        assertNull(shown.remarks());
    }

    @Test
    void aDraftNeverReachesTheStudent() {
        when(results.findAllPublishedByUserAccountId(STUDENT_A)).thenReturn(List.of(result("82", "A", null, false)));

        assertEquals(List.of(), service.getMyResults(STUDENT_A).results());
    }

    @Test
    void aPublishedResultStaysAfterTheEnrollmentIsCompleted() {
        CourseResult published = result("82", "A", null, true);
        enrollment.markCompleted();
        when(results.findAllPublishedByUserAccountId(STUDENT_A)).thenReturn(List.of(published));

        assertEquals(1, service.getMyResults(STUDENT_A).results().size());
    }

    @Test
    void noAccountMeansNoResultsAndNoQuery() {
        assertEquals(List.of(), service.getMyResults(null).results());
        verify(results, never()).findAllPublishedByUserAccountId(null);
    }
}
