package com.stacc.backend.lms.quiz;

import java.util.List;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.stereotype.Component;

@Component
public class LmsQuizNotifications {

    private final NotificationService notifications;
    private final LmsStudentAccessService studentAccess;

    public LmsQuizNotifications(NotificationService notifications, LmsStudentAccessService studentAccess) {
        this.notifications = notifications;
        this.studentAccess = studentAccess;
    }

    public void published(LmsQuiz quiz) {
        Course course = quiz.getLmsCourse().getCourseOffering().getCourse();
        List<Long> students = studentAccess.findStudentAccountIdsWithAccess(quiz.getLmsCourse().getId());
        notifications.notifyUsers(new NotificationMessage(NotificationType.QUIZ_PUBLISHED, "New quiz published",
                quiz.getTitle() + " was published in " + course.getCode() + " " + course.getName() + ".",
                NotificationSourceType.QUIZ, quiz.getId(),
                "/courses/" + quiz.getLmsCourse().getId() + "/quizzes/" + quiz.getId()), students);
    }
}
