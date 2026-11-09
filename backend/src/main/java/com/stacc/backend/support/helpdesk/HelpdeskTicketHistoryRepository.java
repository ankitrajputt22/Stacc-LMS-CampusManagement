package com.stacc.backend.support.helpdesk;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HelpdeskTicketHistoryRepository extends JpaRepository<HelpdeskTicketHistory, Long> {

    /** A ticket's audit trail, oldest first, with each actor. */
    @Query("""
            select entry
            from HelpdeskTicketHistory entry
            join fetch entry.actor
            where entry.ticket.id = :ticketId
            order by entry.createdAt asc, entry.id asc
            """)
    List<HelpdeskTicketHistory> findAllWithActorByTicketId(@Param("ticketId") Long ticketId);
}
