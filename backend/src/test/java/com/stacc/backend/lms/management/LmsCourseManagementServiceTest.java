package com.stacc.backend.lms.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.offering.CourseOfferingStatus;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.academic.teaching.TeachingAssignmentStatus;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.lms.access.LmsFacultyAccessPolicy;
import com.stacc.backend.lms.access.LmsStudentAccessPolicy;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The management service with the real teaching-access rule; only the repositories are stand-ins. Students need no
 * membership: the count in each answer is the offering's current students, and the access policy decides live.
 */
class LmsCourseManagementServiceTest {

    private static final long FACULTY_ACCOUNT = 20L;
    private static final long OTHER_FACULTY_ACCOUNT = 21L;

    private final CourseOfferingRepository courseOfferings = mock(CourseOfferingRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final LmsCourseManagementService service = new LmsCourseManagementService(
            courseOfferings, lmsCourses, new FacultyTeachingAccessService(teachingAssignments), courseEnrollments);

    private final CourseOffering offering = AttendanceFixtures.offering(31, "CS301", "Data Structures");
    private final FacultyProfile faculty = AttendanceFixtures.faculty(FACULTY_ACCOUNT, 7);
    private final TeachingAssignment teaching = AttendanceFixtures.teaching(faculty, offering);
    private final CourseEnrollment studentA = AttendanceFixtures.enrollment(offering, 50, 140, "STU-TEST-A");
    private final CourseEnrollment studentB = AttendanceFixtures.enrollment(offering, 51, 141, "STU-TEST-B");

    @BeforeEach
    void setUp() {
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_ACCOUNT, 31L)).thenReturn(List.of(teaching));
        when(courseOfferings.findById(31L)).thenReturn(Optional.of(offering));
        when(lmsCourses.saveAndFlush(any(LmsCourse.class))).thenAnswer(call -> withId(call.getArgument(0), 42));
        // The query returns only current students; a withdrawn one is never among them.
        when(courseEnrollments.findAllCurrentByCourseOfferingId(31L)).thenReturn(List.of(studentA, studentB));
    }

    private static LmsCourse withId(LmsCourse lmsCourse, long id) {
        ReflectionTestUtils.setField(lmsCourse, "id", id);
        return lmsCourse;
    }

    private LmsCourse stored(LmsCourseStatus status) {
        LmsCourse lmsCourse = withId(new LmsCourse(offering), 42);
        if (status == LmsCourseStatus.PUBLISHED) {
            lmsCourse.publish();
        } else if (status == LmsCourseStatus.ARCHIVED) {
            lmsCourse.archive();
        }
        when(lmsCourses.findByIdForUpdate(42L)).thenReturn(Optional.of(lmsCourse));
        return lmsCourse;
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void theTeachingFacultyCreatesADraftCourseAndLearnsHowManyStudentsAreEnrolled() {
        LmsCourseChange created = service.create(FACULTY_ACCOUNT, 31L);

        assertEquals(new LmsCourseChange(42L, 31L, LmsCourseStatus.DRAFT, 2), created);
    }

    @Test
    void anOfferingTheFacultyDoesNotTeachLooksMissingAndNothingIsCreated() {
        assertRefused(HttpStatus.NOT_FOUND, LmsCourseManagementService.OFFERING_NOT_FOUND, () -> service.create(OTHER_FACULTY_ACCOUNT, 31L));
        assertRefused(HttpStatus.NOT_FOUND, LmsCourseManagementService.OFFERING_NOT_FOUND, () -> service.create(FACULTY_ACCOUNT, 99L));

        teaching.deactivate();
        assertRefused(HttpStatus.NOT_FOUND, LmsCourseManagementService.OFFERING_NOT_FOUND, () -> service.create(FACULTY_ACCOUNT, 31L));
        teaching.activate();
        faculty.deactivate();
        assertRefused(HttpStatus.NOT_FOUND, LmsCourseManagementService.OFFERING_NOT_FOUND, () -> service.create(FACULTY_ACCOUNT, 31L));

        verify(lmsCourses, never()).saveAndFlush(any());
    }

    @Test
    void anOfferingHasAtMostOneLmsCourseEvenWhenTwoRequestsRace() {
        when(lmsCourses.findByCourseOfferingId(31L)).thenReturn(Optional.of(withId(new LmsCourse(offering), 42)));
        assertRefused(HttpStatus.CONFLICT, LmsCourseManagementService.ALREADY_EXISTS, () -> service.create(FACULTY_ACCOUNT, 31L));

        when(lmsCourses.findByCourseOfferingId(31L)).thenReturn(Optional.empty());
        when(lmsCourses.saveAndFlush(any(LmsCourse.class))).thenThrow(new DataIntegrityViolationException("uk_lms_courses_course_offering"));
        assertRefused(HttpStatus.CONFLICT, LmsCourseManagementService.ALREADY_EXISTS, () -> service.create(FACULTY_ACCOUNT, 31L));
    }

    @Test
    void publishingPublishesUnderTheLockAndRepeatingChangesNothing() {
        LmsCourse lmsCourse = stored(LmsCourseStatus.DRAFT);

        LmsCourseChange published = service.publish(FACULTY_ACCOUNT, 42L);

        assertEquals(new LmsCourseChange(42L, 31L, LmsCourseStatus.PUBLISHED, 2), published);
        verify(lmsCourses).findByIdForUpdate(42L);
        assertEquals(LmsCourseStatus.PUBLISHED, service.publish(FACULTY_ACCOUNT, 42L).status());
        assertEquals(LmsCourseStatus.PUBLISHED, lmsCourse.getStatus());
    }

    @Test
    void anArchivedCourseIsNotPublishedAndArchivingAgainChangesNothing() {
        LmsCourse lmsCourse = stored(LmsCourseStatus.ARCHIVED);

        assertRefused(HttpStatus.CONFLICT, LmsCourseManagementService.ARCHIVED_NOT_PUBLISHABLE, () -> service.publish(FACULTY_ACCOUNT, 42L));
        LmsCourseChange archived = service.archive(FACULTY_ACCOUNT, 42L);

        assertEquals(LmsCourseStatus.ARCHIVED, archived.status());
        assertNull(archived.currentStudentCount());
        assertEquals(LmsCourseStatus.ARCHIVED, lmsCourse.getStatus());
        verify(courseEnrollments, never()).findAllCurrentByCourseOfferingId(anyLong());
    }

    @Test
    void anotherFacultysCourseLooksMissingForEveryChange() {
        LmsCourse lmsCourse = stored(LmsCourseStatus.DRAFT);

        for (Executable change : new Executable[] {
                () -> service.publish(OTHER_FACULTY_ACCOUNT, 42L),
                () -> service.archive(OTHER_FACULTY_ACCOUNT, 42L),
                () -> service.publish(FACULTY_ACCOUNT, 99L)}) {
            assertRefused(HttpStatus.NOT_FOUND, LmsCourseManagementService.COURSE_NOT_FOUND, change);
        }
        assertEquals(LmsCourseStatus.DRAFT, lmsCourse.getStatus());
    }

    @Test
    void studentsGetInOnlyWhilePublishedWithoutAnyMembershipAndEverythingIsKept() {
        LmsCourse lmsCourse = stored(LmsCourseStatus.DRAFT);
        assertFalse(LmsStudentAccessPolicy.grantsCurrentAccess(studentA, lmsCourse, 50L));

        service.publish(FACULTY_ACCOUNT, 42L);
        assertTrue(LmsStudentAccessPolicy.grantsCurrentAccess(studentA, lmsCourse, 50L));
        assertFalse(LmsStudentAccessPolicy.grantsCurrentAccess(studentA, lmsCourse, 51L));

        service.archive(FACULTY_ACCOUNT, 42L);
        assertFalse(LmsStudentAccessPolicy.grantsCurrentAccess(studentA, lmsCourse, 50L));
        assertFalse(LmsFacultyAccessPolicy.grantsCurrentAccess(teaching, lmsCourse, FACULTY_ACCOUNT));
        verify(lmsCourses, never()).delete(any());
    }

    @Test
    void aWithdrawnStudentHasNoAccessInAPublishedCourse() {
        LmsCourse lmsCourse = stored(LmsCourseStatus.PUBLISHED);
        CourseEnrollment withdrawn = AttendanceFixtures.enrollment(offering, 52, 142, "STU-TEST-C");
        withdrawn.withdraw();

        assertFalse(LmsStudentAccessPolicy.grantsCurrentAccess(withdrawn, lmsCourse, 52L));
        assertEquals(CourseEnrollmentStatus.WITHDRAWN, withdrawn.getStatus());
    }

    @Test
    void managementNeverChangesAcademicRecords() {
        service.create(FACULTY_ACCOUNT, 31L);
        stored(LmsCourseStatus.DRAFT);
        service.publish(FACULTY_ACCOUNT, 42L);
        service.archive(FACULTY_ACCOUNT, 42L);

        verify(courseOfferings, never()).save(any());
        verify(courseOfferings, never()).saveAndFlush(any());
        verify(courseEnrollments, never()).save(any());
        verify(courseEnrollments, never()).saveAndFlush(any());
        verify(teachingAssignments, never()).save(any());
        assertEquals(CourseOfferingStatus.PLANNED, offering.getStatus());
        assertEquals(CourseEnrollmentStatus.ENROLLED, studentA.getStatus());
        assertEquals(CourseEnrollmentStatus.ENROLLED, studentB.getStatus());
        assertEquals(TeachingAssignmentStatus.ACTIVE, teaching.getStatus());
    }
}
