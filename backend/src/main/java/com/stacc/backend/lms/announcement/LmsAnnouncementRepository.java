package com.stacc.backend.lms.announcement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.lms.content.LmsContentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsAnnouncementRepository extends JpaRepository<LmsAnnouncement, Long> {

    @Query("select announcement.lmsCourse.id from LmsAnnouncement announcement where announcement.id = :announcementId")
    Optional<Long> findLmsCourseIdById(@Param("announcementId") Long announcementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select announcement from LmsAnnouncement announcement join fetch announcement.lmsCourse where announcement.id = :announcementId")
    Optional<LmsAnnouncement> findByIdForUpdate(@Param("announcementId") Long announcementId);

    List<LmsAnnouncement> findAllByLmsCourseIdAndStatusInOrderByCreatedAtDescIdDesc(
            Long lmsCourseId, Collection<LmsContentStatus> statuses);

    List<LmsAnnouncement> findByLmsCourseIdInAndStatus(Collection<Long> lmsCourseIds, LmsContentStatus status, Pageable page);

    List<LmsAnnouncement> findAllByLmsCourseIdAndStatusOrderByPublishedAtDescIdDesc(
            Long lmsCourseId, LmsContentStatus status);

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
