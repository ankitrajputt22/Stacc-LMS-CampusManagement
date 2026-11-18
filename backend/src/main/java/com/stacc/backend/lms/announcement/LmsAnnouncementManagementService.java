package com.stacc.backend.lms.announcement;

import java.time.Clock;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.content.LmsTextFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LmsAnnouncementManagementService {

    static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    static final String ANNOUNCEMENT_UNAVAILABLE = "Announcement not found or unavailable.";
    static final String ANNOUNCEMENT_ARCHIVED = "This announcement is archived and can no longer be changed.";

    private final LmsCourseRepository lmsCourses;
    private final LmsAnnouncementRepository announcements;
    private final LmsFacultyAccessService facultyAccess;
    private final Clock clock;
    private final LmsAnnouncementNotifications notifications;

    public LmsAnnouncementManagementService(
            LmsCourseRepository lmsCourses,
            LmsAnnouncementRepository announcements,
            LmsFacultyAccessService facultyAccess,
            Clock clock,
            LmsAnnouncementNotifications notifications) {
        this.lmsCourses = lmsCourses;
        this.announcements = announcements;
        this.facultyAccess = facultyAccess;
        this.clock = clock;
        this.notifications = notifications;
    }

    public LmsAnnouncement create(Long userAccountId, Long lmsCourseId, String title, String message) {
        return create(userAccountId, lmsCourseId, title, message, null);
    }

    public LmsAnnouncement create(Long userAccountId, Long lmsCourseId, String title, String message, LmsTextFormat format) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw notFound(COURSE_UNAVAILABLE);
        }
        LmsCourse lmsCourse = lmsCourses.findById(lmsCourseId).orElseThrow(() -> notFound(COURSE_UNAVAILABLE));
        LmsAnnouncement announcement = new LmsAnnouncement(lmsCourse, title, message);
        announcement.changeMessageFormat(LmsTextFormat.orKeep(format, LmsTextFormat.PLAIN));
        return announcements.save(announcement);
    }

    public LmsAnnouncement update(Long userAccountId, Long announcementId, String title, String message) {
        return update(userAccountId, announcementId, title, message, null);
    }

    public LmsAnnouncement update(Long userAccountId, Long announcementId, String title, String message,
            LmsTextFormat format) {
        LmsAnnouncement announcement = changeable(userAccountId, announcementId);
        announcement.edit(title, message);
        announcement.changeMessageFormat(LmsTextFormat.orKeep(format, announcement.getMessageFormat()));
        return announcement;
    }

    public LmsAnnouncement publish(Long userAccountId, Long announcementId) {
        Long lmsCourseId = announcementId == null ? null : announcements.findLmsCourseIdById(announcementId).orElse(null);
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw notFound(ANNOUNCEMENT_UNAVAILABLE);
        }
        LmsAnnouncement announcement = announcements.findByIdForUpdate(announcementId)
                .orElseThrow(() -> notFound(ANNOUNCEMENT_UNAVAILABLE));
        if (announcement.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, ANNOUNCEMENT_ARCHIVED);
        }
        boolean first = announcement.getPublishedAt() == null;
        announcement.publish(clock.instant());
        if (first) {
            notifications.published(announcement);
        }
        return announcement;
    }

    public LmsAnnouncement archive(Long userAccountId, Long announcementId) {
        LmsAnnouncement announcement = manageable(userAccountId, announcementId);
        announcement.archive();
        return announcement;
    }

    private LmsAnnouncement manageable(Long userAccountId, Long announcementId) {
        LmsAnnouncement announcement = announcementId == null ? null : announcements.findById(announcementId).orElse(null);
        if (announcement == null
                || !facultyAccess.canAccessCourse(userAccountId, announcement.getLmsCourse().getId())) {
            throw notFound(ANNOUNCEMENT_UNAVAILABLE);
        }
        return announcement;
    }

    private LmsAnnouncement changeable(Long userAccountId, Long announcementId) {
        LmsAnnouncement announcement = manageable(userAccountId, announcementId);
        if (announcement.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, ANNOUNCEMENT_ARCHIVED);
        }
        return announcement;
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }
}
