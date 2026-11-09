package com.stacc.backend.academic.calendar;

import static com.stacc.backend.academic.calendar.CalendarFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AcademicCalendarNotificationsTest {

    private final CalendarFixtures college = new CalendarFixtures();
    private final NotificationService notificationService = mock(NotificationService.class);
    private final SemesterEnrollmentRepository semesterEnrollments = mock(SemesterEnrollmentRepository.class);
    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final AcademicCalendarNotifications notifications =
            new AcademicCalendarNotifications(notificationService, semesterEnrollments, teachingAssignments, accounts);

    private TeachingAssignment teaching(long accountId, Semester semester, boolean active) {
        UserAccount account = withId(new UserAccount("FAC-" + accountId, "hash"), accountId);
        FacultyProfile faculty = withId(new FacultyProfile(account, college.cse), accountId + 1000);
        Course course = withId(new Course(college.cse, "CS" + accountId, "Course " + accountId, new BigDecimal("4.00")), accountId);
        TeachingAssignment assignment = new TeachingAssignment(faculty, withId(new CourseOffering(course, semester), accountId));
        if (!active) {
            assignment.deactivate();
        }
        return assignment;
    }

    private AcademicCalendarEvent published(Semester semester, String title, LocalDate start, LocalDate end) {
        AcademicCalendarEvent event = college.event(14, college.session, semester, title, start, end);
        event.publish(Instant.parse("2026-09-01T10:00:00Z"));
        return event;
    }

    @BeforeEach
    void setUp() {
        when(accounts.findIdsByRoleName(RoleName.ADMIN)).thenReturn(List.of(1L, 90L));
    }

    @Test
    void aSemesterEventReachesItsStudentsItsCurrentTeachersAndAdminsEachOnce() {
        // Account 90 is a student of semester 3 who is also an admin.
        when(semesterEnrollments.findStudentAccountIdsInSession(2L, 9L, AcademicCalendarEventQueryService.CALENDAR_PARTICIPATION))
                .thenReturn(List.of(70L, 90L));
        when(teachingAssignments.findAllWithFacultyInSession(2L, 9L))
                .thenReturn(List.of(teaching(80, college.third, true), teaching(81, college.third, false)));

        assertThat(notifications.recipientsOf(published(college.third, "Exams", LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 25)))).containsExactly(70L, 90L, 80L, 1L);
    }

    @Test
    void aSessionWideEventLooksAcrossTheWholeSession() {
        when(semesterEnrollments.findStudentAccountIdsInSession(eq(2L), eq(null), anyCollection())).thenReturn(List.of(70L));
        when(teachingAssignments.findAllWithFacultyInSession(2L, null)).thenReturn(List.of(teaching(82, college.fourth, true)));

        assertThat(notifications.recipientsOf(published(null, "Holiday", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 19))))
                .containsExactly(70L, 82L, 1L, 90L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicationAndCancellationAreWordedWithTheDatesAndLinkToTheCalendar() {
        when(semesterEnrollments.findStudentAccountIdsInSession(any(), any(), anyCollection())).thenReturn(List.of(70L));
        when(teachingAssignments.findAllWithFacultyInSession(any(), any())).thenReturn(List.of());
        AcademicCalendarEvent week = published(college.third, "Mid-semester examinations", LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 25));
        AcademicCalendarEvent day = published(null, "Diwali holiday", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 19));

        notifications.published(week);
        notifications.cancelled(day);

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        ArgumentCaptor<Collection<Long>> recipients = ArgumentCaptor.forClass(Collection.class);
        verify(notificationService, times(2)).notifyUsers(message.capture(), recipients.capture());
        assertThat(message.getAllValues().get(0)).isEqualTo(new NotificationMessage(NotificationType.ACADEMIC_CALENDAR_PUBLISHED,
                "Academic calendar updated", "Mid-semester examinations (20 Oct 2026 to 25 Oct 2026) was added to the academic calendar.",
                NotificationSourceType.ACADEMIC_CALENDAR_EVENT, 14L, "/calendar"));
        assertThat(message.getAllValues().get(1).type()).isEqualTo(NotificationType.ACADEMIC_CALENDAR_CANCELLED);
        assertThat(message.getAllValues().get(1).message()).isEqualTo("Diwali holiday (19 Oct 2026) was cancelled.");
        assertThat(recipients.getAllValues()).allSatisfy(found -> assertThat(found).containsExactly(70L, 1L, 90L));
    }
}
