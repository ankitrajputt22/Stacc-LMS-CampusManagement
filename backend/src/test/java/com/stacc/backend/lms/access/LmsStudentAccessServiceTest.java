package com.stacc.backend.lms.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Access is worked out live from the official enrollment and the course's state. Every test starts from a student who
 * is allowed in, and then changes exactly one thing, so each rule is shown to matter on its own.
 */
class LmsStudentAccessServiceTest {

    // Made-up IDs. The LMS course ID is deliberately different from the course offering ID.
    private static final Long ACCOUNT_ID = 12L;
    private static final Long OTHER_ACCOUNT_ID = 13L;
    private static final Long LMS_COURSE_ID = 7L;
    private static final Long OFFERING_ID = 50L;
    private static final Long OTHER_OFFERING_ID = 72L;

    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final LmsStudentAccessService service = new LmsStudentAccessService(lmsCourses, courseEnrollments);

    private final Department department = new Department("CSE", "Computer Science and Engineering");
    private final Program program = new Program(department, "BTECH", "Bachelor of Technology", 8);
    private final AcademicSession session =
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
    private final Semester semester = new Semester(program, session, 3);

    private final StudentProfile student = student("TEST-STUDENT-1", ACCOUNT_ID);
    private final SemesterEnrollment semesterEnrollment = new SemesterEnrollment(student, semester);
    private final CourseOffering offering = offering("TST301", OFFERING_ID);
    private final CourseEnrollment courseEnrollment = new CourseEnrollment(semesterEnrollment, offering);
    private final LmsCourse lmsCourse = lmsCourse(offering, LMS_COURSE_ID);

    private StudentProfile student(String loginId, Long accountId) {
        UserAccount account = new UserAccount(loginId, "example-hash-value");
        ReflectionTestUtils.setField(account, "id", accountId);
        return new StudentProfile(account, program, session);
    }

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
    void studentWhoIsAllowedIn() {
        lmsCourse.publish();
        when(lmsCourses.findById(LMS_COURSE_ID)).thenReturn(Optional.of(lmsCourse));
        foundEnrollments(courseEnrollment);
    }

    private void foundEnrollments(CourseEnrollment... found) {
        when(courseEnrollments.findAllWithStudentByUserAccountIdAndCourseOfferingId(ACCOUNT_ID, OFFERING_ID))
                .thenReturn(List.of(found));
    }

    private boolean canAccess() {
        return service.canAccessCourse(ACCOUNT_ID, LMS_COURSE_ID);
    }

    @Test
    void enrolledStudentInAPublishedCourseIsAllowedWithoutAnyMembership() {
        assertTrue(canAccess());
    }

    @Test
    void studentWithoutAnEnrollmentInTheOfferingIsDenied() {
        foundEnrollments();

        assertFalse(canAccess());
    }

    @Test
    void inactiveStudentProfileIsDenied() {
        student.deactivate();

        assertFalse(canAccess());
    }

    @Test
    void unknownLmsCourseIsDenied() {
        when(lmsCourses.findById(LMS_COURSE_ID)).thenReturn(Optional.empty());

        assertFalse(canAccess());
    }

    @Test
    void draftLmsCourseIsDeniedEvenWithAnEnrolledStudent() {
        when(lmsCourses.findById(LMS_COURSE_ID)).thenReturn(Optional.of(lmsCourse(offering, LMS_COURSE_ID)));

        assertFalse(canAccess());
    }

    @Test
    void archivedLmsCourseIsDenied() {
        lmsCourse.archive();

        assertFalse(canAccess());
    }

    @Test
    void endedSemesterEnrollmentsAreDenied() {
        semesterEnrollment.markCompleted();
        assertFalse(canAccess());
    }

    @Test
    void withdrawnSemesterEnrollmentIsDenied() {
        semesterEnrollment.withdraw();

        assertFalse(canAccess());
    }

    @Test
    void completedCourseEnrollmentIsDenied() {
        courseEnrollment.markCompleted();

        assertFalse(canAccess());
        assertEquals(LmsCourseStatus.PUBLISHED, lmsCourse.getStatus());
    }

    @Test
    void withdrawnCourseEnrollmentIsDenied() {
        courseEnrollment.withdraw();

        assertFalse(canAccess());
    }

    @Test
    void droppingTheCourseEndsAccessAtOnce() {
        assertTrue(canAccess());

        courseEnrollment.drop(java.time.Instant.parse("2026-07-05T06:00:00Z"));

        assertFalse(canAccess());
    }

