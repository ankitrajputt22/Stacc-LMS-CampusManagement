package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingUpdate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.SemesterEnrollmentSectionChange;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.offering.CourseOfferingStatus;
import com.stacc.backend.academic.offering.OfferingRegistrationStatus;
import com.stacc.backend.academic.section.Section;
import com.stacc.backend.academic.section.SectionRepository;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;

/** Course offerings per section, and students taking only their own section's offerings. */
class SectionOfferingRulesTest {

    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final SectionRepository sections = mock(SectionRepository.class);
    private final SemesterEnrollmentRepository semesterEnrollments = mock(SemesterEnrollmentRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final CourseOfferingManagementService offeringService = new CourseOfferingManagementService(offerings, courses,
            semesters, sections, mock(OfferingFacultyLoader.class));
    private final AcademicEnrollmentManagementService enrollmentService = new AcademicEnrollmentManagementService(
            mock(StudentProfileRepository.class), semesters, offerings, semesterEnrollments, courseEnrollments, sections);
    private final EnrollmentFixtures college = new EnrollmentFixtures();
    private final Section a = MasterFixtures.withId(new Section(college.third, "A", null, 60), 21L);
    private final Section b = MasterFixtures.withId(new Section(college.third, "B", null, 60), 22L);

    private static void assertConflict(String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.CONFLICT, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    private void offeringContext() {
        when(courses.findById(13L)).thenReturn(Optional.of(college.databases));
        when(semesters.findWithContextById(9L)).thenReturn(Optional.of(college.third));
        when(sections.findWithContextById(21L)).thenReturn(Optional.of(a));
        when(offerings.saveAndFlush(any(CourseOffering.class))).thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 40L));
    }

    @Test
    void aCourseIsOfferedPerSectionWithCapacityAndRegistrationStatus() {
        offeringContext();

        var created = offeringService.create(new CourseOfferingCreate(13L, 9L, 21L, 60, OfferingRegistrationStatus.OPEN));

        assertEquals(21L, created.sectionId());
        assertEquals("A", created.sectionCode());
        assertEquals(60, created.capacity());
        assertEquals("OPEN", created.registrationStatus());
    }

    @Test
    void wholeSemesterAndPerSectionOfferingsOfOneCourseDoNotMix() {
        offeringContext();
        when(offerings.findByCourseIdAndSemesterIdAndSectionIsNull(13L, 9L)).thenReturn(Optional.of(college.offeringB));
        assertConflict(CourseOfferingManagementService.WHOLE_SEMESTER_EXISTS,
                () -> offeringService.create(new CourseOfferingCreate(13L, 9L, 21L, null, null)));

        when(offerings.findByCourseIdAndSemesterIdAndSectionIsNull(13L, 9L)).thenReturn(Optional.empty());
        when(offerings.existsByCourseIdAndSemesterIdAndSectionIsNotNull(13L, 9L)).thenReturn(true);
        assertConflict(CourseOfferingManagementService.PER_SECTION_EXISTS,
                () -> offeringService.create(new CourseOfferingCreate(13L, 9L, null, null, null)));
        verify(offerings, never()).saveAndFlush(any());
    }

    @Test
    void aSectionOfAnotherSemesterOrAnInactiveOneIsRefused() {
        offeringContext();
        Section other = MasterFixtures.withId(new Section(college.fourth, "A", null, null), 23L);
        when(sections.findWithContextById(23L)).thenReturn(Optional.of(other));
        assertConflict(CourseOfferingManagementService.SECTION_OTHER_SEMESTER,
                () -> offeringService.create(new CourseOfferingCreate(13L, 9L, 23L, null, null)));

        a.deactivate();
        assertConflict(CourseOfferingManagementService.SECTION_INACTIVE,
                () -> offeringService.create(new CourseOfferingCreate(13L, 9L, 21L, null, null)));
    }

    @Test
    void anOfferingsSectionIsFixedButItsCapacityAndRegistrationStatusChange() {
        CourseOffering offering = MasterFixtures.withId(new CourseOffering(college.databases, college.third, a), 40L);
        when(offerings.findWithContextById(40L)).thenReturn(Optional.of(offering));
        when(offerings.saveAndFlush(offering)).thenReturn(offering);

        assertConflict(CourseOfferingManagementService.SECTION_FIXED, () -> offeringService.update(40L,
                new CourseOfferingUpdate(CourseOfferingStatus.PLANNED, null, null, 22L, null, null)));

        var changed = offeringService.update(40L,
                new CourseOfferingUpdate(CourseOfferingStatus.PLANNED, null, null, 21L, 45, OfferingRegistrationStatus.CLOSED));
        assertEquals(45, changed.capacity());
        assertEquals("CLOSED", changed.registrationStatus());
    }

    @Test
    void aStudentTakesOnlyTheirOwnSectionsOfferings() {
        CourseOffering forA = MasterFixtures.withId(new CourseOffering(college.databases, college.third, a), 40L);
        SemesterEnrollment enrollment = college.semesterEnrollment(college.studentA, college.third, 88);
        when(semesterEnrollments.findByIdForUpdate(88L)).thenReturn(Optional.of(enrollment));
        when(offerings.findWithContextById(40L)).thenReturn(Optional.of(forA));

        assertConflict("This course offering is for section A. Place the student in section A first.",
                () -> enrollmentService.createCourseEnrollment(88L, new CourseEnrollmentCreate(40L)));
        enrollment.placeIn(b);
        assertConflict("This course offering is for section A. Place the student in section A first.",
                () -> enrollmentService.createCourseEnrollment(88L, new CourseEnrollmentCreate(40L)));
        verify(courseEnrollments, never()).saveAndFlush(any());
    }

    @Test
    void aStudentInTheirSectionsCoursesCannotMoveSection() {
        SemesterEnrollment enrollment = college.semesterEnrollment(college.studentA, college.third, 88);
        enrollment.placeIn(a);
        when(semesterEnrollments.findByIdForUpdate(88L)).thenReturn(Optional.of(enrollment));
        when(courseEnrollments.existsEnrolledInOtherSectionOffering(88L, 22L)).thenReturn(true);
        when(courseEnrollments.existsEnrolledInOtherSectionOffering(88L, null)).thenReturn(true);

        assertConflict(AcademicEnrollmentManagementService.SECTION_COURSES,
                () -> enrollmentService.changeSection(88L, new SemesterEnrollmentSectionChange(22L)));
        assertConflict(AcademicEnrollmentManagementService.SECTION_COURSES,
                () -> enrollmentService.changeSection(88L, new SemesterEnrollmentSectionChange(null)));
    }
}
