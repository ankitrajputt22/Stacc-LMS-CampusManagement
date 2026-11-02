package com.stacc.backend.academic.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.admin.OfferingFacultyLoader;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository.OfferingStatusCount;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.offering.OfferingRegistrationStatus;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.EligibleOffering;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegisteredCourse;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegistrationResult;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegistrationTerm;
import com.stacc.backend.academic.section.Section;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class StudentRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-05T06:00:00Z");
    private static final long ACCOUNT = 21L;

    private final StudentProfileRepository profiles = mock(StudentProfileRepository.class);
    private final SemesterEnrollmentRepository semesterEnrollments = mock(SemesterEnrollmentRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
    private final StudentRegistrationService service = new StudentRegistrationService(profiles, semesterEnrollments,
            courseEnrollments, offerings, mock(OfferingFacultyLoader.class), Clock.fixed(NOW, ZoneOffset.UTC));

    private final Department cse = withId(new Department("CSE", "Computer Science"), 3);
    private final Program btech = withId(new Program(cse, "BTECH", "B.Tech.", 8), 4);
    private final AcademicSession session = withId(new AcademicSession("2026-27",
            java.time.LocalDate.of(2026, 7, 1), java.time.LocalDate.of(2027, 6, 30)), 2);
    private final Semester third = withId(new Semester(btech, session, 3), 5);
    private final Section sectionA = withId(new Section(third, "A", "Section A", 60), 6);
    private final Section sectionB = withId(new Section(third, "B", "Section B", 60), 7);
    private final StudentProfile student = student(ACCOUNT, 31L);
    private final SemesterEnrollment term = withId(new SemesterEnrollment(student, third), 41);
    private final CourseOffering ds = offering(101, "CS301", "4.00", null);
    private final CourseOffering web = offering(102, "CS306", "3.00", sectionA);

    @BeforeEach
    void setUp() {
        third.changeRegistrationPeriod(NOW.minus(1, ChronoUnit.DAYS), NOW.plus(10, ChronoUnit.DAYS),
                NOW.plus(20, ChronoUnit.DAYS));
        term.placeIn(sectionA);
        when(profiles.findWithContextByUserAccountId(ACCOUNT)).thenReturn(Optional.of(student));
        when(semesterEnrollments.findByStudentProfileIdAndSemesterId(31L, 5L)).thenReturn(Optional.of(term));
        when(semesterEnrollments.findAllWithContextByStudentProfileId(31L)).thenReturn(List.of(term));
        when(offerings.findWithContextById(101L)).thenReturn(Optional.of(ds));
        when(offerings.findWithContextById(102L)).thenReturn(Optional.of(web));
        when(courseEnrollments.saveAndFlush(any(CourseEnrollment.class))).thenAnswer(call -> {
            CourseEnrollment saved = call.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 500L + saved.getCourseOffering().getId());
                ReflectionTestUtils.setField(saved, "createdAt", NOW);
            }
            return saved;
        });
    }

    @Test
    void aStudentRegistersForSeveralOfferingsAtOnce() {
        RegistrationResult result = service.register(ACCOUNT, List.of(102L, 101L));

        assertEquals(2, result.totalCourses());
        assertEquals(new BigDecimal("7.00"), result.totalCredits());
        assertEquals(List.of("CS301", "CS306"), result.registered().stream().map(RegisteredCourse::courseCode).toList());
        assertEquals("ENROLLED", result.registered().get(0).status());
        verify(semesterEnrollments).findByIdForUpdate(41L);
        verify(offerings).findByIdForUpdate(101L);
        verify(offerings).findByIdForUpdate(102L);
    }

    @Test
    void choosingAnOfferingTwiceIsRefused() {
        assertRefused(HttpStatus.BAD_REQUEST, StudentRegistrationService.REPEATED,
                () -> service.register(ACCOUNT, List.of(101L, 101L)));
    }

    @Test
    void anInactiveProfileCannotRegister() {
        student.deactivate();

        assertRefused(HttpStatus.CONFLICT, StudentRegistrationService.PROFILE_INACTIVE,
                () -> service.register(ACCOUNT, List.of(101L)));
    }

    @Test
    void aMissingOfferingChangesNothing() {
        assertRefused(HttpStatus.NOT_FOUND, StudentRegistrationService.OFFERING_MISSING,
                () -> service.register(ACCOUNT, List.of(101L, 999L)));
    }

    @Test
    void registrationOutsideThePeriodIsRefused() {
        third.changeRegistrationPeriod(NOW.plus(1, ChronoUnit.DAYS), NOW.plus(2, ChronoUnit.DAYS), null);

        assertRefused(HttpStatus.CONFLICT, "Registration for semester 3 is not open now.",
                () -> service.register(ACCOUNT, List.of(101L)));
    }

    @Test
    void anOfferingNotOpenForRegistrationIsRefused() {
        ds.changeRegistrationStatus(OfferingRegistrationStatus.CLOSED);

        assertRefused(HttpStatus.CONFLICT, "Registration for CS301 is not open.",
                () -> service.register(ACCOUNT, List.of(101L)));
    }

    @Test
    void anotherSectionsOfferingIsRefused() {
        term.placeIn(sectionB);

        assertRefused(HttpStatus.CONFLICT, "CS306 is for section A, and you are in section B.",
                () -> service.register(ACCOUNT, List.of(102L)));
    }

    @Test
    void aFullOfferingIsRefusedWithItsCapacity() {
        ds.changeCapacity(40);
        when(courseEnrollments.countEnrolledInOfferingForShare(101L)).thenReturn(40L);

        assertRefused(HttpStatus.CONFLICT, "CS301 is full: all 40 places are taken.",
                () -> service.register(ACCOUNT, List.of(102L, 101L)));
    }

    @Test
    void anOfferingTakenOrDroppedBeforeCannotBeTakenAgain() {
        CourseEnrollment existing = new CourseEnrollment(term, ds);
        when(courseEnrollments.findBySemesterEnrollmentIdAndCourseOfferingId(41L, 101L)).thenReturn(Optional.of(existing));
        assertRefused(HttpStatus.CONFLICT, "You are already registered for CS301.",
                () -> service.register(ACCOUNT, List.of(101L)));

        existing.drop(NOW);
        assertRefused(HttpStatus.CONFLICT, "You dropped or ended CS301 earlier, so it cannot be taken again.",
                () -> service.register(ACCOUNT, List.of(101L)));
    }

    @Test
    void theProgramsCreditLimitIsKept() {
        btech.changeMaxCreditsPerSemester(new BigDecimal("10"));
        CourseOffering other = offering(103, "CS302", "4.00", null);
        when(courseEnrollments.findAllWithOfferingBySemesterEnrollmentIdIn(List.of(41L)))
                .thenReturn(List.of(new CourseEnrollment(term, other)));

        assertRefused(HttpStatus.CONFLICT, "This would bring you to 11 credits in semester 3, over the limit of 10.",
                () -> service.register(ACCOUNT, List.of(101L, 102L)));
    }

    @Test
    void aSemesterTheStudentIsNotEnrolledInIsRefused() {
        term.markCompleted();

        assertRefused(HttpStatus.CONFLICT, "CS301 is not offered to a semester you are enrolled in.",
                () -> service.register(ACCOUNT, List.of(101L)));
    }

    @Test
    void aStudentDropsTheirOwnCourseBeforeTheDeadline() {
        CourseEnrollment enrollment = withId(new CourseEnrollment(term, ds), 601);
        when(courseEnrollments.findByIdForUpdate(601L)).thenReturn(Optional.of(enrollment));

        RegisteredCourse dropped = service.drop(ACCOUNT, 601L);

        assertEquals("DROPPED", dropped.status());
        assertEquals(NOW, dropped.droppedAt());
        assertEquals(CourseEnrollmentStatus.DROPPED, enrollment.getStatus());
    }

    @Test
    void droppingIsRefusedWhenNotAllowed() {
        CourseEnrollment enrollment = withId(new CourseEnrollment(term, ds), 601);
        when(courseEnrollments.findByIdForUpdate(601L)).thenReturn(Optional.of(enrollment));

        assertRefused(HttpStatus.NOT_FOUND, StudentRegistrationService.ENROLLMENT_MISSING,
                () -> service.drop(99L, 601L));

        third.changeRegistrationPeriod(NOW.minus(9, ChronoUnit.DAYS), NOW.minus(5, ChronoUnit.DAYS),
                NOW.minus(1, ChronoUnit.DAYS));
        assertRefused(HttpStatus.CONFLICT, StudentRegistrationService.DROP_PASSED, () -> service.drop(ACCOUNT, 601L));

        third.changeRegistrationPeriod(NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS), null);
        assertRefused(HttpStatus.CONFLICT, StudentRegistrationService.NO_DROPPING, () -> service.drop(ACCOUNT, 601L));

        third.changeRegistrationPeriod(NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS),
                NOW.plus(1, ChronoUnit.DAYS));
        enrollment.withdraw();
        assertRefused(HttpStatus.CONFLICT, StudentRegistrationService.NOT_ENROLLED, () -> service.drop(ACCOUNT, 601L));
    }

    @Test
    void theOverviewListsOnlyOpenOfferingsOfTheStudentsSectionWithReasons() {
        CourseOffering draft = offering(103, "CS302", "4.00", null);
        draft.changeRegistrationStatus(OfferingRegistrationStatus.DRAFT);
        CourseOffering otherSection = offering(104, "CS307", "3.00", sectionB);
        ds.changeCapacity(1);
        when(offerings.findAllWithContext(eq(5L), isNull(), isNull(), isNull(), any()))
                .thenReturn(List.of(ds, web, draft, otherSection));
        when(courseEnrollments.countByOfferingAndStatus(anyCollection()))
                .thenReturn(List.of(count(101L, CourseEnrollmentStatus.ENROLLED, 1L)));
        CourseEnrollment mine = withId(new CourseEnrollment(term, web), 602);
        ReflectionTestUtils.setField(mine, "createdAt", NOW);
        when(courseEnrollments.findAllWithOfferingBySemesterEnrollmentIdIn(List.of(41L))).thenReturn(List.of(mine));

        RegistrationTerm shown = service.overview(ACCOUNT).terms().get(0);

        assertTrue(shown.registrationOpen());
        assertTrue(shown.dropOpen());
        assertEquals("A", shown.sectionCode());
        assertEquals(new BigDecimal("3.00"), shown.registeredCredits());
        assertEquals(List.of("CS301", "CS306"), shown.offerings().stream().map(EligibleOffering::courseCode).toList());
        EligibleOffering full = shown.offerings().get(0);
        assertFalse(full.canRegister());
        assertEquals(0, full.availableSeats());
        assertEquals(StudentRegistrationService.REASON_FULL, full.reason());
        assertEquals(StudentRegistrationService.REASON_REGISTERED, shown.offerings().get(1).reason());
        assertNull(shown.offerings().get(1).capacity());
        assertTrue(shown.registered().get(0).canDrop());
        verify(courseEnrollments, never()).saveAndFlush(any());
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    private CourseOffering offering(long id, String code, String credits, Section section) {
        Course course = withId(new Course(cse, code, "Course " + code, new BigDecimal(credits)), id + 1000);
        CourseOffering offering = withId(section == null ? new CourseOffering(course, third)
                : new CourseOffering(course, third, section), id);
        offering.markActive();
        offering.changeRegistrationStatus(OfferingRegistrationStatus.OPEN);
        return offering;
    }

    private StudentProfile student(long accountId, long profileId) {
        UserAccount account = withId(new UserAccount("student1", "example-hash-value"), accountId);
        return withId(new StudentProfile(account, btech, session), profileId);
    }

    private static OfferingStatusCount count(Long offeringId, CourseEnrollmentStatus status, Long total) {
        return new OfferingStatusCount() {
            public Long getCourseOfferingId() {
                return offeringId;
            }

            public CourseEnrollmentStatus getStatus() {
                return status;
            }

            public Long getTotal() {
                return total;
            }
        };
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
