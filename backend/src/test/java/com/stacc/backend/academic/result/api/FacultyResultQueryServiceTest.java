package com.stacc.backend.academic.result.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.result.CourseResult;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** Faculty A (account 21) teaches offerings 17 and 18, neither with an LMS course; faculty B (22) teaches neither. */
class FacultyResultQueryServiceTest {

    private static final Long FACULTY_A = 21L;
    private static final Long FACULTY_B = 22L;

    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final CourseResultRepository results = mock(CourseResultRepository.class);
    private final FacultyResultQueryService service = new FacultyResultQueryService(
            teachingAssignments, new FacultyTeachingAccessService(teachingAssignments), courseEnrollments, results);

    private final CourseOffering structures = AttendanceFixtures.offering(17, "BCS301", "Data Structures");
    private final CourseOffering systems = AttendanceFixtures.offering(18, "BCS401", "Operating Systems");
    private final FacultyProfile facultyA = AttendanceFixtures.faculty(FACULTY_A, 61);
    private final TeachingAssignment teachingStructures = AttendanceFixtures.teaching(facultyA, structures);
    private final TeachingAssignment teachingSystems = AttendanceFixtures.teaching(facultyA, systems);

    @BeforeEach
    void setUp() {
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(anyLong(), anyLong())).thenReturn(List.of());
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_A, 17L)).thenReturn(List.of(teachingStructures));
    }

    @Test
    void myCoursesAreTheOfferingsTaughtNowInCourseCodeOrder() {
        TeachingAssignment ended = AttendanceFixtures.teaching(facultyA, AttendanceFixtures.offering(19, "BCS101", "Programming"));
        ended.deactivate();
        when(teachingAssignments.findAllWithCourseDetailsByUserAccountId(FACULTY_A))
                .thenReturn(List.of(teachingSystems, ended, teachingStructures, teachingStructures));

        List<ResultCourseOfferingResponse> courses = service.getMyCourseOfferings(FACULTY_A);

        assertEquals(List.of(17L, 18L), courses.stream().map(ResultCourseOfferingResponse::courseOfferingId).toList());
        assertEquals(new ResultCourseOfferingResponse(17L, "BCS301", "Data Structures", new BigDecimal("4.00"), 3, "BTECH", "2026-27"),
                courses.getFirst());
    }

    @Test
    void theRosterListsEveryEnrollmentWithItsResultInOneQueryEach() {
        CourseEnrollment enrolled = AttendanceFixtures.enrollment(structures, 11, 40, "2408400100011");
        CourseEnrollment withdrawn = AttendanceFixtures.enrollment(structures, 13, 42, "2408400100013");
        withdrawn.withdraw();
        CourseResult result = new CourseResult(enrolled, facultyA, new BigDecimal("82"), new BigDecimal("100"), "A", null);
        ReflectionTestUtils.setField(result, "id", 300L);
        when(courseEnrollments.findAllWithStudentByCourseOfferingId(17L)).thenReturn(List.of(enrolled, withdrawn));
        when(results.findAllByCourseEnrollmentIdIn(List.of(40L, 42L))).thenReturn(List.of(result));

        CourseResultRosterResponse roster = service.getRoster(FACULTY_A, 17L);

        assertEquals("BCS301", roster.courseCode());
        assertEquals(new BigDecimal("4.00"), roster.credits());
        assertEquals(2, roster.students().size());
        CourseResultRosterResponse.Student first = roster.students().getFirst();
        assertEquals(40L, first.courseEnrollmentId());
        assertEquals("2408400100011", first.studentLoginId());
        assertEquals("ENROLLED", first.courseEnrollmentStatus());
        assertEquals(300L, first.result().resultId());
        assertEquals("DRAFT", first.result().status());
        assertEquals("WITHDRAWN", roster.students().get(1).courseEnrollmentStatus());
        assertNull(roster.students().get(1).result());
    }

    @Test
    void anOfferingNotTaughtNowAnswers404WithoutReadingItsStudents() {
        ApiException refused = assertThrows(ApiException.class, () -> service.getRoster(FACULTY_B, 17L));

        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
        assertEquals(FacultyResultQueryService.COURSE_UNAVAILABLE, refused.getMessage());
        verify(courseEnrollments, never()).findAllWithStudentByCourseOfferingId(anyLong());
    }
}
