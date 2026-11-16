package com.stacc.backend.lms.announcement;

import java.util.List;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.stereotype.Component;

/**
 * The Inbox notice about a course announcement, worded in one place. The caller decides that the announcement was
 * published for the first time; the students to tell come from the student access rule.
 */
@Component
public class LmsAnnouncementNotifications {

    private final NotificationService notifications;
    private final LmsStudentAccessService studentAccess;

    public LmsAnnouncementNotifications(NotificationService notifications, LmsStudentAccessService studentAccess) {
        this.notifications = notifications;
        this.studentAccess = studentAccess;
    }

    /** Tells every student who may use the course now. A course students cannot use yet has nobody to tell. */
    public void published(LmsAnnouncement announcement) {
        Course course = announcement.getLmsCourse().getCourseOffering().getCourse();
        List<Long> students = studentAccess.findStudentAccountIdsWithAccess(announcement.getLmsCourse().getId());
        notifications.notifyUsers(new NotificationMessage(NotificationType.ANNOUNCEMENT_PUBLISHED,
                "New announcement",
                announcement.getTitle() + " was posted in " + course.getCode() + " " + course.getName() + ".",
                NotificationSourceType.ANNOUNCEMENT, announcement.getId(),
                "/courses/" + announcement.getLmsCourse().getId() + "/announcements"), students);
    }
}
