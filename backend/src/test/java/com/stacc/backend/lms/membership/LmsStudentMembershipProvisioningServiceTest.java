package com.stacc.backend.lms.membership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class LmsStudentMembershipProvisioningServiceTest {

    // Made-up IDs. The LMS course ID is deliberately different from the course offering ID.
    private static final Long ENROLLMENT_ID = 100L;
    private static final Long OFFERING_ID = 50L;
    private static final Long LMS_COURSE_ID = 7L;
    private static final Long OTHER_OFFERING_ID = 72L;

    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsStudentMembershipRepository memberships = mock(LmsStudentMembershipRepository.class);
    private final LmsStudentMembershipProvisioningService service =
            new LmsStudentMembershipProvisioningService(courseEnrollments, lmsCourses, memberships);

    private final Department department = new Department("CSE", "Computer Science and Engineering");
    private final Program program = new Program(department, "BTECH", "Bachelor of Technology", 8);
    private final AcademicSession session =
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
    private final Semester semester = new Semester(program, session, 3);
    private final StudentProfile student =
            new StudentProfile(new UserAccount("TEST-STUDENT-1", "example-hash-value"), program, session);
    private final CourseOffering offering = offering("TST301", OFFERING_ID);
    private final CourseEnrollment courseEnrollment =
            new CourseEnrollment(new SemesterEnrollment(student, semester), offering);
    private final LmsCourse lmsCourse = lmsCourse(offering, LMS_COURSE_ID);

    private CourseOffering offering(String courseCode, Long id) {
        Course course = new Course(department, courseCode, "Test Course " + courseCode, new BigDecimal("4.00"));
        CourseOffering created = new CourseOffering(course, semester);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    private static LmsCourse lmsCourse(CourseOffering courseOffering, Long id) {
        LmsCourse created = new LmsCourse(courseOffering);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    @BeforeEach
    void storedData() {
        ReflectionTestUtils.setField(courseEnrollment, "id", ENROLLMENT_ID);
        when(courseEnrollments.findById(ENROLLMENT_ID)).thenReturn(Optional.of(courseEnrollment));
        when(lmsCourses.findByCourseOfferingId(OFFERING_ID)).thenReturn(Optional.of(lmsCourse));
        when(memberships.findByCourseEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.empty());
        when(memberships.save(any(LmsStudentMembership.class))).thenAnswer(call -> call.getArgument(0));
    }

    private LmsStudentMembership existingMembership(LmsCourse inLmsCourse) {
        LmsStudentMembership membership = new LmsStudentMembership(courseEnrollment, inLmsCourse);
        when(memberships.findByCourseEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.of(membership));
        return membership;
    }

    @Test
    void enrolledCourseEnrollmentGetsAnActiveMembershipInTheMatchingLmsCourse() {
        LmsStudentMembership membership = service.provision(ENROLLMENT_ID);

        assertEquals(LmsStudentMembershipStatus.ACTIVE, membership.getStatus());
        assertSame(courseEnrollment, membership.getCourseEnrollment());
        assertSame(lmsCourse, membership.getLmsCourse());
        verify(memberships).save(membership);
    }

    @Test
    void lmsCourseIsFoundThroughTheCourseOfferingOfTheEnrollment() {
        service.provision(ENROLLMENT_ID);

        verify(lmsCourses).findByCourseOfferingId(OFFERING_ID);
        verifyNoMoreInteractions(lmsCourses);
    }

    @Test
    void provisioningTwiceKeepsOneMembership() {
        LmsStudentMembership first = service.provision(ENROLLMENT_ID);
        when(memberships.findByCourseEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.of(first));

        LmsStudentMembership second = service.provision(ENROLLMENT_ID);
        LmsStudentMembership third = service.provision(ENROLLMENT_ID);

        assertSame(first, second);
        assertSame(first, third);
        assertEquals(LmsStudentMembershipStatus.ACTIVE, third.getStatus());
        verify(memberships, times(1)).save(any(LmsStudentMembership.class));
    }

    @Test
    void existingActiveMembershipIsReturnedAsItIs() {
        LmsStudentMembership existing = existingMembership(lmsCourse);

        LmsStudentMembership membership = service.provision(ENROLLMENT_ID);

        assertSame(existing, membership);
        assertEquals(LmsStudentMembershipStatus.ACTIVE, membership.getStatus());
        verify(memberships, never()).save(any(LmsStudentMembership.class));
    }

    @Test
    void existingInactiveMembershipIsMadeActiveAgain() {
        LmsStudentMembership existing = existingMembership(lmsCourse);
        existing.deactivate();

        LmsStudentMembership membership = service.provision(ENROLLMENT_ID);

        assertSame(existing, membership);
        assertEquals(LmsStudentMembershipStatus.ACTIVE, membership.getStatus());
        verify(memberships, never()).save(any(LmsStudentMembership.class));
    }

    @Test
    void sameOfferingHeldAsDifferentObjectsStillMatches() {
        CourseOffering sameOfferingLoadedAgain = offering("TST301", OFFERING_ID);
        LmsCourse loadedLmsCourse = lmsCourse(sameOfferingLoadedAgain, LMS_COURSE_ID);
        when(lmsCourses.findByCourseOfferingId(OFFERING_ID)).thenReturn(Optional.of(loadedLmsCourse));

        LmsStudentMembership membership = service.provision(ENROLLMENT_ID);

        assertSame(loadedLmsCourse, membership.getLmsCourse());
    }

    @Test
    void missingCourseEnrollmentFailsWithNotFound() {
        when(courseEnrollments.findById(404L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.provision(404L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Course enrollment not found.", exception.getMessage());
        verifyNoInteractions(lmsCourses, memberships);
    }

    @Test
    void courseEnrollmentIdIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> service.provision(null));
        assertThrows(IllegalArgumentException.class, () -> service.deactivate(null));
        verifyNoInteractions(courseEnrollments, lmsCourses, memberships);
    }

    @Test
    void withdrawnCourseEnrollmentIsNotProvisioned() {
        courseEnrollment.withdraw();

        ApiException exception = assertThrows(ApiException.class, () -> service.provision(ENROLLMENT_ID));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(CourseEnrollmentStatus.WITHDRAWN, courseEnrollment.getStatus());
        verifyNoInteractions(lmsCourses, memberships);
    }

    @Test
    void completedCourseEnrollmentIsNotProvisioned() {
        courseEnrollment.markCompleted();

        ApiException exception = assertThrows(ApiException.class, () -> service.provision(ENROLLMENT_ID));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(CourseEnrollmentStatus.COMPLETED, courseEnrollment.getStatus());
        verifyNoInteractions(lmsCourses, memberships);
    }

    @Test
    void endedCourseEnrollmentDoesNotReactivateAnInactiveMembership() {
        LmsStudentMembership existing = existingMembership(lmsCourse);
        existing.deactivate();
        courseEnrollment.withdraw();

        assertThrows(ApiException.class, () -> service.provision(ENROLLMENT_ID));

        assertEquals(LmsStudentMembershipStatus.INACTIVE, existing.getStatus());
    }

    @Test
    void missingLmsCourseFailsAndNoLmsCourseIsCreated() {
        when(lmsCourses.findByCourseOfferingId(OFFERING_ID)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.provision(ENROLLMENT_ID));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("This course offering has no LMS course yet.", exception.getMessage());
        verify(lmsCourses, never()).save(any(LmsCourse.class));
        verifyNoInteractions(memberships);
    }

    @Test
    void lmsCourseOfAnotherOfferingIsRefusedAndNoMembershipIsCreated() {
        LmsCourse wrong = lmsCourse(offering("TST302", OTHER_OFFERING_ID), 8L);
        when(lmsCourses.findByCourseOfferingId(OFFERING_ID)).thenReturn(Optional.of(wrong));

        assertThrows(IllegalStateException.class, () -> service.provision(ENROLLMENT_ID));

        verifyNoInteractions(memberships);
        assertSame(offering, courseEnrollment.getCourseOffering());
    }

    @Test
    void existingMembershipInTheWrongLmsCourseIsRefusedAndLeftUnchanged() {
        LmsCourse wrong = lmsCourse(offering("TST302", OTHER_OFFERING_ID), 8L);
        LmsStudentMembership existing = existingMembership(wrong);
        existing.deactivate();

        assertThrows(IllegalStateException.class, () -> service.provision(ENROLLMENT_ID));

        assertSame(wrong, existing.getLmsCourse());
        assertEquals(LmsStudentMembershipStatus.INACTIVE, existing.getStatus());
        verify(memberships, never()).save(any(LmsStudentMembership.class));
    }

    @Test
    void provisioningOnlyReadsTheCourseEnrollment() {
        service.provision(ENROLLMENT_ID);

        verify(courseEnrollments).findById(ENROLLMENT_ID);
        verifyNoMoreInteractions(courseEnrollments);
        assertEquals(CourseEnrollmentStatus.ENROLLED, courseEnrollment.getStatus());
        assertSame(offering, courseEnrollment.getCourseOffering());
    }

    @Test
    void lmsCourseNeedsNoParticularStatusAndIsLeftAsItIs() {
        LmsStudentMembership inDraft = service.provision(ENROLLMENT_ID);

        assertEquals(LmsStudentMembershipStatus.ACTIVE, inDraft.getStatus());
        assertEquals(LmsCourseStatus.DRAFT, lmsCourse.getStatus());
    }

    @Test
    void activeMembershipIsDeactivated() {
        LmsStudentMembership existing = existingMembership(lmsCourse);

        service.deactivate(ENROLLMENT_ID);

        assertEquals(LmsStudentMembershipStatus.INACTIVE, existing.getStatus());
    }

    @Test
    void deactivatingAnInactiveMembershipLeavesItInactive() {
        LmsStudentMembership existing = existingMembership(lmsCourse);

        service.deactivate(ENROLLMENT_ID);
        service.deactivate(ENROLLMENT_ID);

        assertEquals(LmsStudentMembershipStatus.INACTIVE, existing.getStatus());
    }

    @Test
    void deactivatingWithoutAMembershipDoesNothing() {
        service.deactivate(ENROLLMENT_ID);

        verify(memberships).findByCourseEnrollmentId(ENROLLMENT_ID);
        verifyNoMoreInteractions(memberships);
    }

    @Test
    void deactivatingNeverTouchesTheCourseEnrollmentOrTheLmsCourse() {
        existingMembership(lmsCourse);

        service.deactivate(ENROLLMENT_ID);

        assertEquals(CourseEnrollmentStatus.ENROLLED, courseEnrollment.getStatus());
        assertEquals(LmsCourseStatus.DRAFT, lmsCourse.getStatus());
        verifyNoInteractions(courseEnrollments, lmsCourses);
    }
}
