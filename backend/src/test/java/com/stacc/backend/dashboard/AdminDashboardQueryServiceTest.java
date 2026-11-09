package com.stacc.backend.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

/**
 * The admin dashboard over stand-in repositories. Counting rows and filtering by status happen in the database, so
 * here the test checks which counts are asked for, with which status, and how the answer is put together.
 */
class AdminDashboardQueryServiceTest {

    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
    private final SemesterEnrollmentRepository semesterEnrollments = mock(SemesterEnrollmentRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final AdminDashboardQueryService service = new AdminDashboardQueryService(departments, programs, sessions,
            semesters, courses, offerings, semesterEnrollments, courseEnrollments);

    @BeforeEach
    void setUp() {
        when(departments.count()).thenReturn(2L);
        when(programs.count()).thenReturn(3L);
        when(sessions.count()).thenReturn(2L);
        when(semesters.count()).thenReturn(4L);
        when(courses.count()).thenReturn(7L);
        when(offerings.count()).thenReturn(6L);
        when(semesterEnrollments.countByStatus(SemesterEnrollmentStatus.ENROLLED)).thenReturn(2L);
        when(courseEnrollments.countByStatus(CourseEnrollmentStatus.ENROLLED)).thenReturn(3L);
    }

    @Test
    void theMasterAndOperationCountsComeStraightFromTheRepositories() {
        AdminDashboardResponse dashboard = service.getDashboard();

        assertEquals(new AdminDashboardResponse.AcademicMasters(2, 3, 2, 4, 7), dashboard.academicMasters());
        assertEquals(new AdminDashboardResponse.AcademicOperations(6, 2, 3), dashboard.academicOperations());
    }

    @Test
    void activeEnrollmentIsCountedForEnrolledOnlyAndOfferingsNotCourses() {
        service.getDashboard();

        verify(semesterEnrollments).countByStatus(SemesterEnrollmentStatus.ENROLLED);
        verify(courseEnrollments).countByStatus(CourseEnrollmentStatus.ENROLLED);
        for (SemesterEnrollmentStatus ended : new SemesterEnrollmentStatus[] {SemesterEnrollmentStatus.COMPLETED, SemesterEnrollmentStatus.WITHDRAWN}) {
            verify(semesterEnrollments, never()).countByStatus(ended);
        }
        for (CourseEnrollmentStatus ended : new CourseEnrollmentStatus[] {CourseEnrollmentStatus.COMPLETED, CourseEnrollmentStatus.WITHDRAWN}) {
            verify(courseEnrollments, never()).countByStatus(ended);
        }
        verify(semesterEnrollments, never()).count();
        verify(courseEnrollments, never()).count();
        verify(offerings).count();
    }

    @Test
    void thePreviewIsTheFirstPageOfTheAdminOfferingListWithItsOwnOrder() {
        List<CourseOffering> firstPage = IntStream.range(0, 5)
                .mapToObj(index -> AttendanceFixtures.offering(31 + index, "CS30" + index, "Course " + index))
                .toList();
        when(offerings.findAllWithContext(null, null, null, null, PageRequest.of(0, 5))).thenReturn(firstPage);

        AdminDashboardResponse dashboard = service.getDashboard();

        assertEquals(5, dashboard.courseOfferings().size());
        assertEquals(List.of(31L, 32L, 33L, 34L, 35L),
                dashboard.courseOfferings().stream().map(AdminDashboardResponse.CourseOfferingPreview::courseOfferingId).toList());
        assertEquals(new AdminDashboardResponse.CourseOfferingPreview(31L, "CS300", "Course 0", new BigDecimal("4.00"), 3, "BTECH",
                "2026-27"), dashboard.courseOfferings().getFirst());
        assertEquals(6, dashboard.academicOperations().courseOfferingCount());
        verify(offerings).findAllWithContext(null, null, null, null, PageRequest.of(0, AdminDashboardQueryService.OFFERING_PREVIEW_LIMIT));
    }

    @Test
    void emptyDataGivesZeroCountsAndAnEmptyPreview() {
        AdminDashboardQueryService empty = new AdminDashboardQueryService(mock(DepartmentRepository.class),
                mock(ProgramRepository.class), mock(AcademicSessionRepository.class), mock(SemesterRepository.class),
                mock(CourseRepository.class), mock(CourseOfferingRepository.class), mock(SemesterEnrollmentRepository.class),
                mock(CourseEnrollmentRepository.class));

        assertEquals(new AdminDashboardResponse(new AdminDashboardResponse.AcademicMasters(0, 0, 0, 0, 0),
                new AdminDashboardResponse.AcademicOperations(0, 0, 0), List.of()), empty.getDashboard());
    }

    @Test
    void theAnswerHoldsNoStudentFacultyOrAccountFields() {
        assertEquals(List.of("courseOfferingId", "courseCode", "courseName", "credits", "semesterNumber", "programCode",
                "academicSessionCode"), Arrays.stream(AdminDashboardResponse.CourseOfferingPreview.class.getRecordComponents())
                .map(component -> component.getName()).toList());
        assertEquals(List.of("academicMasters", "academicOperations", "courseOfferings"),
                Arrays.stream(AdminDashboardResponse.class.getRecordComponents()).map(component -> component.getName()).toList());
    }
}
