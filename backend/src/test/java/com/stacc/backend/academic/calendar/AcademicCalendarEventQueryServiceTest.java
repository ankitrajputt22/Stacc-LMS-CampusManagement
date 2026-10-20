package com.stacc.backend.academic.calendar;

import static com.stacc.backend.academic.calendar.CalendarFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository.EnrolledSemester;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.faculty.FacultyProfile;
import org.junit.jupiter.api.Test;

class AcademicCalendarEventQueryServiceTest {

    private static final long STUDENT_ACCOUNT = 70;
    private static final long FACULTY_ACCOUNT = 80;
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);
    private static final Instant PUBLISHED_AT = Instant.parse("2026-09-01T10:00:00Z");

    private final CalendarFixtures college = new CalendarFixtures();
    private final AcademicCalendarEventRepository events = mock(AcademicCalendarEventRepository.class);
    private final SemesterEnrollmentRepository semesterEnrollments = mock(SemesterEnrollmentRepository.class);
    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final AcademicCalendarEventQueryService service =
            new AcademicCalendarEventQueryService(events, semesterEnrollments, teachingAssignments);

    private static EnrolledSemester enrolled(Semester semester) {
        return new EnrolledSemester() {
            @Override
            public Long getSemesterId() {
                return semester.getId();
            }

            @Override
            public Long getAcademicSessionId() {
                return semester.getAcademicSession().getId();
            }
        };
    }

    private AcademicCalendarEvent published(long id, Semester semester, String title) {
        AcademicCalendarEvent event = college.event(id, semester == null ? college.session : semester.getAcademicSession(),
                semester, title, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 25));
        event.publish(PUBLISHED_AT);
        return event;
    }

    private TeachingAssignment teaching(long id, long accountId, Semester semester) {
        UserAccount account = withId(new UserAccount("FAC-" + accountId, "example-hash-value"), accountId);
        FacultyProfile faculty = withId(new FacultyProfile(account, college.cse), accountId + 1000);
        Course course = withId(new Course(college.cse, "CS" + id, "Course " + id, new BigDecimal("4.00")), id + 100);
        CourseOffering offering = withId(new CourseOffering(course, semester), id + 200);
        return withId(new TeachingAssignment(faculty, offering), id);
    }

    @Test
    void aStudentsScopeComesFromTheirOwnEnrolledAndCompletedSemesters() {
        when(semesterEnrollments.findEnrolledSemestersByUserAccountId(eq(STUDENT_ACCOUNT), anyCollection()))
                .thenReturn(List.of(enrolled(college.third), enrolled(college.nextYearFifth)));

        AcademicCalendarScope scope = service.studentScope(STUDENT_ACCOUNT);

        assertThat(scope.semesterIds()).containsExactlyInAnyOrder(9L, 12L);
        assertThat(scope.academicSessionIds()).containsExactlyInAnyOrder(2L, 5L);
        // Withdrawn enrollments are left out by the query itself.
        verify(semesterEnrollments).findEnrolledSemestersByUserAccountId(STUDENT_ACCOUNT,
                Set.of(SemesterEnrollmentStatus.ENROLLED, SemesterEnrollmentStatus.COMPLETED));
    }

    @Test
    void aFacultyScopeCountsOnlyCurrentTeachingOfTheAccountItself() {
        TeachingAssignment current = teaching(1, FACULTY_ACCOUNT, college.third);
        TeachingAssignment ended = teaching(2, FACULTY_ACCOUNT, college.fourth);
        ended.deactivate();
        TeachingAssignment inactiveProfile = teaching(3, FACULTY_ACCOUNT, college.nextYearFifth);
        inactiveProfile.getFacultyProfile().deactivate();
        TeachingAssignment someoneElse = teaching(4, 81, college.fourth);
        when(teachingAssignments.findAllWithCourseDetailsByUserAccountId(FACULTY_ACCOUNT))
                .thenReturn(List.of(current, ended, inactiveProfile, someoneElse));

        AcademicCalendarScope scope = service.facultyScope(FACULTY_ACCOUNT);

        assertThat(scope.semesterIds()).containsExactly(9L);
        assertThat(scope.academicSessionIds()).containsExactly(2L);
    }

    @Test
    void aStudentInSemesterThreeSeesItsEventsAndTheSessionWideOnesOnly() {
        AcademicCalendarEvent holiday = published(1, null, "Diwali holiday");
        AcademicCalendarEvent thirdExam = published(2, college.third, "Semester 3 exams");
        AcademicCalendarEvent fourthExam = published(3, college.fourth, "Semester 4 exams");
        AcademicCalendarEvent nextYear = published(4, college.nextYearFifth, "Next year");
        AcademicCalendarEvent nextYearWide = college.event(5, college.nextSession, null, "Next session holiday",
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2));
        nextYearWide.publish(PUBLISHED_AT);
        AcademicCalendarScope scope = new AcademicCalendarScope(Set.of(9L), Set.of(2L));
        // Even if the query returned more, only what applies to the scope is kept.
        when(events.findVisibleInRangeForScope(FROM, TO, scope.semesterIds(), scope.academicSessionIds()))
                .thenReturn(List.of(holiday, thirdExam, fourthExam, nextYear, nextYearWide));

        List<AcademicCalendarEventResponse> visible = service.findVisible(scope, FROM, TO);

        assertThat(visible).extracting(AcademicCalendarEventResponse::title)
                .containsExactly("Diwali holiday", "Semester 3 exams");
        assertThat(visible.get(0).semesterNumber()).isNull();
        assertThat(visible.get(0).programCode()).isNull();
        assertThat(visible.get(1).semesterNumber()).isEqualTo(3);
        assertThat(visible.get(1).programCode()).isEqualTo("BTECH-CSE");
        assertThat(visible.get(1).academicSessionCode()).isEqualTo("2026-27");
    }

    @Test
    void draftsAndDraftsCancelledBeforePublishingNeverShowButCancelledPublishedEventsDo() {
        AcademicCalendarEvent draft = college.event(1, college.session, null, "Draft", FROM, FROM);
        AcademicCalendarEvent cancelledDraft = college.event(2, college.session, null, "Cancelled draft", FROM, FROM);
        cancelledDraft.cancel(PUBLISHED_AT);
        AcademicCalendarEvent cancelledPublished = published(3, college.third, "Called off");
        cancelledPublished.cancel(Instant.parse("2026-09-10T10:00:00Z"));
        when(events.findVisibleInRangeForScope(any(), any(), anyCollection(), anyCollection()))
                .thenReturn(List.of(draft, cancelledDraft, cancelledPublished));

        List<AcademicCalendarEventResponse> visible =
                service.findVisible(new AcademicCalendarScope(Set.of(9L), Set.of(2L)), FROM, TO);

        assertThat(visible).singleElement().satisfies(event -> {
            assertThat(event.title()).isEqualTo("Called off");
            assertThat(event.status()).isEqualTo("CANCELLED");
        });
    }

    @Test
    void anEmptyScopeSendsNoQuery() {
        assertThat(service.findVisible(AcademicCalendarScope.NONE, FROM, TO)).isEmpty();
        assertThat(service.studentScope(null)).isEqualTo(AcademicCalendarScope.NONE);
        assertThat(service.facultyScope(null)).isEqualTo(AcademicCalendarScope.NONE);

        verifyNoInteractions(events, semesterEnrollments, teachingAssignments);
    }

    @Test
    void theAdminCalendarHasEveryVisibleEventWhateverItsScope() {
        AcademicCalendarEvent draft = college.event(1, college.session, null, "Draft", FROM, FROM);
        when(events.findVisibleInRange(FROM, TO)).thenReturn(List.of(
                published(2, null, "Diwali holiday"), published(3, college.fourth, "Semester 4 exams"), draft));

        assertThat(service.findAllVisible(FROM, TO)).extracting(AcademicCalendarEventResponse::title)
                .containsExactly("Diwali holiday", "Semester 4 exams");
        verify(events).findVisibleInRange(FROM, TO);
        verifyNoInteractions(semesterEnrollments, teachingAssignments);
    }

    @Test
    void scopesOfSeveralRolesAddUp() {
        AcademicCalendarScope student = new AcademicCalendarScope(Set.of(9L), Set.of(2L));
        AcademicCalendarScope faculty = new AcademicCalendarScope(Set.of(12L), Set.of(5L));

        AcademicCalendarScope both = student.and(faculty);

        assertThat(both.semesterIds()).containsExactlyInAnyOrder(9L, 12L);
        assertThat(both.academicSessionIds()).containsExactlyInAnyOrder(2L, 5L);
        assertThat(AcademicCalendarScope.NONE.and(student)).isEqualTo(student);
    }
}
