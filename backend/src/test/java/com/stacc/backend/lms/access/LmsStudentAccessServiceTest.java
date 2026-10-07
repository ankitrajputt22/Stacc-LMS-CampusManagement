package com.stacc.backend.lms.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.identity.student.StudentProfileStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import com.stacc.backend.lms.membership.LmsStudentMembership;
import com.stacc.backend.lms.membership.LmsStudentMembershipRepository;
import com.stacc.backend.lms.membership.LmsStudentMembershipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Every test starts from a student who is allowed in, and then changes exactly one thing.
 * That is how each rule is shown to matter on its own.
 */
class LmsStudentAccessServiceTest {

    // Made-up IDs. The LMS course ID is deliberately different from the course offering ID.
    private static final Long ACCOUNT_ID = 12L;
    private static final Long OTHER_ACCOUNT_ID = 13L;
    private static final Long LMS_COURSE_ID = 7L;
    private static final Long OFFERING_ID = 50L;
    private static final Long OTHER_OFFERING_ID = 72L;

    private final StudentProfileRepository studentProfiles = mock(StudentProfileRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsStudentMembershipRepository memberships = mock(LmsStudentMembershipRepository.class);
    private final LmsStudentAccessService service =
            new LmsStudentAccessService(studentProfiles, lmsCourses, memberships);

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
    private final LmsStudentMembership membership = new LmsStudentMembership(courseEnrollment, lmsCourse);

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
        when(studentProfiles.findByUserAccountId(ACCOUNT_ID)).thenReturn(Optional.of(student));
        when(lmsCourses.findById(LMS_COURSE_ID)).thenReturn(Optional.of(lmsCourse));
        foundMemberships(membership);
    }

    private void foundMemberships(LmsStudentMembership... found) {
        when(memberships.findAllByUserAccountIdAndLmsCourseId(ACCOUNT_ID, LMS_COURSE_ID)).thenReturn(List.of(found));
    }

    private boolean canAccess() {
        return service.canAccessCourse(ACCOUNT_ID, LMS_COURSE_ID);
    }

    @Test
    void enrolledStudentWithActiveMembershipInAPublishedCourseIsAllowed() {
        assertTrue(canAccess());
    }