    @Test
    void courseEnrollmentForAnotherOfferingIsDenied() {
        foundEnrollments(new CourseEnrollment(semesterEnrollment, offering("TST302", OTHER_OFFERING_ID)));

        assertFalse(canAccess());
    }

    @Test
    void sameOfferingHeldAsDifferentObjectsStillMatches() {
        foundEnrollments(new CourseEnrollment(semesterEnrollment, offering("TST301", OFFERING_ID)));

        assertTrue(canAccess());
    }

    @Test
    void anotherStudentsEnrollmentIsDenied() {
        StudentProfile classmate = student("TEST-STUDENT-2", OTHER_ACCOUNT_ID);
        foundEnrollments(new CourseEnrollment(new SemesterEnrollment(classmate, semester), offering));

        assertFalse(canAccess());
    }

    @Test
    void aStrayEnrollmentNeitherGrantsNorBlocksAccess() {
        CourseEnrollment elsewhere = new CourseEnrollment(semesterEnrollment, offering("TST302", OTHER_OFFERING_ID));

        foundEnrollments(elsewhere);
        assertFalse(canAccess());

        foundEnrollments(elsewhere, courseEnrollment);
        assertTrue(canAccess());
    }

    @Test
    void anotherAccountGetsNoAccessFromThisStudentsEnrollment() {
        assertFalse(service.canAccessCourse(OTHER_ACCOUNT_ID, LMS_COURSE_ID));
    }

    @Test
    void missingIdsAreDeniedWithoutAnyLookup() {
        assertFalse(service.canAccessCourse(null, LMS_COURSE_ID));
        assertFalse(service.canAccessCourse(ACCOUNT_ID, null));

        verifyNoInteractions(lmsCourses, courseEnrollments);
    }

    @Test
    void theCheckOnlyReadsAndChangesNothing() {
        assertTrue(canAccess());

        verify(lmsCourses).findById(LMS_COURSE_ID);
        verify(courseEnrollments).findAllWithStudentByUserAccountIdAndCourseOfferingId(ACCOUNT_ID, OFFERING_ID);
        verifyNoMoreInteractions(lmsCourses, courseEnrollments);
        assertEquals(StudentProfileStatus.ACTIVE, student.getStatus());
        assertEquals(SemesterEnrollmentStatus.ENROLLED, semesterEnrollment.getStatus());
        assertEquals(CourseEnrollmentStatus.ENROLLED, courseEnrollment.getStatus());
        assertEquals(LmsCourseStatus.PUBLISHED, lmsCourse.getStatus());
    }

    @Test
    void allowedStudentGetsTheCourseEnrollmentThatGrantsAccess() {
        assertSame(courseEnrollment, service.findAccessGrantingEnrollment(ACCOUNT_ID, LMS_COURSE_ID).orElseThrow());
    }

    @Test
    void theEnrollmentLookupFollowsExactlyTheSameRuleAsTheAccessCheck() {
        courseEnrollment.withdraw();

        assertFalse(canAccess());
        assertTrue(service.findAccessGrantingEnrollment(ACCOUNT_ID, LMS_COURSE_ID).isEmpty());
        assertTrue(service.findAccessGrantingEnrollment(OTHER_ACCOUNT_ID, LMS_COURSE_ID).isEmpty());
        assertTrue(service.findAccessGrantingEnrollment(null, LMS_COURSE_ID).isEmpty());
        assertTrue(service.findAccessGrantingEnrollment(ACCOUNT_ID, null).isEmpty());
    }

    @Test
    void theStudentsToTellAboutACourseAreItsCurrentStudents() {
        CourseEnrollment second = new CourseEnrollment(
                new SemesterEnrollment(student("TEST-STUDENT-2", 14L), semester), offering);
        when(courseEnrollments.findAllCurrentByCourseOfferingId(OFFERING_ID))
                .thenReturn(List.of(courseEnrollment, second, courseEnrollment));

        assertEquals(List.of(ACCOUNT_ID, 14L), service.findStudentAccountIdsWithAccess(LMS_COURSE_ID));
        assertEquals(List.of(), service.findStudentAccountIdsWithAccess(null));
    }

    @Test
    void nobodyIsToldAboutACourseThatIsNotPublished() {
        LmsCourse draft = lmsCourse(offering("TST399", 99L), 9L);
        when(lmsCourses.findById(9L)).thenReturn(Optional.of(draft));

        assertEquals(List.of(), service.findStudentAccountIdsWithAccess(9L));
        verify(courseEnrollments, org.mockito.Mockito.never()).findAllCurrentByCourseOfferingId(99L);
    }
}
