package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.OfferingRosterResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.RosterEntryResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.SemesterCourseEnrollmentsResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.StudentLookupResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.StudentSemesterEnrollmentsResponse;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

class AdminEnrollmentQueryServiceTest {

    private final StudentProfileRepository students = mock(StudentProfileRepository.class);
    private final SemesterEnrollmentRepository semesterEnrollments = mock(SemesterEnrollmentRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
    private final AdminEnrollmentQueryService service =
            new AdminEnrollmentQueryService(students, semesterEnrollments, courseEnrollments, offerings);
    private final EnrollmentFixtures college = new EnrollmentFixtures();

    @Test
    void studentsAreFoundByPartOfTheirLoginIdWithLikeWildcardsTakenLiterally() {
        when(students.searchByLoginId(eq("%STU-TEST%"), any())).thenReturn(List.of(college.studentA));

        List<StudentLookupResponse> found = service.searchStudents("  STU-TEST ");

        assertEquals(List.of(new StudentLookupResponse(57L, "STU-TEST-001", 4L, "BTECH-CSE", "Program BTECH-CSE", "2026-27", "ACTIVE")), found);
        verify(students).searchByLoginId("%STU-TEST%", PageRequest.of(0, AdminEnrollmentQueryService.SEARCH_LIMIT));

        service.searchStudents("50%_off!");
        verify(students).searchByLoginId("%50!%!_off!!%", PageRequest.of(0, 50));
        service.searchStudents(null);
        verify(students).searchByLoginId("%%", PageRequest.of(0, 50));
    }

    @Test
    void anOverlongSearchIsRefusedBeforeTheDatabase() {
        ApiException refused = assertThrows(ApiException.class, () -> service.searchStudents("S".repeat(101)));

        assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
        assertEquals(AdminEnrollmentQueryService.QUERY_TOO_LONG, refused.getMessage());
        verify(students, never()).searchByLoginId(anyString(), any());
    }

    @Test
    void theStudentLookupCarriesOnlyAcademicIdentifiersNeverAccountSecrets() {
        assertEquals(List.of("studentProfileId", "loginId", "programId", "programCode", "programName", "admissionSessionCode",
                "studentProfileStatus"), Arrays.stream(StudentLookupResponse.class.getRecordComponents()).map(c -> c.getName()).toList());
        assertEquals(List.of("courseEnrollmentId", "semesterEnrollmentId", "studentProfileId", "studentLoginId", "status"),
                Arrays.stream(RosterEntryResponse.class.getRecordComponents()).map(c -> c.getName()).toList());
    }

    @Test
    void aStudentsSemesterEnrollmentsComeWithTheStudentAndA404WhenTheStudentIsMissing() {
        SemesterEnrollment fourth = college.semesterEnrollment(college.studentA, college.fourth, 90);
        SemesterEnrollment third = college.semesterEnrollment(college.studentA, college.third, 88);
        when(students.findWithContextById(57L)).thenReturn(Optional.of(college.studentA));
        when(semesterEnrollments.findAllWithContextByStudentProfileId(57L)).thenReturn(List.of(fourth, third));

        StudentSemesterEnrollmentsResponse listed = service.studentSemesterEnrollments(57L);

        assertEquals("STU-TEST-001", listed.student().loginId());
        assertEquals(List.of(90L, 88L), listed.semesterEnrollments().stream().map(e -> e.semesterEnrollmentId()).toList());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.studentSemesterEnrollments(99L)).getStatus());
    }

    @Test
    void aSemesterEnrollmentsCoursesComeWithTheirContext() {
        SemesterEnrollment third = college.semesterEnrollment(college.studentA, college.third, 88);
        when(semesterEnrollments.findWithContextById(88L)).thenReturn(Optional.of(third));
        when(courseEnrollments.findAllWithOfferingBySemesterEnrollmentId(88L))
                .thenReturn(List.of(college.courseEnrollment(third, college.offeringA, 140)));

        SemesterCourseEnrollmentsResponse listed = service.semesterCourseEnrollments(88L);

        assertEquals(88L, listed.semesterEnrollment().semesterEnrollmentId());
        assertEquals(57L, listed.student().studentProfileId());
        assertEquals("CS301", listed.courseEnrollments().get(0).courseCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.semesterCourseEnrollments(99L)).getStatus());
    }

    @Test
    void theRosterListsOnlyThisOfferingsEnrollmentsInTheQuerysLoginIdOrder() {
        StudentProfile studentB = college.student(58, 71, "STU-TEST-002");
        CourseEnrollment a = college.courseEnrollment(college.semesterEnrollment(college.studentA, college.third, 88), college.offeringA, 140);
        CourseEnrollment b = college.courseEnrollment(college.semesterEnrollment(studentB, college.third, 91), college.offeringA, 143);
        b.withdraw();
        when(offerings.findWithContextById(31L)).thenReturn(Optional.of(college.offeringA));
        when(courseEnrollments.findAllWithStudentByCourseOfferingId(31L)).thenReturn(List.of(a, b));

        OfferingRosterResponse roster = service.roster(31L);

        assertEquals(31L, roster.courseOffering().courseOfferingId());
        assertEquals(List.of(new RosterEntryResponse(140L, 88L, 57L, "STU-TEST-001", "ENROLLED"),
                new RosterEntryResponse(143L, 91L, 58L, "STU-TEST-002", "WITHDRAWN")), roster.enrollments());
        verify(courseEnrollments).findAllWithStudentByCourseOfferingId(31L);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.roster(99L)).getStatus());
    }
}
