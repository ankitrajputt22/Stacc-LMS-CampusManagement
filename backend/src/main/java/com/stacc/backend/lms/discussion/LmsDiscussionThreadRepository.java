package com.stacc.backend.lms.discussion;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsDiscussionThreadRepository extends JpaRepository<LmsDiscussionThread, Long> {

    /** Pinned threads first, then the most recently active. */
    @Query("select t from LmsDiscussionThread t join fetch t.author where t.lmsCourse.id = :lmsCourseId "
            + "order by t.pinned desc, t.lastActivityAt desc, t.id desc")
    List<LmsDiscussionThread> findAllForCourse(@Param("lmsCourseId") Long lmsCourseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LmsDiscussionThread t where t.id = :id")
    Optional<LmsDiscussionThread> findByIdForUpdate(@Param("id") Long id);

    interface ReplyCount {
        Long getThreadId();
        long getReplies();
    }

    @Query("select r.thread.id as threadId, count(r) as replies from LmsDiscussionReply r "
            + "where r.thread.lmsCourse.id = :lmsCourseId group by r.thread.id")
    List<ReplyCount> countRepliesForCourse(@Param("lmsCourseId") Long lmsCourseId);
}
