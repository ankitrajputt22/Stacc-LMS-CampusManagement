package com.stacc.backend.lms.announcement;

import java.time.Clock;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
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

    public LmsAnnouncementManagementService(
            LmsCourseRepository lmsCourses,
            LmsAnnouncementRepository announcements,
            LmsFacultyAccessService facultyAccess,
            Clock clock) {
        this.lmsCourses = lmsCourses;
        this.announcements = announcements;
        this.facultyAccess = facultyAccess;
        this.clock = clock;
    }

    /** Adds a draft announcement to the course. */
    public LmsAnnouncement create(Long userAccountId, Long lmsCourseId, String title, String message) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw notFound(COURSE_UNAVAILABLE);
        }
        LmsCourse lmsCourse = lmsCourses.findById(lmsCourseId).orElseThrow(() -> notFound(COURSE_UNAVAILABLE));
        return announcements.save(new LmsAnnouncement(lmsCourse, title, message));
    }

    /** Changes the title and message of a draft or published announcement. Publication time is kept. */
    public LmsAnnouncement update(Long userAccountId, Long announcementId, String title, String message) {
        LmsAnnouncement announcement = changeable(userAccountId, announcementId);
        announcement.edit(title, message);
        return announcement;
    }

    /** Publishes a draft now. Publishing a published announcement changes nothing. */
    public LmsAnnouncement publish(Long userAccountId, Long announcementId) {
        LmsAnnouncement announcement = changeable(userAccountId, announcementId);
        announcement.publish(clock.instant());
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
