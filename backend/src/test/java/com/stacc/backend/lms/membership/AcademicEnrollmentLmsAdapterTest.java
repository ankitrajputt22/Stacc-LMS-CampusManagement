package com.stacc.backend.lms.membership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.stacc.backend.academic.admin.LmsProvisioningOutcome;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.lms.access.LmsStudentAccessPolicy;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class AcademicEnrollmentLmsAdapterTest {

    private static final Long ACCOUNT_ID = 70L;
    private static final Long ENROLLMENT_ID = 140L;
    private static final Long OFFERING_ID = 31L;

    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsStudentMembershipRepository memberships = mock(LmsStudentMembershipRepository.class);
    private final LmsStudentMembershipProvisioningService provisioning =
            new LmsStudentMembershipProvisioningService(courseEnrollments, lmsCourses, memberships);
    private final AcademicEnrollmentLmsAdapter adapter = new AcademicEnrollmentLmsAdapter(courseEnrollments, lmsCourses, provisioning);

    private final CourseEnrollment courseEnrollment = courseEnrollment();
    private final LmsCourse lmsCourse = new LmsCourse(courseEnrollment.getCourseOffering());

    private static CourseEnrollment courseEnrollment() {
        Department department = new Department("CSE", "Computer Science and Engineering");
        Program program = new Program(department, "BTECH-CSE", "Bachelor of Technology", 8);
        AcademicSession session = new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
        Semester semester = new Semester(program, session, 3);
        CourseOffering offering = new CourseOffering(new Course(department, "CS301", "Data Structures", new BigDecimal("4.00")), semester);
        ReflectionTestUtils.setField(offering, "id", OFFERING_ID);
        UserAccount account = new UserAccount("STU-TEST-001", "example-hash-value");
        ReflectionTestUtils.setField(account, "id", ACCOUNT_ID);
        CourseEnrollment enrollment = new CourseEnrollment(
                new SemesterEnrollment(new StudentProfile(account, program, session), semester), offering);
        ReflectionTestUtils.setField(enrollment, "id", ENROLLMENT_ID);
        return enrollment;
    }

    @BeforeEach
    void setUp() {
        when(courseEnrollments.findById(ENROLLMENT_ID)).thenReturn(Optional.of(courseEnrollment));
        when(memberships.findByCourseEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.empty());
        when(memberships.save(any(LmsStudentMembership.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void anOfferingWithAnLmsCourseGivesTheStudentAnActiveMembershipThroughTheExistingService() {
        when(lmsCourses.findByCourseOfferingId(OFFERING_ID)).thenReturn(Optional.of(lmsCourse));

        assertEquals(LmsProvisioningOutcome.PROVISIONED, adapter.provision(ENROLLMENT_ID));

        verify(memberships).save(any(LmsStudentMembership.class));
        verify(lmsCourses, never()).save(any());
    }

    @Test
    void anOfferingWithoutAnLmsCourseIsReportedAndNoLmsCourseIsCreated() {
        when(lmsCourses.findByCourseOfferingId(OFFERING_ID)).thenReturn(Optional.empty());

        assertEquals(LmsProvisioningOutcome.NO_LMS_COURSE, adapter.provision(ENROLLMENT_ID));

        verify(lmsCourses, never()).save(any());
        verify(memberships, never()).save(any());
    }

    @Test
    void aMissingEnrollmentAnswers404() {
        ApiException refused = assertThrows(ApiException.class, () -> adapter.provision(99L));

        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
    }

    @Test
    void anEndedEnrollmentLosesLmsAccessAndItsMembershipIsKeptInactive() {
        lmsCourse.publish();
        LmsStudentMembership membership = new LmsStudentMembership(courseEnrollment, lmsCourse);
        when(memberships.findByCourseEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.of(membership));
        assertTrue(LmsStudentAccessPolicy.grantsCurrentAccess(membership, ACCOUNT_ID));

        courseEnrollment.withdraw();
        // The access policy refuses an ended enrollment on its own, before the membership is touched.
        assertFalse(LmsStudentAccessPolicy.grantsCurrentAccess(membership, ACCOUNT_ID));

        adapter.deactivate(ENROLLMENT_ID);

        assertEquals(LmsStudentMembershipStatus.INACTIVE, membership.getStatus());
        assertFalse(LmsStudentAccessPolicy.grantsCurrentAccess(membership, ACCOUNT_ID));
        verify(memberships, never()).delete(any());
        verify(memberships, never()).deleteById(any());
    }
}
