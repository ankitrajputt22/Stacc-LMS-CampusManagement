package com.stacc.backend.lms.announcement;

import java.util.Collection;
import java.util.List;

import com.stacc.backend.lms.content.LmsContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsAnnouncementRepository extends JpaRepository<LmsAnnouncement, Long> {

    /** For faculty: the course's announcements in these statuses, newest created first. */
    List<LmsAnnouncement> findAllByLmsCourseIdAndStatusInOrderByCreatedAtDescIdDesc(
            Long lmsCourseId, Collection<LmsContentStatus> statuses);

    /** For students: the course's announcements in one status, newest published first. */
    List<LmsAnnouncement> findAllByLmsCourseIdAndStatusOrderByPublishedAtDescIdDesc(
            Long lmsCourseId, LmsContentStatus status);
}
