package com.stacc.backend.lms.assignment;

import java.util.List;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGrade;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.stereotype.Component;

@Component
public class LmsAssignmentNotifications {

    private final NotificationService notifications;
    private final LmsStudentAccessService studentAccess;

    public LmsAssignmentNotifications(NotificationService notifications, LmsStudentAccessService studentAccess) {
        this.notifications = notifications;
        this.studentAccess = studentAccess;
    }

    public void published(LmsAssignment assignment) {
        Course course = assignment.getLmsCourse().getCourseOffering().getCourse();
        List<Long> students = studentAccess.findStudentAccountIdsWithAccess(assignment.getLmsCourse().getId());
        notifications.notifyUsers(new NotificationMessage(NotificationType.ASSIGNMENT_PUBLISHED,
                "New assignment published",
                assignment.getTitle() + " was published in " + course.getCode() + " " + course.getName() + ".",
                NotificationSourceType.ASSIGNMENT, assignment.getId(), pathOf(assignment)), students);
    }

    public void graded(LmsSubmissionGrade grade, boolean firstGrade) {
        LmsSubmissionAttempt attempt = grade.getAttempt();
        LmsAssignment assignment = attempt.getSubmission().getAssignment();
        Course course = assignment.getLmsCourse().getCourseOffering().getCourse();
        Long student = attempt.getSubmission().getCourseEnrollment().getSemesterEnrollment().getStudentProfile()
                .getUserAccount().getId();
        String what = "attempt " + attempt.getAttemptNumber() + " for " + assignment.getTitle() + " in " + course.getCode();
        notifications.notifyUser(new NotificationMessage(NotificationType.ASSIGNMENT_GRADED,
                firstGrade ? "Assignment graded" : "Assignment grade updated",
                firstGrade ? "Your " + what + " was graded." : "The grade of your " + what + " was updated.",
                NotificationSourceType.SUBMISSION_GRADE, grade.getId(), pathOf(assignment)), student);
    }

    private static String pathOf(LmsAssignment assignment) {
        return "/courses/" + assignment.getLmsCourse().getId() + "/assignments/" + assignment.getId();
    }
}
