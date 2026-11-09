package com.stacc.backend.lms.message;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsMessageAttachmentRepository extends JpaRepository<LmsMessageAttachment, Long> {

    @Query("select a from LmsMessageAttachment a where a.message.conversation.id = :conversationId order by a.id")
    List<LmsMessageAttachment> findAllForConversation(@Param("conversationId") Long conversationId);
}
