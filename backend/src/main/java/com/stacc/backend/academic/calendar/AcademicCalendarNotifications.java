package com.stacc.backend.academic.calendar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.stereotype.Component;

/**
 * The Inbox notifications about official calendar events: when one is published, and when a published one is
 * cancelled. They reach exactly the accounts that would see the event on their calendar, by the same rule as
 * {@link AcademicCalendarEventQueryService} turned around: students with an enrolled or completed semester enrollment
 * in its semester (or, for a session-wide event, in any semester of its session), faculty teaching there now, and every
 * admin. An account found through several roles is told once.
 */
@Component
public class AcademicCalendarNotifications {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final NotificationService notifications;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final TeachingAssignmentRepository teachingAssignments;
    private final UserAccountRepository accounts;

    public AcademicCalendarNotifications(NotificationService notifications,
            SemesterEnrollmentRepository semesterEnrollments, TeachingAssignmentRepository teachingAssignments,
            UserAccountRepository accounts) {
        this.notifications = notifications;
        this.semesterEnrollments = semesterEnrollments;
        this.teachingAssignments = teachingAssignments;
        this.accounts = accounts;
    }

    /** Tells everyone who will see the newly published event. */
    public void published(AcademicCalendarEvent event) {
        notify(event, NotificationType.ACADEMIC_CALENDAR_PUBLISHED, "Academic calendar updated",
                event.getTitle() + " (" + datesOf(event) + ") was added to the academic calendar.");
    }

    /** Tells everyone who saw the event that it was cancelled. Only events that were published are ever passed here. */
    public void cancelled(AcademicCalendarEvent event) {
        notify(event, NotificationType.ACADEMIC_CALENDAR_CANCELLED, "Academic calendar event cancelled",
                event.getTitle() + " (" + datesOf(event) + ") was cancelled.");
    }

    /** The accounts that would see the event on their calendar, each once. */
    Set<Long> recipientsOf(AcademicCalendarEvent event) {
        Long sessionId = event.getAcademicSession().getId();
        Semester semester = event.getSemester();
        Long semesterId = semester == null ? null : semester.getId();
        Set<Long> recipients = new LinkedHashSet<>(semesterEnrollments.findStudentAccountIdsInSession(
                sessionId, semesterId, AcademicCalendarEventQueryService.CALENDAR_PARTICIPATION));
        for (TeachingAssignment assignment : teachingAssignments.findAllWithFacultyInSession(sessionId, semesterId)) {
            Long accountId = assignment.getFacultyProfile().getUserAccount().getId();
            if (FacultyTeachingAccessService.isCurrentTeaching(assignment, accountId)) {
                recipients.add(accountId);
            }
        }
        recipients.addAll(accounts.findIdsByRoleName(RoleName.ADMIN));
        return recipients;
    }

    private void notify(AcademicCalendarEvent event, NotificationType type, String title, String message) {
        notifications.notifyUsers(new NotificationMessage(type, title, message,
                NotificationSourceType.ACADEMIC_CALENDAR_EVENT, event.getId(), "/calendar"), recipientsOf(event));
    }

    private static String datesOf(AcademicCalendarEvent event) {
        LocalDate start = event.getStartDate();
        LocalDate end = event.getEndDate();
        return start.equals(end) ? DAY.format(start) : DAY.format(start) + " to " + DAY.format(end);
    }
}
