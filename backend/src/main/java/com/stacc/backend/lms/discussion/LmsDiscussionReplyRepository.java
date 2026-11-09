package com.stacc.backend.lms.discussion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsDiscussionReplyRepository extends JpaRepository<LmsDiscussionReply, Long> {

    @Query("select r from LmsDiscussionReply r join fetch r.author where r.thread.id = :threadId order by r.createdAt asc, r.id asc")
    List<LmsDiscussionReply> findAllForThread(@Param("threadId") Long threadId);
}
