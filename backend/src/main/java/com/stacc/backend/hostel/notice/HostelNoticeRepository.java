package com.stacc.backend.hostel.notice;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostelNoticeRepository extends JpaRepository<HostelNotice, Long> {

    /** Every notice of a hostel, archived ones included, newest first. */
    @Query("select notice from HostelNotice notice join fetch notice.hostel where notice.hostel.id = :hostelId order by notice.id desc")
    List<HostelNotice> findAllForHostel(@Param("hostelId") Long hostelId);

    /** The published notices of a hostel, newest published first. */
    @Query("""
            select notice from HostelNotice notice join fetch notice.hostel
            where notice.hostel.id = :hostelId and notice.status = com.stacc.backend.hostel.notice.HostelNotice.Status.PUBLISHED
            order by notice.emergency desc, notice.publishedAt desc, notice.id desc
            """)
    List<HostelNotice> findPublishedForHostel(@Param("hostelId") Long hostelId);

    @Query("select notice.hostel.id from HostelNotice notice where notice.id = :noticeId")
    Optional<Long> findHostelIdById(@Param("noticeId") Long noticeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notice from HostelNotice notice join fetch notice.hostel where notice.id = :noticeId")
    Optional<HostelNotice> findByIdForUpdate(@Param("noticeId") Long noticeId);
}
