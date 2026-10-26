package com.stacc.backend.support.helpdesk;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tickets, always loaded with their category, requester, and assignee for display, so a page is one query plus a count.
 * Lists are ordered newest first, then by ID.
 */
public interface HelpdeskTicketRepository extends JpaRepository<HelpdeskTicket, Long> {

    @Query("""
            select ticket
            from HelpdeskTicket ticket
            join fetch ticket.category
            join fetch ticket.requester
            left join fetch ticket.assignedTo
            where ticket.id = :ticketId
            """)
    Optional<HelpdeskTicket> findWithContextById(@Param("ticketId") Long ticketId);

    /**
     * One ticket, locked until the transaction ends ({@code SELECT ... FOR UPDATE}). Every change takes this lock, so two
     * changes to the same ticket take turns. Only the ticket's own row is locked.
     */
    /** Whether the account already raised a ticket with exactly this subject. Used by the local test data. */
    boolean existsByRequesterIdAndSubject(Long requesterId, String subject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from HelpdeskTicket ticket where ticket.id = :ticketId")
    Optional<HelpdeskTicket> findByIdForUpdate(@Param("ticketId") Long ticketId);

    /** One page of the tickets one account raised, narrowed by each filter that is not null. */
    @Query(value = """
            select ticket
            from HelpdeskTicket ticket
            join fetch ticket.category category
            join fetch ticket.requester requester
            left join fetch ticket.assignedTo
            where requester.id = :requesterId
              and (:status is null or ticket.status = :status)
              and (:priority is null or ticket.priority = :priority)
              and (:categoryId is null or category.id = :categoryId)
            order by ticket.createdAt desc, ticket.id desc
            """,
            countQuery = """
            select count(ticket)
            from HelpdeskTicket ticket
            where ticket.requester.id = :requesterId
              and (:status is null or ticket.status = :status)
              and (:priority is null or ticket.priority = :priority)
              and (:categoryId is null or ticket.category.id = :categoryId)
            """)
    Page<HelpdeskTicket> findOwn(@Param("requesterId") Long requesterId, @Param("status") HelpdeskTicketStatus status,
            @Param("priority") HelpdeskTicketPriority priority, @Param("categoryId") Long categoryId, Pageable page);

    /** One page of every ticket, for the support queue, narrowed by each filter that is not null. */
    @Query(value = """
            select ticket
            from HelpdeskTicket ticket
            join fetch ticket.category category
            join fetch ticket.requester requester
            left join fetch ticket.assignedTo assignee
            where (:status is null or ticket.status = :status)
              and (:priority is null or ticket.priority = :priority)
              and (:categoryId is null or category.id = :categoryId)
              and (:assigneeId is null or assignee.id = :assigneeId)
              and (:requesterLoginId is null or requester.loginId = :requesterLoginId)
            order by ticket.createdAt desc, ticket.id desc
            """,
            countQuery = """
            select count(ticket)
            from HelpdeskTicket ticket
            left join ticket.assignedTo assignee
            where (:status is null or ticket.status = :status)
              and (:priority is null or ticket.priority = :priority)
              and (:categoryId is null or ticket.category.id = :categoryId)
              and (:assigneeId is null or assignee.id = :assigneeId)
              and (:requesterLoginId is null or ticket.requester.loginId = :requesterLoginId)
            """)
    Page<HelpdeskTicket> findQueue(@Param("status") HelpdeskTicketStatus status,
            @Param("priority") HelpdeskTicketPriority priority, @Param("categoryId") Long categoryId,
            @Param("assigneeId") Long assigneeId, @Param("requesterLoginId") String requesterLoginId, Pageable page);

    /**
     * Search: one requester's own tickets whose number, subject, or category code or name contains the text. The
     * description is not searched. Exact numbers or subjects first, then those that start with the text, then the
     * rest; within each, the newest first.
     */
    @Query(value = """
            select ticket
            from HelpdeskTicket ticket
            join fetch ticket.category category
            where ticket.requester.id = :requesterId
              and (lower(ticket.ticketNumber) like :contains escape '!'
                   or lower(ticket.subject) like :contains escape '!'
                   or lower(category.code) like :contains escape '!'
                   or lower(category.name) like :contains escape '!')
            order by case when lower(ticket.ticketNumber) = :exact or lower(ticket.subject) = :exact then 0 when lower(ticket.ticketNumber) like :prefix escape '!' or lower(ticket.subject) like :prefix escape '!' then 1 else 2 end, ticket.createdAt desc, ticket.id desc
            """,
            countQuery = """
            select count(ticket)
            from HelpdeskTicket ticket
            join ticket.category category
            where ticket.requester.id = :requesterId
              and (lower(ticket.ticketNumber) like :contains escape '!'
                   or lower(ticket.subject) like :contains escape '!'
                   or lower(category.code) like :contains escape '!'
                   or lower(category.name) like :contains escape '!')
            """)
    Page<HelpdeskTicket> searchOwn(@Param("requesterId") Long requesterId, @Param("exact") String exact,
            @Param("prefix") String prefix, @Param("contains") String contains, Pageable page);

    /** Search: every ticket, as {@link #searchOwn} matches and orders them, for admins who may see all tickets. */
    @Query(value = """
            select ticket
            from HelpdeskTicket ticket
            join fetch ticket.category category
            join fetch ticket.requester
            where (lower(ticket.ticketNumber) like :contains escape '!'
                   or lower(ticket.subject) like :contains escape '!'
                   or lower(category.code) like :contains escape '!'
                   or lower(category.name) like :contains escape '!')
            order by case when lower(ticket.ticketNumber) = :exact or lower(ticket.subject) = :exact then 0 when lower(ticket.ticketNumber) like :prefix escape '!' or lower(ticket.subject) like :prefix escape '!' then 1 else 2 end, ticket.createdAt desc, ticket.id desc
            """,
            countQuery = """
            select count(ticket)
            from HelpdeskTicket ticket
            join ticket.category category
            where (lower(ticket.ticketNumber) like :contains escape '!'
                   or lower(ticket.subject) like :contains escape '!'
                   or lower(category.code) like :contains escape '!'
                   or lower(category.name) like :contains escape '!')
            """)
    Page<HelpdeskTicket> searchAll(@Param("exact") String exact, @Param("prefix") String prefix,
            @Param("contains") String contains, Pageable page);

    /** Reports: how many tickets are in each status, in one grouped query. */
    @Query("select ticket.status as status, count(ticket) as total from HelpdeskTicket ticket group by ticket.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        HelpdeskTicketStatus getStatus();

        Long getTotal();
    }
}
