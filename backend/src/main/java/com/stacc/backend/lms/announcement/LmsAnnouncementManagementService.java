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

/**
 * Every change faculty make to the announcements of an LMS course. Before any change, the course is
 * found from stored data, never from the request, and {@link LmsFacultyAccessService} must allow the
 * signed-in account to use it. An announcement that does not exist and one in a course the caller does
 * not teach get the same 404. Nothing is deleted: announcements are archived.
 */
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

    /** Adds a draft plain-text announcement to the course. */
    public LmsAnnouncement create(Long userAccountId, Long lmsCourseId, String title, String message) {
        return create(userAccountId, lmsCourseId, title, message, null);
    }

    /** Adds a draft announcement to the course, with its message's format (plain when none is sent). */
    public LmsAnnouncement create(Long userAccountId, Long lmsCourseId, String title, String message, LmsTextFormat format) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw notFound(COURSE_UNAVAILABLE);
        }
        LmsCourse lmsCourse = lmsCourses.findById(lmsCourseId).orElseThrow(() -> notFound(COURSE_UNAVAILABLE));
        LmsAnnouncement announcement = new LmsAnnouncement(lmsCourse, title, message);
        announcement.changeMessageFormat(LmsTextFormat.orKeep(format, LmsTextFormat.PLAIN));
        return announcements.save(announcement);
    }

    /** Changes the title and message of a draft or published announcement. Publication time is kept. */
    public LmsAnnouncement update(Long userAccountId, Long announcementId, String title, String message) {
        return update(userAccountId, announcementId, title, message, null);
    }

    /** As above; without a format the message keeps its current one. */
    public LmsAnnouncement update(Long userAccountId, Long announcementId, String title, String message,
            LmsTextFormat format) {
        LmsAnnouncement announcement = changeable(userAccountId, announcementId);
        announcement.edit(title, message);
        announcement.changeMessageFormat(LmsTextFormat.orKeep(format, announcement.getMessageFormat()));
        return announcement;
    }

    /**
     * Publishes a draft now and, the first time only, tells the students who may use the course through the Inbox.
     * Publishing a published announcement changes nothing. The row is locked first and read fresh, so two publications
     * at once notify once.
     */
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

    /** Archives the announcement and keeps it. Archiving again changes nothing. */
    public LmsAnnouncement archive(Long userAccountId, Long announcementId) {
        LmsAnnouncement announcement = manageable(userAccountId, announcementId);
        announcement.archive();
        return announcement;
    }

    // The course is taken from the stored announcement, never from the request.
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
