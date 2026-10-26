package com.stacc.backend.campus.notices;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Campus notices. */
public interface CampusNoticeRepository extends JpaRepository<CampusNotice, Long> {

    boolean existsByTitle(String title);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notice from CampusNotice notice where notice.id = :noticeId")
    Optional<CampusNotice> findByIdForUpdate(@Param("noticeId") Long noticeId);

    /**
     * One page of the published notices visible to any of the account's roles, newest first. Each notice appears once,
     * however many of its flags match.
     */
    @Query(value = """
            select notice
            from CampusNotice notice
            where notice.status = com.stacc.backend.campus.notices.CampusNoticeStatus.PUBLISHED
              and ((:student = true and notice.visibleToStudent = true)
                   or (:faculty = true and notice.visibleToFaculty = true)
                   or (:admin = true and notice.visibleToAdmin = true))
            order by notice.publishedAt desc, notice.id desc
            """,
            countQuery = """
            select count(notice)
            from CampusNotice notice
            where notice.status = com.stacc.backend.campus.notices.CampusNoticeStatus.PUBLISHED
              and ((:student = true and notice.visibleToStudent = true)
                   or (:faculty = true and notice.visibleToFaculty = true)
                   or (:admin = true and notice.visibleToAdmin = true))
            """)
    Page<CampusNotice> findFeed(@Param("student") boolean student, @Param("faculty") boolean faculty,
            @Param("admin") boolean admin, Pageable page);

    /** One page of every notice, or those in one status, the most recently created first. */
    @Query(value = """
            select notice
            from CampusNotice notice
            where (:status is null or notice.status = :status)
            order by notice.createdAt desc, notice.id desc
            """,
            countQuery = "select count(notice) from CampusNotice notice where (:status is null or notice.status = :status)")
    Page<CampusNotice> findAdminPage(@Param("status") CampusNoticeStatus status, Pageable page);
}
