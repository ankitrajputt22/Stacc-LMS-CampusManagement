package com.stacc.backend.lms.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmission;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGrade;
import com.stacc.backend.lms.assignment.submission.SubmissionFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class LmsAssignmentNotificationsTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final LmsStudentAccessService studentAccess = mock(LmsStudentAccessService.class);
    private final LmsAssignmentNotifications notifications = new LmsAssignmentNotifications(notificationService, studentAccess);

    private final LmsCourse course = SubmissionFixtures.course(7);
    private final Course catalogCourse = course.getCourseOffering().getCourse();
    private final LmsAssignment assignment = SubmissionFixtures.assignment(course, 44, LmsContentStatus.PUBLISHED);

    @Test
    @SuppressWarnings("unchecked")
    void aPublishedAssignmentReachesTheStudentsTheAccessRuleAllows() {
        when(studentAccess.findStudentAccountIdsWithAccess(7L)).thenReturn(List.of(12L, 14L));

        notifications.published(assignment);

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        ArgumentCaptor<List<Long>> recipients = ArgumentCaptor.forClass(List.class);
        verify(notificationService).notifyUsers(message.capture(), recipients.capture());
        assertThat(recipients.getValue()).containsExactly(12L, 14L);
        assertThat(message.getValue()).isEqualTo(new NotificationMessage(NotificationType.ASSIGNMENT_PUBLISHED,
                "New assignment published",
                "Assignment 44 was published in " + catalogCourse.getCode() + " " + catalogCourse.getName() + ".",
                NotificationSourceType.ASSIGNMENT, 44L, "/courses/7/assignments/44"));
    }

    @Test
    void aGradeReachesOnlyTheStudentWhoHandedInTheAttempt() {
        LmsAssignmentSubmission submission = new LmsAssignmentSubmission(assignment,
                SubmissionFixtures.membership(course, 12, 40).getCourseEnrollment());
        LmsSubmissionAttempt attempt = new LmsSubmissionAttempt(submission, 2, "Work", Instant.parse("2026-10-20T17:00:00Z"),
                SubmissionFixtures.DUE);
        LmsSubmissionGrade grade = new LmsSubmissionGrade(attempt, SubmissionFixtures.faculty(course, 21, 61),
                new BigDecimal("18"), new BigDecimal("20"), null, Instant.parse("2026-10-21T08:00:00Z"));
        ReflectionTestUtils.setField(grade, "id", 900L);

        notifications.graded(grade, true);
        notifications.graded(grade, false);

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService, times(2)).notifyUser(message.capture(), eq(12L));
        String what = "attempt 2 for Assignment 44 in " + catalogCourse.getCode();
        assertThat(message.getAllValues()).extracting(NotificationMessage::title)
                .containsExactly("Assignment graded", "Assignment grade updated");
        assertThat(message.getAllValues()).extracting(NotificationMessage::message)
                .containsExactly("Your " + what + " was graded.", "The grade of your " + what + " was updated.");
        assertThat(message.getValue().type()).isEqualTo(NotificationType.ASSIGNMENT_GRADED);
        assertThat(message.getValue().sourceType()).isEqualTo(NotificationSourceType.SUBMISSION_GRADE);
        assertThat(message.getValue().sourceId()).isEqualTo(900L);
        assertThat(message.getValue().actionPath()).isEqualTo("/courses/7/assignments/44");
    }
}
