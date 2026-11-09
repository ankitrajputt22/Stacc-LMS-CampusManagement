package com.stacc.backend.lms.message;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsConversationMessageRepository extends JpaRepository<LmsConversationMessage, Long> {

    @Query("select m from LmsConversationMessage m join fetch m.author where m.conversation.id = :conversationId "
            + "order by m.createdAt asc, m.id asc")
    List<LmsConversationMessage> findAllForConversation(@Param("conversationId") Long conversationId);
}
