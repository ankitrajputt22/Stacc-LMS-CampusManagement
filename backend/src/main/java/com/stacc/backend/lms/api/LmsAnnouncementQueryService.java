package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.announcement.LmsAnnouncementRepository;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsContentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only answers about the announcements of an LMS course, for each side. */
@Service
@Transactional(readOnly = true)
public class LmsAnnouncementQueryService {

    static final String COURSE_UNAVAILABLE_MESSAGE = "Course not found or unavailable.";

    private final LmsStudentAccessService studentAccess;
    private final LmsFacultyAccessService facultyAccess;
    private final LmsAnnouncementRepository announcements;

    public LmsAnnouncementQueryService(
            LmsStudentAccessService studentAccess,
            LmsFacultyAccessService facultyAccess,
            LmsAnnouncementRepository announcements) {
        this.studentAccess = studentAccess;
        this.facultyAccess = facultyAccess;
        this.announcements = announcements;
    }

    /**
     * The published announcements of a course the student may use, newest published first. Drafts and
     * archived announcements never appear. A course the student may not use answers the same 404 as a
     * course that does not exist.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public List<LmsCourseAnnouncementResponse> getPublishedAnnouncements(Long userAccountId, Long lmsCourseId) {
        if (!studentAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE_MESSAGE);
        }
        return announcements.findAllByLmsCourseIdAndStatusOrderByPublishedAtDescIdDesc(lmsCourseId, LmsContentStatus.PUBLISHED)
                .stream()
                .filter(announcement -> announcement.getStatus() == LmsContentStatus.PUBLISHED)
                .map(LmsCourseAnnouncementResponse::of)
                .toList();
    }

    /**
     * The announcements of a course the faculty member teaches, drafts included and archived ones left out,
     * newest created first. A course they may not use answers the same 404 as a course that does not exist.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public List<LmsFacultyAnnouncementResponse> getManagedAnnouncements(Long userAccountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE_MESSAGE);
        }
        return announcements.findAllByLmsCourseIdAndStatusInOrderByCreatedAtDescIdDesc(lmsCourseId, LmsContentPolicy.IN_USE)
                .stream()
                .filter(announcement -> LmsContentPolicy.isInUse(announcement.getStatus()))
                .map(LmsFacultyAnnouncementResponse::of)
                .toList();
    }
}
