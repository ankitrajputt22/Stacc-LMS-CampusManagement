package com.stacc.backend.lms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LmsFacultyManagedCourseQueryServiceTest {

    private static final long ACCOUNT = 20L;

    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsFacultyManagedCourseQueryService service = new LmsFacultyManagedCourseQueryService(teachingAssignments, lmsCourses);
    private final FacultyProfile faculty = AttendanceFixtures.faculty(ACCOUNT, 7);
    private final CourseOffering published = AttendanceFixtures.offering(31, "CS304", "Operating Systems");
    private final CourseOffering draft = AttendanceFixtures.offering(32, "CS302", "Databases");
    private final CourseOffering none = AttendanceFixtures.offering(33, "CS301", "Data Structures");
    private final CourseOffering archived = AttendanceFixtures.offering(34, "CS303", "Networks");

    private static LmsCourse lmsCourse(CourseOffering offering, long id, String status) {
        LmsCourse lmsCourse = new LmsCourse(offering);
        ReflectionTestUtils.setField(lmsCourse, "id", id);
        if (status.equals("PUBLISHED")) {
            lmsCourse.publish();
        } else if (status.equals("ARCHIVED")) {
            lmsCourse.archive();
        }
        return lmsCourse;
    }

    @Test
    void everyOfferingTaughtNowIsListedWithItsLmsCourseOrNoneInCourseCodeOrder() {
        when(teachingAssignments.findAllWithCourseDetailsByUserAccountId(ACCOUNT)).thenReturn(List.of(
                AttendanceFixtures.teaching(faculty, published), AttendanceFixtures.teaching(faculty, draft),
                AttendanceFixtures.teaching(faculty, none), AttendanceFixtures.teaching(faculty, archived)));
        when(lmsCourses.findAllByCourseOfferingIdIn(anyCollection())).thenReturn(List.of(
                lmsCourse(published, 41, "PUBLISHED"), lmsCourse(draft, 42, "DRAFT"), lmsCourse(archived, 44, "ARCHIVED")));

        List<LmsFacultyManagedCourseResponse> managed = service.getManagedCourses(ACCOUNT);

        assertEquals(List.of(
                new LmsFacultyManagedCourseResponse(33L, "CS301", "Data Structures", new BigDecimal("4.00"), 3, "BTECH", "2026-27", null, null),
                new LmsFacultyManagedCourseResponse(32L, "CS302", "Databases", new BigDecimal("4.00"), 3, "BTECH", "2026-27", 42L, "DRAFT"),
                new LmsFacultyManagedCourseResponse(34L, "CS303", "Networks", new BigDecimal("4.00"), 3, "BTECH", "2026-27", 44L, "ARCHIVED"),
                new LmsFacultyManagedCourseResponse(31L, "CS304", "Operating Systems", new BigDecimal("4.00"), 3, "BTECH", "2026-27", 41L, "PUBLISHED")),
                managed);
    }

    @Test
    void inactiveAssignmentsAnotherAccountsAssignmentsAndDuplicatesAreLeftOut() {
        TeachingAssignment inactive = AttendanceFixtures.teaching(faculty, draft);
        inactive.deactivate();
        TeachingAssignment someoneElse = AttendanceFixtures.teaching(AttendanceFixtures.faculty(21, 8), archived);
        when(teachingAssignments.findAllWithCourseDetailsByUserAccountId(ACCOUNT)).thenReturn(List.of(
                AttendanceFixtures.teaching(faculty, none), AttendanceFixtures.teaching(faculty, none), inactive, someoneElse));

        List<LmsFacultyManagedCourseResponse> managed = service.getManagedCourses(ACCOUNT);

        assertEquals(List.of(33L), managed.stream().map(LmsFacultyManagedCourseResponse::courseOfferingId).toList());
    }

    @Test
    void anInactiveFacultyProfileManagesNothingAndNoLmsQueryIsMade() {
        faculty.deactivate();
        when(teachingAssignments.findAllWithCourseDetailsByUserAccountId(ACCOUNT)).thenReturn(List.of(
                AttendanceFixtures.teaching(faculty, published), AttendanceFixtures.teaching(faculty, none)));

        assertTrue(service.getManagedCourses(ACCOUNT).isEmpty());
        assertTrue(service.getManagedCourses(null).isEmpty());
        verify(lmsCourses, never()).findAllByCourseOfferingIdIn(anyCollection());
    }
}
