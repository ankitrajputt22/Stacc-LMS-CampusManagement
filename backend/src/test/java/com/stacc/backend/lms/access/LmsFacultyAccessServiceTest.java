package com.stacc.backend.lms.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.academic.teaching.TeachingAssignmentStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Every test starts from a faculty member who is allowed into a draft course, and then changes
 * exactly one thing. That is how each rule is shown to matter on its own.
 */
class LmsFacultyAccessServiceTest {

    // Made-up IDs. The LMS course ID is deliberately different from the course offering ID.
    private static final Long ACCOUNT_ID = 21L;
    private static final Long OTHER_ACCOUNT_ID = 22L;
    private static final Long LMS_COURSE_ID = 7L;
    private static final Long OFFERING_ID = 50L;
    private static final Long OTHER_OFFERING_ID = 72L;

    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final LmsFacultyAccessService service = new LmsFacultyAccessService(lmsCourses, teachingAssignments);

    private final Department department = new Department("CSE", "Computer Science and Engineering");
    private final Program program = new Program(department, "BTECH", "Bachelor of Technology", 8);
    private final AcademicSession session =
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
    private final Semester semester = new Semester(program, session, 3);

    private final FacultyProfile faculty = faculty("TEST-FACULTY-1", ACCOUNT_ID);
    private final CourseOffering offering = offering("TST301", OFFERING_ID);
    private final LmsCourse lmsCourse = lmsCourse(offering, LMS_COURSE_ID);
    private final TeachingAssignment assignment = new TeachingAssignment(faculty, offering);

    private FacultyProfile faculty(String loginId, Long accountId) {
        UserAccount account = new UserAccount(loginId, "example-hash-value");
        ReflectionTestUtils.setField(account, "id", accountId);
        return new FacultyProfile(account, department);
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
    void facultyMemberWhoIsAllowedIn() {
        when(lmsCourses.findById(LMS_COURSE_ID)).thenReturn(Optional.of(lmsCourse));
        foundAssignments(assignment);
    }

    private void foundAssignments(TeachingAssignment... found) {
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(ACCOUNT_ID, OFFERING_ID))
                .thenReturn(List.of(found));
    }

    private boolean canAccess() {
        return service.canAccessCourse(ACCOUNT_ID, LMS_COURSE_ID);
    }

    @Test
    void activeFacultyMemberWithAnActiveAssignmentCanUseADraftCourse() {
        assertEquals(LmsCourseStatus.DRAFT, lmsCourse.getStatus());

        assertTrue(canAccess());
    }

    @Test
    void activeFacultyMemberWithAnActiveAssignmentCanUseAPublishedCourse() {
        lmsCourse.publish();

        assertTrue(canAccess());
    }

    @Test
    void archivedLmsCourseIsDeniedWithoutLookingForAssignments() {
        lmsCourse.archive();

        assertFalse(canAccess());
        verifyNoInteractions(teachingAssignments);
    }

    @Test
    void inactiveFacultyProfileIsDenied() {
        faculty.deactivate();

        assertFalse(canAccess());
    }

    @Test
    void inactiveTeachingAssignmentIsDenied() {
        assignment.deactivate();

        assertFalse(canAccess());
    }

    @Test
    void facultyMemberWithoutAnAssignmentForTheOfferingIsDenied() {
        foundAssignments();

        assertFalse(canAccess());
    }

    @Test
    void accountWithoutAFacultyProfileIsDenied() {
        // Without a profile the lookup, which goes through the profile, finds nothing.
        foundAssignments();

        assertFalse(service.canAccessCourse(ACCOUNT_ID, LMS_COURSE_ID));
    }

    @Test
    void assignmentOfAnotherFacultyMemberIsDenied() {
        // Even if the lookup returned someone else's assignment, it would not count for this account.
        foundAssignments(new TeachingAssignment(faculty("TEST-FACULTY-2", OTHER_ACCOUNT_ID), offering));

        assertFalse(canAccess());
    }

    @Test
    void assignmentForAnotherCourseOfferingIsDenied() {
        // Even if the lookup returned an assignment for another offering, it would not count.
        foundAssignments(new TeachingAssignment(faculty, offering("TST302", OTHER_OFFERING_ID)));

        assertFalse(canAccess());
    }

    @Test
    void otherFacultyAccountIsDeniedTheCourse() {
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(OTHER_ACCOUNT_ID, OFFERING_ID))
                .thenReturn(List.of());

        assertFalse(service.canAccessCourse(OTHER_ACCOUNT_ID, LMS_COURSE_ID));
    }

    @Test
    void unknownLmsCourseIsDeniedWithoutLookingForAssignments() {
        when(lmsCourses.findById(LMS_COURSE_ID)).thenReturn(Optional.empty());

        assertFalse(canAccess());
        verifyNoInteractions(teachingAssignments);
    }

    @Test
    void missingIdsAreDeniedWithoutAnyLookup() {
        assertFalse(service.canAccessCourse(null, LMS_COURSE_ID));
        assertFalse(service.canAccessCourse(ACCOUNT_ID, null));

        verifyNoInteractions(lmsCourses, teachingAssignments);
    }

    @Test
    void checkingAccessOnlyReadsAndChangesNothing() {
        assertTrue(canAccess());

        verify(lmsCourses).findById(LMS_COURSE_ID);
        verify(teachingAssignments).findAllByUserAccountIdAndCourseOfferingId(ACCOUNT_ID, OFFERING_ID);
        verifyNoMoreInteractions(lmsCourses, teachingAssignments);
        assertEquals(LmsCourseStatus.DRAFT, lmsCourse.getStatus());
        assertEquals(TeachingAssignmentStatus.ACTIVE, assignment.getStatus());
        assertEquals(FacultyProfileStatus.ACTIVE, faculty.getStatus());
    }
}
