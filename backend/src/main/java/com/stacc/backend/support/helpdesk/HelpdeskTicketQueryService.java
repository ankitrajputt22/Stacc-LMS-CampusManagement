package com.stacc.backend.support.helpdesk;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketDetail;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketPage;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading tickets: a requester's own list, the admin queue, and one ticket in full. A ticket the actor may not see
 * answers the same 404 as one that does not exist, so ticket IDs reveal nothing. It only reads.
 */
@Service
@Transactional(readOnly = true)
public class HelpdeskTicketQueryService {

    static final int MAX_PAGE_SIZE = 100;
    static final String BAD_PAGE = "The page must be 0 or more.";
    static final String BAD_SIZE = "The page size must be between 1 and " + MAX_PAGE_SIZE + ".";

    private final HelpdeskTicketRepository tickets;
    private final HelpdeskTicketMessageRepository messages;
    private final HelpdeskTicketHistoryRepository history;
    private final HelpdeskAccessService access;

    public HelpdeskTicketQueryService(HelpdeskTicketRepository tickets, HelpdeskTicketMessageRepository messages,
            HelpdeskTicketHistoryRepository history, HelpdeskAccessService access) {
        this.tickets = tickets;
        this.messages = messages;
        this.history = history;
        this.access = access;
    }

    /** The acting account's own tickets, newest first. */
    public HelpdeskTicketPage ownTickets(HelpdeskActor actor, HelpdeskTicketStatus status, HelpdeskTicketPriority priority,
            Long categoryId, int page, int size) {
        PageRequest request = pageOf(page, size);
        return pageOf(tickets.findOwn(actor.accountId(), status, priority, categoryId, request), request);
    }

    /** Every ticket, newest first, for admins who may see them all. {@code assignedToMe} keeps the actor's own. */
    public HelpdeskTicketPage queue(HelpdeskActor actor, HelpdeskTicketStatus status, HelpdeskTicketPriority priority,
            Long categoryId, boolean assignedToMe, String requesterLoginId, int page, int size) {
        if (!(actor.admin() && actor.viewAll())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot see every helpdesk ticket.");
        }
        PageRequest request = pageOf(page, size);
        String requester = requesterLoginId == null || requesterLoginId.isBlank() ? null : requesterLoginId.strip();
        return pageOf(tickets.findQueue(status, priority, categoryId, assignedToMe ? actor.accountId() : null, requester,
                request), request);
    }

    /** One ticket in full, if the actor may see it. */
    public HelpdeskTicketDetail ticket(HelpdeskActor actor, Long ticketId) {
        HelpdeskTicket ticket = (ticketId == null ? null : tickets.findWithContextById(ticketId).orElse(null));
        if (ticket == null || !access.canView(actor, ticket)) {
            throw HelpdeskTicketCommandService.notFound();
        }
        return HelpdeskTicketDetail.of(ticket, messages.findAllWithAuthorByTicketId(ticketId),
                history.findAllWithActorByTicketId(ticketId));
    }

    private static PageRequest pageOf(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, BAD_PAGE);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, BAD_SIZE);
        }
        return PageRequest.of(page, size);
    }

    private static HelpdeskTicketPage pageOf(Page<HelpdeskTicket> rows, PageRequest request) {
        return new HelpdeskTicketPage(rows.getContent().stream().map(HelpdeskTicketSummary::of).toList(),
                request.getPageNumber(), request.getPageSize(), rows.getTotalElements(), rows.getTotalPages());
    }
}
