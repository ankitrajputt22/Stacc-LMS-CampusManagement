package com.stacc.backend.lms.announcement;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsAnnouncementAttachmentRepository extends JpaRepository<LmsAnnouncementAttachment, Long> {

    /** The files still listed on these announcements, oldest first. */
    @Query("""
            select attachment from LmsAnnouncementAttachment attachment
            where attachment.announcement.id in :announcementIds and attachment.removedAt is null
            order by attachment.createdAt, attachment.id
            """)
    List<LmsAnnouncementAttachment> findListedFor(@Param("announcementIds") Collection<Long> announcementIds);

    /** How many files are still listed on the announcement. Called while its row is locked. */
    @Query("""
            select count(attachment) from LmsAnnouncementAttachment attachment
            where attachment.announcement.id = :announcementId and attachment.removedAt is null
            """)
    long countListed(@Param("announcementId") Long announcementId);
}
