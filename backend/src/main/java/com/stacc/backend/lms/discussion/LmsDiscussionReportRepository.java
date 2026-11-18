package com.stacc.backend.lms.discussion;

import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsDiscussionReportRepository extends JpaRepository<LmsDiscussionReport, Long> {

    @Query("""
            select report from LmsDiscussionReport report join fetch report.thread thread join fetch thread.author
            left join fetch report.reply reply left join fetch reply.author
            where thread.lmsCourse.id = :lmsCourseId and report.resolvedAt is null order by report.createdAt, report.id
            """)
    List<LmsDiscussionReport> findOpenForCourse(@Param("lmsCourseId") Long lmsCourseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select report from LmsDiscussionReport report where report.thread.id = :threadId and report.resolvedAt is null
            and ((:replyId is null and report.reply is null) or report.reply.id = :replyId)
            """)
    List<LmsDiscussionReport> findOpenForPostForUpdate(@Param("threadId") Long threadId, @Param("replyId") Long replyId);

    @Query("""
            select count(report) > 0 from LmsDiscussionReport report where report.thread.id = :threadId
            and report.reporterAccountId = :reporterAccountId and report.resolvedAt is null
            and ((:replyId is null and report.reply is null) or report.reply.id = :replyId)
            """)
    boolean existsOpen(@Param("threadId") Long threadId, @Param("replyId") Long replyId,
            @Param("reporterAccountId") Long reporterAccountId);
}