    @Test
    void accountWithoutAStudentProfileIsDenied() {
        when(studentProfiles.findByUserAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

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
    void draftLmsCourseIsDeniedEvenWithActiveMembershipAndEnrollment() {
        LmsCourse draft = lmsCourse(offering, LMS_COURSE_ID);
        when(lmsCourses.findById(LMS_COURSE_ID)).thenReturn(Optional.of(draft));
        foundMemberships(new LmsStudentMembership(courseEnrollment, draft));

        assertFalse(canAccess());
    }

    @Test
    void archivedLmsCourseIsDenied() {
        lmsCourse.archive();

        assertFalse(canAccess());
    }

    @Test
    void studentWithoutAMembershipIsDenied() {
        foundMemberships();

        assertFalse(canAccess());
    }

    @Test
    void inactiveMembershipIsDeniedEvenWithEnrolledEnrollment() {
        membership.deactivate();

        assertFalse(canAccess());
        assertEquals(CourseEnrollmentStatus.ENROLLED, courseEnrollment.getStatus());
    }

    @Test
    void completedSemesterEnrollmentIsDenied() {
        semesterEnrollment.markCompleted();

        assertFalse(canAccess());
    }

    @Test
    void withdrawnSemesterEnrollmentIsDenied() {
        semesterEnrollment.withdraw();

        assertFalse(canAccess());
    }

    @Test
    void completedCourseEnrollmentIsDeniedEvenWithActiveMembershipInAPublishedCourse() {
        courseEnrollment.markCompleted();

        assertFalse(canAccess());
        assertEquals(LmsStudentMembershipStatus.ACTIVE, membership.getStatus());
        assertEquals(LmsCourseStatus.PUBLISHED, lmsCourse.getStatus());
    }

    @Test
    void withdrawnCourseEnrollmentIsDeniedEvenWithActiveMembershipInAPublishedCourse() {
        courseEnrollment.withdraw();

        assertFalse(canAccess());
    }

    @Test
    void courseEnrollmentForAnotherOfferingIsDenied() {
        CourseEnrollment elsewhere =
                new CourseEnrollment(semesterEnrollment, offering("TST302", OTHER_OFFERING_ID));
        foundMemberships(new LmsStudentMembership(elsewhere, lmsCourse));

        assertFalse(canAccess());
    }

    @Test
    void sameOfferingHeldAsDifferentObjectsStillMatches() {
        CourseEnrollment loadedSeparately =
                new CourseEnrollment(semesterEnrollment, offering("TST301", OFFERING_ID));
        foundMemberships(new LmsStudentMembership(loadedSeparately, lmsCourse));

        assertTrue(canAccess());
    }

    @Test
    void membershipOfAnotherStudentIsDenied() {
        StudentProfile classmate = student("TEST-STUDENT-2", OTHER_ACCOUNT_ID);
        CourseEnrollment theirs = new CourseEnrollment(new SemesterEnrollment(classmate, semester), offering);
        foundMemberships(new LmsStudentMembership(theirs, lmsCourse));

        assertFalse(canAccess());
    }

    @Test
    void membershipInAnotherLmsCourseIsDenied() {
        LmsCourse another = lmsCourse(offering, 8L);
        another.publish();
        foundMemberships(new LmsStudentMembership(courseEnrollment, another));

        assertFalse(canAccess());
    }

    @Test
    void strayInconsistentMembershipNeitherGrantsNorBlocksAccess() {
        CourseEnrollment elsewhere =
                new CourseEnrollment(semesterEnrollment, offering("TST302", OTHER_OFFERING_ID));
        LmsStudentMembership stray = new LmsStudentMembership(elsewhere, lmsCourse);

        foundMemberships(stray);
        assertFalse(canAccess());

        foundMemberships(stray, membership);
        assertTrue(canAccess());
    }

    @Test
    void anotherAccountGetsNoAccessFromThisStudentsMembership() {
        assertFalse(service.canAccessCourse(OTHER_ACCOUNT_ID, LMS_COURSE_ID));
    }

    @Test
    void missingIdsAreDeniedWithoutAnyLookup() {
        assertFalse(service.canAccessCourse(null, LMS_COURSE_ID));
        assertFalse(service.canAccessCourse(ACCOUNT_ID, null));

        verifyNoInteractions(studentProfiles, lmsCourses, memberships);
    }

    @Test
    void allowedCheckOnlyReadsAndChangesNothing() {
        assertTrue(canAccess());

        verify(studentProfiles).findByUserAccountId(ACCOUNT_ID);
        verify(lmsCourses).findById(LMS_COURSE_ID);
        verify(memberships).findAllByUserAccountIdAndLmsCourseId(ACCOUNT_ID, LMS_COURSE_ID);
        verifyNoMoreInteractions(studentProfiles, lmsCourses, memberships);
        assertEquals(StudentProfileStatus.ACTIVE, student.getStatus());
        assertEquals(SemesterEnrollmentStatus.ENROLLED, semesterEnrollment.getStatus());
        assertEquals(CourseEnrollmentStatus.ENROLLED, courseEnrollment.getStatus());
        assertEquals(LmsStudentMembershipStatus.ACTIVE, membership.getStatus());
        assertEquals(LmsCourseStatus.PUBLISHED, lmsCourse.getStatus());
    }

    @Test
    void deniedCheckDoesNotCreateOrReactivateAMembership() {
        membership.deactivate();
        assertFalse(canAccess());

        foundMemberships();
        assertFalse(canAccess());

        assertEquals(LmsStudentMembershipStatus.INACTIVE, membership.getStatus());
        verify(studentProfiles, times(2)).findByUserAccountId(ACCOUNT_ID);
        verify(lmsCourses, times(2)).findById(LMS_COURSE_ID);
        verify(memberships, times(2)).findAllByUserAccountIdAndLmsCourseId(ACCOUNT_ID, LMS_COURSE_ID);
        verifyNoMoreInteractions(studentProfiles, lmsCourses, memberships);
    }
}
