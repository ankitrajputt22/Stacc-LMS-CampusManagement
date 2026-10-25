package com.stacc.backend.support.helpdesk;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HelpdeskTicketMessageRepository extends JpaRepository<HelpdeskTicketMessage, Long> {

    /** A ticket's conversation, oldest first, with each author. */
    @Query("""
            select message
            from HelpdeskTicketMessage message
            join fetch message.author
            where message.ticket.id = :ticketId
            order by message.createdAt asc, message.id asc
            """)
    List<HelpdeskTicketMessage> findAllWithAuthorByTicketId(@Param("ticketId") Long ticketId);
}
