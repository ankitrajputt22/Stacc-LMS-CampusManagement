package com.stacc.backend.lms.announcement;

import java.util.Collection;
import java.util.List;

import com.stacc.backend.lms.content.LmsContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsAnnouncementRepository extends JpaRepository<LmsAnnouncement, Long> {

    /** For faculty: the course's announcements in these statuses, newest created first. */
    List<LmsAnnouncement> findAllByLmsCourseIdAndStatusInOrderByCreatedAtDescIdDesc(
            Long lmsCourseId, Collection<LmsContentStatus> statuses);

    /** The announcements of several courses in one status, as many as the page asks for, in the page's order. */
    List<LmsAnnouncement> findByLmsCourseIdInAndStatus(Collection<Long> lmsCourseIds, LmsContentStatus status, Pageable page);

    /** For students: the course's announcements in one status, newest published first. */
    List<LmsAnnouncement> findAllByLmsCourseIdAndStatusOrderByPublishedAtDescIdDesc(
            Long lmsCourseId, LmsContentStatus status);

    /**
     * Search: announcements of these courses in these statuses whose title or message contains the text. Exact titles
     * first, then titles that start with the text, then the rest; within each, the newest first. The caller passes
     * only courses the account may use, never an empty collection.
     */
    @Query(value = """
            select announcement
            from LmsAnnouncement announcement
            where announcement.lmsCourse.id in :lmsCourseIds
              and announcement.status in :statuses
              and (lower(announcement.title) like :contains escape '!'
                   or lower(announcement.message) like :contains escape '!')
            order by case when lower(announcement.title) = :exact then 0 when lower(announcement.title) like :prefix escape '!' then 1 else 2 end, announcement.createdAt desc, announcement.id desc
            """,
            countQuery = """
            select count(announcement)
            from LmsAnnouncement announcement
            where announcement.lmsCourse.id in :lmsCourseIds
              and announcement.status in :statuses
              and (lower(announcement.title) like :contains escape '!'
                   or lower(announcement.message) like :contains escape '!')
            """)
    Page<LmsAnnouncement> search(@Param("lmsCourseIds") Collection<Long> lmsCourseIds,
            @Param("statuses") Collection<LmsContentStatus> statuses, @Param("exact") String exact,
            @Param("prefix") String prefix, @Param("contains") String contains, Pageable page);
}
