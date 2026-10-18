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
import com.stacc.backend.lms.membership.LmsStudentMembership;
import com.stacc.backend.lms.membership.LmsStudentMembershipProvisioningService;
import com.stacc.backend.lms.membership.LmsStudentMembershipRepository;
import com.stacc.backend.lms.membership.LmsStudentMembershipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The management service with the real teaching-access rule and the real membership provisioning; only the
 * repositories are stand-ins. Memberships saved by provisioning are kept in a list, the way the database would.
 */
class LmsCourseManagementServiceTest {

    private static final long FACULTY_ACCOUNT = 20L;
    private static final long OTHER_FACULTY_ACCOUNT = 21L;

    private final CourseOfferingRepository courseOfferings = mock(CourseOfferingRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final LmsStudentMembershipRepository memberships = mock(LmsStudentMembershipRepository.class);
    private final LmsStudentMembershipProvisioningService provisioning =
            new LmsStudentMembershipProvisioningService(courseEnrollments, lmsCourses, memberships);
    private final LmsCourseManagementService service = new LmsCourseManagementService(
            courseOfferings, lmsCourses, new FacultyTeachingAccessService(teachingAssignments), provisioning);

    private final CourseOffering offering = AttendanceFixtures.offering(31, "CS301", "Data Structures");
    private final FacultyProfile faculty = AttendanceFixtures.faculty(FACULTY_ACCOUNT, 7);
    private final TeachingAssignment teaching = AttendanceFixtures.teaching(faculty, offering);
    private final CourseEnrollment studentA = AttendanceFixtures.enrollment(offering, 50, 140, "STU-TEST-A");
    private final CourseEnrollment studentB = AttendanceFixtures.enrollment(offering, 51, 141, "STU-TEST-B");
    private final List<LmsStudentMembership> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_ACCOUNT, 31L)).thenReturn(List.of(teaching));
        when(courseOfferings.findById(31L)).thenReturn(Optional.of(offering));
        when(lmsCourses.saveAndFlush(any(LmsCourse.class))).thenAnswer(call -> withId(call.getArgument(0), 42));
        // The query returns only current students; a withdrawn one is never among them.
        when(courseEnrollments.findAllCurrentByCourseOfferingId(31L)).thenReturn(List.of(studentA, studentB));
        when(memberships.save(any(LmsStudentMembership.class))).thenAnswer(call -> {
            saved.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(memberships.findAllByCourseEnrollmentIdIn(anyCollection())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return saved.stream().filter(membership -> ids.contains(membership.getCourseEnrollment().getId())).toList();
        });
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
    void theTeachingFacultyCreatesADraftCourseAndItsCurrentStudentsGetMemberships() {
        LmsCourseChange created = service.create(FACULTY_ACCOUNT, 31L);

        assertEquals(new LmsCourseChange(42L, 31L, LmsCourseStatus.DRAFT, 2), created);
        assertEquals(List.of(140L, 141L), saved.stream().map(membership -> membership.getCourseEnrollment().getId()).toList());
        assertTrue(saved.stream().allMatch(membership -> membership.getStatus() == LmsStudentMembershipStatus.ACTIVE));
        assertTrue(saved.stream().allMatch(membership -> membership.getLmsCourse().getId() == 42L));
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
        verify(memberships, never()).save(any());
    }

    @Test
    void anOfferingHasAtMostOneLmsCourseEvenWhenTwoRequestsRace() {
        when(lmsCourses.findByCourseOfferingId(31L)).thenReturn(Optional.of(withId(new LmsCourse(offering), 42)));
        assertRefused(HttpStatus.CONFLICT, LmsCourseManagementService.ALREADY_EXISTS, () -> service.create(FACULTY_ACCOUNT, 31L));

        when(lmsCourses.findByCourseOfferingId(31L)).thenReturn(Optional.empty());
        when(lmsCourses.saveAndFlush(any(LmsCourse.class))).thenThrow(new DataIntegrityViolationException("uk_lms_courses_course_offering"));
        assertRefused(HttpStatus.CONFLICT, LmsCourseManagementService.ALREADY_EXISTS, () -> service.create(FACULTY_ACCOUNT, 31L));
        verify(memberships, never()).save(any());
    }

    @Test
    void synchronizingTwiceKeepsOneMembershipEachAndReactivatesAnInactiveOne() {
        LmsCourse lmsCourse = stored(LmsCourseStatus.DRAFT);

        assertEquals(2, service.synchronizeStudentMemberships(FACULTY_ACCOUNT, 42L).synchronizedCount());
        saved.get(0).deactivate();
        assertEquals(2, service.synchronizeStudentMemberships(FACULTY_ACCOUNT, 42L).synchronizedCount());

        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(membership -> membership.getStatus() == LmsStudentMembershipStatus.ACTIVE));
        assertEquals(LmsCourseStatus.DRAFT, lmsCourse.getStatus());
    }

    @Test
    void aMembershipLinkedToAnotherLmsCourseIsReportedNotRepaired() {
        stored(LmsCourseStatus.DRAFT);
        CourseOffering other = AttendanceFixtures.offering(32, "CS302", "Databases");
        saved.add(new LmsStudentMembership(studentA, withId(new LmsCourse(other), 43)));

        assertThrows(IllegalStateException.class, () -> service.synchronizeStudentMemberships(FACULTY_ACCOUNT, 42L));
        assertEquals(43L, saved.get(0).getLmsCourse().getId());
    }

    @Test
    void publishingSynchronizesFirstThenPublishesAndRepeatingChangesNothing() {
        LmsCourse lmsCourse = stored(LmsCourseStatus.DRAFT);

        LmsCourseChange published = service.publish(FACULTY_ACCOUNT, 42L);

        assertEquals(new LmsCourseChange(42L, 31L, LmsCourseStatus.PUBLISHED, 2), published);
        var order = inOrder(courseEnrollments, lmsCourses);
        order.verify(lmsCourses).findByIdForUpdate(42L);
        order.verify(courseEnrollments).findAllCurrentByCourseOfferingId(31L);
        assertEquals(LmsCourseStatus.PUBLISHED, service.publish(FACULTY_ACCOUNT, 42L).status());
        assertEquals(2, saved.size());
        assertEquals(LmsCourseStatus.PUBLISHED, lmsCourse.getStatus());
    }

    @Test
    void anArchivedCourseIsNeitherPublishedNorSynchronizedAndArchivingAgainChangesNothing() {
        LmsCourse lmsCourse = stored(LmsCourseStatus.ARCHIVED);

        assertRefused(HttpStatus.CONFLICT, LmsCourseManagementService.ARCHIVED_NOT_PUBLISHABLE, () -> service.publish(FACULTY_ACCOUNT, 42L));
        assertRefused(HttpStatus.CONFLICT, LmsCourseManagementService.ARCHIVED_NOT_SYNCHRONIZED,
                () -> service.synchronizeStudentMemberships(FACULTY_ACCOUNT, 42L));
        LmsCourseChange archived = service.archive(FACULTY_ACCOUNT, 42L);

        assertEquals(LmsCourseStatus.ARCHIVED, archived.status());
        assertNull(archived.synchronizedCount());
        assertEquals(LmsCourseStatus.ARCHIVED, lmsCourse.getStatus());
        verify(courseEnrollments, never()).findAllCurrentByCourseOfferingId(anyLong());
    }

    @Test
    void anotherFacultysCourseLooksMissingForEveryChange() {
        LmsCourse lmsCourse = stored(LmsCourseStatus.DRAFT);

        for (Executable change : new Executable[] {
                () -> service.publish(OTHER_FACULTY_ACCOUNT, 42L),
                () -> service.archive(OTHER_FACULTY_ACCOUNT, 42L),
                () -> service.synchronizeStudentMemberships(OTHER_FACULTY_ACCOUNT, 42L),
                () -> service.publish(FACULTY_ACCOUNT, 99L)}) {
            assertRefused(HttpStatus.NOT_FOUND, LmsCourseManagementService.COURSE_NOT_FOUND, change);
        }
        assertEquals(LmsCourseStatus.DRAFT, lmsCourse.getStatus());
        verify(memberships, never()).save(any());
    }

    @Test
    void studentsGetInOnlyOncePublishedAndLoseAccessWhenArchivedWhileEverythingIsKept() {
        LmsCourse lmsCourse = stored(LmsCourseStatus.DRAFT);
        service.synchronizeStudentMemberships(FACULTY_ACCOUNT, 42L);
        LmsStudentMembership membershipA = saved.get(0);
        assertFalse(LmsStudentAccessPolicy.grantsCurrentAccess(membershipA, 50L));

        service.publish(FACULTY_ACCOUNT, 42L);
        assertTrue(LmsStudentAccessPolicy.grantsCurrentAccess(membershipA, 50L));
        assertFalse(LmsStudentAccessPolicy.grantsCurrentAccess(membershipA, 51L));

        service.archive(FACULTY_ACCOUNT, 42L);
        assertFalse(LmsStudentAccessPolicy.grantsCurrentAccess(membershipA, 50L));
        assertFalse(LmsFacultyAccessPolicy.grantsCurrentAccess(teaching, lmsCourse, FACULTY_ACCOUNT));
        assertEquals(LmsStudentMembershipStatus.ACTIVE, membershipA.getStatus());
        verify(lmsCourses, never()).delete(any());
        verify(memberships, never()).delete(any());
    }

    @Test
    void aWithdrawnStudentKeepsNoAccessEvenWithAnOldMembership() {
        stored(LmsCourseStatus.PUBLISHED);
        CourseEnrollment withdrawn = AttendanceFixtures.enrollment(offering, 52, 142, "STU-TEST-C");
        withdrawn.withdraw();
        LmsStudentMembership old = new LmsStudentMembership(withdrawn, lmsCourses.findByIdForUpdate(42L).orElseThrow());

        service.synchronizeStudentMemberships(FACULTY_ACCOUNT, 42L);

        assertFalse(saved.stream().anyMatch(membership -> membership.getCourseEnrollment() == withdrawn));
        assertFalse(LmsStudentAccessPolicy.grantsCurrentAccess(old, 52L));
        assertEquals(CourseEnrollmentStatus.WITHDRAWN, withdrawn.getStatus());
    }

    @Test
    void managementNeverChangesAcademicRecords() {
        service.create(FACULTY_ACCOUNT, 31L);
        stored(LmsCourseStatus.DRAFT);
        service.synchronizeStudentMemberships(FACULTY_ACCOUNT, 42L);
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
