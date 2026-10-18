package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentStatusChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.SemesterEnrollmentCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.SemesterEnrollmentStatusChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseEnrollmentResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.SemesterEnrollmentResponse;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class AcademicEnrollmentManagementServiceTest {

    private final StudentProfileRepository students = mock(StudentProfileRepository.class);
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
    private final SemesterEnrollmentRepository semesterEnrollments = mock(SemesterEnrollmentRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final AcademicEnrollmentManagementService service = new AcademicEnrollmentManagementService(
            students, semesters, offerings, semesterEnrollments, courseEnrollments);
    private final EnrollmentFixtures college = new EnrollmentFixtures();
    private final SemesterEnrollment thirdSemester = college.semesterEnrollment(college.studentA, college.third, 88);

    @BeforeEach
    void setUp() {
        when(students.findWithContextById(57L)).thenReturn(Optional.of(college.studentA));
        when(semesters.findWithContextById(9L)).thenReturn(Optional.of(college.third));
        when(semesters.findWithContextById(11L)).thenReturn(Optional.of(college.mtechFirst));
        when(semesterEnrollments.findByIdForUpdate(88L)).thenReturn(Optional.of(thirdSemester));
        when(offerings.findWithContextById(31L)).thenReturn(Optional.of(college.offeringA));
        when(offerings.findWithContextById(32L)).thenReturn(Optional.of(college.offeringB));
        when(offerings.findWithContextById(33L)).thenReturn(Optional.of(college.fourthSemesterOffering));
        when(semesterEnrollments.saveAndFlush(any(SemesterEnrollment.class)))
                .thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 89L));
        when(courseEnrollments.saveAndFlush(any(CourseEnrollment.class)))
                .thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 141L));
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    private static SemesterEnrollmentStatusChange semesterTo(SemesterEnrollmentStatus status) {
        return new SemesterEnrollmentStatusChange(status);
    }

    private static CourseEnrollmentStatusChange courseTo(CourseEnrollmentStatus status) {
        return new CourseEnrollmentStatusChange(status);
    }

    @Test
    void aStudentIsEnrolledInASemesterOfTheirProgramAndStartsEnrolled() {
        SemesterEnrollmentResponse created = service.createSemesterEnrollment(57L, new SemesterEnrollmentCreate(9L));

        assertEquals(new SemesterEnrollmentResponse(89L, 57L, 9L, 3, 4L, "BTECH-CSE", 2L, "2026-27", "ENROLLED"), created);
    }

    @Test
    void theCreateRequestsCarryNoStatusOrOwnerSoTheClientCannotChooseThem() {
        assertEquals(List.of("semesterId"), components(SemesterEnrollmentCreate.class));
        assertEquals(List.of("courseOfferingId"), components(CourseEnrollmentCreate.class));
    }

    private static List<String> components(Class<? extends Record> request) {
        return Arrays.stream(request.getRecordComponents()).map(component -> component.getName()).toList();
    }

    @Test
    void aMissingStudentOrSemesterAnswers404AndNothingIsSaved() {
        assertRefused(HttpStatus.NOT_FOUND, AcademicEnrollmentManagementService.STUDENT_NOT_FOUND,
                () -> service.createSemesterEnrollment(99L, new SemesterEnrollmentCreate(9L)));
        assertRefused(HttpStatus.NOT_FOUND, AcademicEnrollmentManagementService.SEMESTER_MISSING,
                () -> service.createSemesterEnrollment(57L, new SemesterEnrollmentCreate(99L)));
        verify(semesterEnrollments, never()).saveAndFlush(any());
    }

    @Test
    void anInactiveStudentAnotherProgramsSemesterAndADuplicateAreRefused() {
        assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.OTHER_PROGRAM,
                () -> service.createSemesterEnrollment(57L, new SemesterEnrollmentCreate(11L)));

        when(semesterEnrollments.findByStudentProfileIdAndSemesterId(57L, 9L)).thenReturn(Optional.of(thirdSemester));
        assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.SEMESTER_TAKEN,
                () -> service.createSemesterEnrollment(57L, new SemesterEnrollmentCreate(9L)));

        StudentProfile inactive = college.student(58, 71, "STU-TEST-002");
        inactive.deactivate();
        when(students.findWithContextById(58L)).thenReturn(Optional.of(inactive));
        assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.STUDENT_INACTIVE,
                () -> service.createSemesterEnrollment(58L, new SemesterEnrollmentCreate(9L)));
        verify(semesterEnrollments, never()).saveAndFlush(any());
    }

    @Test
    void aRacingDuplicateSemesterEnrollmentGetsTheSameSafeConflict() {
        when(semesterEnrollments.saveAndFlush(any(SemesterEnrollment.class)))
                .thenThrow(new DataIntegrityViolationException("uk_semester_enrollments_student_semester"));

        assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.SEMESTER_TAKEN,
                () -> service.createSemesterEnrollment(57L, new SemesterEnrollmentCreate(9L)));
    }

    @Test
    void anEnrolledSemesterEnrollmentCompletesOrWithdrawsOnceNoCourseIsStillEnrolled() {
        assertEquals("COMPLETED", service.changeSemesterEnrollmentStatus(88L, semesterTo(SemesterEnrollmentStatus.COMPLETED)).status());

        SemesterEnrollment fourthSemester = college.semesterEnrollment(college.studentA, college.fourth, 90);
        when(semesterEnrollments.findByIdForUpdate(90L)).thenReturn(Optional.of(fourthSemester));
        assertEquals("WITHDRAWN", service.changeSemesterEnrollmentStatus(90L, semesterTo(SemesterEnrollmentStatus.WITHDRAWN)).status());
        verify(semesterEnrollments).saveAndFlush(thirdSemester);
        verify(semesterEnrollments).saveAndFlush(fourthSemester);
    }

    @Test
    void anEnrolledCourseEnrollmentBlocksEndingTheSemesterEnrollmentAndNothingCascades() {
        CourseEnrollment active = college.courseEnrollment(thirdSemester, college.offeringA, 140);
        when(courseEnrollments.existsBySemesterEnrollmentIdAndStatus(88L, CourseEnrollmentStatus.ENROLLED)).thenReturn(true);

        for (SemesterEnrollmentStatus target : new SemesterEnrollmentStatus[] {SemesterEnrollmentStatus.COMPLETED, SemesterEnrollmentStatus.WITHDRAWN}) {
            assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.ACTIVE_COURSES,
                    () -> service.changeSemesterEnrollmentStatus(88L, semesterTo(target)));
        }
        assertEquals(SemesterEnrollmentStatus.ENROLLED, thirdSemester.getStatus());
        assertEquals(CourseEnrollmentStatus.ENROLLED, active.getStatus());
        verify(semesterEnrollments, never()).saveAndFlush(any());
        verify(courseEnrollments, never()).saveAndFlush(any());
    }

    @Test
    void theSameSemesterStatusChangesNothingAndAnEndedOneNeverChangesAgain() {
        assertEquals("ENROLLED", service.changeSemesterEnrollmentStatus(88L, semesterTo(SemesterEnrollmentStatus.ENROLLED)).status());
        verify(semesterEnrollments, never()).saveAndFlush(any());

        service.changeSemesterEnrollmentStatus(88L, semesterTo(SemesterEnrollmentStatus.COMPLETED));
        assertEquals("COMPLETED", service.changeSemesterEnrollmentStatus(88L, semesterTo(SemesterEnrollmentStatus.COMPLETED)).status());
        for (SemesterEnrollmentStatus target : new SemesterEnrollmentStatus[] {SemesterEnrollmentStatus.ENROLLED, SemesterEnrollmentStatus.WITHDRAWN}) {
            assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.SEMESTER_ENDED,
                    () -> service.changeSemesterEnrollmentStatus(88L, semesterTo(target)));
        }

        SemesterEnrollment withdrawn = college.semesterEnrollment(college.studentA, college.fourth, 90);
        withdrawn.withdraw();
        when(semesterEnrollments.findByIdForUpdate(90L)).thenReturn(Optional.of(withdrawn));
        for (SemesterEnrollmentStatus target : new SemesterEnrollmentStatus[] {SemesterEnrollmentStatus.ENROLLED, SemesterEnrollmentStatus.COMPLETED}) {
            assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.SEMESTER_ENDED,
                    () -> service.changeSemesterEnrollmentStatus(90L, semesterTo(target)));
        }
        assertEquals(SemesterEnrollmentStatus.WITHDRAWN, withdrawn.getStatus());
        assertRefused(HttpStatus.NOT_FOUND, AcademicEnrollmentManagementService.SEMESTER_ENROLLMENT_NOT_FOUND,
                () -> service.changeSemesterEnrollmentStatus(99L, semesterTo(SemesterEnrollmentStatus.COMPLETED)));
    }

    @Test
    void aStudentIsEnrolledInAnOfferingOfTheSameSemesterUnderTheLockedSemesterEnrollment() {
        CourseEnrollmentResponse created = service.createCourseEnrollment(88L, new CourseEnrollmentCreate(32L));

        assertEquals(new CourseEnrollmentResponse(141L, 88L, 32L, 13L, "CS302", "Course CS302", new BigDecimal("3.00"), "ENROLLED"), created);
        verify(semesterEnrollments).findByIdForUpdate(88L);
    }

    @Test
    void anOfferingOfAnotherSemesterIsRefused() {
        assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.OTHER_SEMESTER,
                () -> service.createCourseEnrollment(88L, new CourseEnrollmentCreate(33L)));
        verify(courseEnrollments, never()).saveAndFlush(any());
    }

    @Test
    void aDuplicateAMissingOfferingAndAnEndedSemesterEnrollmentAreRefused() {
        when(courseEnrollments.findBySemesterEnrollmentIdAndCourseOfferingId(88L, 31L))
                .thenReturn(Optional.of(college.courseEnrollment(thirdSemester, college.offeringA, 140)));
        assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.COURSE_TAKEN,
                () -> service.createCourseEnrollment(88L, new CourseEnrollmentCreate(31L)));
        assertRefused(HttpStatus.NOT_FOUND, AcademicEnrollmentManagementService.OFFERING_MISSING,
                () -> service.createCourseEnrollment(88L, new CourseEnrollmentCreate(99L)));
        assertRefused(HttpStatus.NOT_FOUND, AcademicEnrollmentManagementService.SEMESTER_ENROLLMENT_NOT_FOUND,
                () -> service.createCourseEnrollment(99L, new CourseEnrollmentCreate(31L)));

        thirdSemester.markCompleted();
        assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.NOT_ENROLLED_IN_SEMESTER,
                () -> service.createCourseEnrollment(88L, new CourseEnrollmentCreate(32L)));
        verify(courseEnrollments, never()).saveAndFlush(any());
    }

    @Test
    void aRacingDuplicateCourseEnrollmentGetsTheSameSafeConflict() {
        when(courseEnrollments.saveAndFlush(any(CourseEnrollment.class)))
                .thenThrow(new DataIntegrityViolationException("uk_course_enrollments_semester_offering"));

        assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.COURSE_TAKEN,
                () -> service.createCourseEnrollment(88L, new CourseEnrollmentCreate(32L)));
    }

    @Test
    void anEnrolledCourseEnrollmentCompletesOrWithdrawsOnceAndNeverReopens() {
        CourseEnrollment first = college.courseEnrollment(thirdSemester, college.offeringA, 140);
        CourseEnrollment second = college.courseEnrollment(thirdSemester, college.offeringB, 142);
        when(courseEnrollments.findByIdForUpdate(140L)).thenReturn(Optional.of(first));
        when(courseEnrollments.findByIdForUpdate(142L)).thenReturn(Optional.of(second));

        assertEquals("ENROLLED", service.changeCourseEnrollmentStatus(140L, courseTo(CourseEnrollmentStatus.ENROLLED)).status());
        verify(courseEnrollments, never()).saveAndFlush(any());
        assertEquals("COMPLETED", service.changeCourseEnrollmentStatus(140L, courseTo(CourseEnrollmentStatus.COMPLETED)).status());
        assertEquals("WITHDRAWN", service.changeCourseEnrollmentStatus(142L, courseTo(CourseEnrollmentStatus.WITHDRAWN)).status());
        assertEquals("COMPLETED", service.changeCourseEnrollmentStatus(140L, courseTo(CourseEnrollmentStatus.COMPLETED)).status());

        for (CourseEnrollmentStatus target : new CourseEnrollmentStatus[] {CourseEnrollmentStatus.ENROLLED, CourseEnrollmentStatus.WITHDRAWN}) {
            assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.COURSE_ENDED,
                    () -> service.changeCourseEnrollmentStatus(140L, courseTo(target)));
        }
        for (CourseEnrollmentStatus target : new CourseEnrollmentStatus[] {CourseEnrollmentStatus.ENROLLED, CourseEnrollmentStatus.COMPLETED}) {
            assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.COURSE_ENDED,
                    () -> service.changeCourseEnrollmentStatus(142L, courseTo(target)));
        }
        assertEquals(CourseEnrollmentStatus.COMPLETED, first.getStatus());
        assertEquals(CourseEnrollmentStatus.WITHDRAWN, second.getStatus());
        assertRefused(HttpStatus.NOT_FOUND, AcademicEnrollmentManagementService.COURSE_ENROLLMENT_NOT_FOUND,
                () -> service.changeCourseEnrollmentStatus(99L, courseTo(CourseEnrollmentStatus.COMPLETED)));
    }

    @Test
    void theSemesterEnrollmentCompletesAfterEachCourseHasBeenEndedOnItsOwn() {
        CourseEnrollment first = college.courseEnrollment(thirdSemester, college.offeringA, 140);
        when(courseEnrollments.findByIdForUpdate(140L)).thenReturn(Optional.of(first));
        when(courseEnrollments.existsBySemesterEnrollmentIdAndStatus(88L, CourseEnrollmentStatus.ENROLLED)).thenReturn(true);
        assertRefused(HttpStatus.CONFLICT, AcademicEnrollmentManagementService.ACTIVE_COURSES,
                () -> service.changeSemesterEnrollmentStatus(88L, semesterTo(SemesterEnrollmentStatus.COMPLETED)));

        service.changeCourseEnrollmentStatus(140L, courseTo(CourseEnrollmentStatus.COMPLETED));
        when(courseEnrollments.existsBySemesterEnrollmentIdAndStatus(88L, CourseEnrollmentStatus.ENROLLED)).thenReturn(false);

        assertEquals("COMPLETED", service.changeSemesterEnrollmentStatus(88L, semesterTo(SemesterEnrollmentStatus.COMPLETED)).status());
    }

    @Test
    void onlyAnEnrolledCourseEnrollmentCanBeOfferedLmsProvisioningAgain() {
        CourseEnrollment active = college.courseEnrollment(thirdSemester, college.offeringA, 140);
        when(courseEnrollments.findWithOfferingById(140L)).thenReturn(Optional.of(active));

        assertEquals(140L, service.requireEnrolledCourseEnrollment(140L).courseEnrollmentId());
        active.withdraw();
        assertRefused(HttpStatus.CONFLICT, CourseEnrollmentWorkflow.NOT_ENROLLED, () -> service.requireEnrolledCourseEnrollment(140L));
        assertRefused(HttpStatus.NOT_FOUND, AcademicEnrollmentManagementService.COURSE_ENROLLMENT_NOT_FOUND,
                () -> service.requireEnrolledCourseEnrollment(99L));
    }
}
