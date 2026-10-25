package com.stacc.backend.support.helpdesk;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.CreateHelpdeskTicketRequest;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskMessageResponse;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketDetail;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change to a ticket: raising it, replying, assigning, prioritising, and moving it through its lifecycle. Each
 * real change writes its history entry here, in the same transaction, and tells the people concerned in their Inbox; a
 * request that changes nothing writes and sends nothing. The acting account always comes from the access token. Every
 * change locks the ticket row first, and a closed ticket refuses every change.
 */
@Service
@Transactional
public class HelpdeskTicketCommandService {

    static final String TICKET_NOT_FOUND = "Ticket not found.";
    static final String CATEGORY_MISSING = "The selected category does not exist.";
    static final String CATEGORY_INACTIVE = "The selected category is no longer available for new tickets.";
    static final String URGENT_NOT_ALLOWED = "Urgent priority can only be set by the support team.";
    static final String CANNOT_REPLY = "You cannot reply to this ticket.";
    static final String CLOSED = "This ticket is closed and can no longer be changed.";
    static final String ASSIGNEE_UNKNOWN = "The selected account does not exist.";
    static final String ASSIGNEE_NOT_ADMIN = "Tickets can only be assigned to active admin accounts.";
    static final String NOT_MANAGER = "You cannot manage helpdesk tickets.";

    private final HelpdeskTicketRepository tickets;
    private final HelpdeskCategoryRepository categories;
    private final HelpdeskTicketMessageRepository messages;
    private final HelpdeskTicketHistoryRepository history;
    private final UserAccountRepository accounts;
    private final HelpdeskAccessService access;
    private final HelpdeskNotifications notifications;
    private final Clock clock;

    public HelpdeskTicketCommandService(HelpdeskTicketRepository tickets, HelpdeskCategoryRepository categories,
            HelpdeskTicketMessageRepository messages, HelpdeskTicketHistoryRepository history,
            UserAccountRepository accounts, HelpdeskAccessService access, HelpdeskNotifications notifications,
            Clock clock) {
        this.tickets = tickets;
        this.categories = categories;
        this.messages = messages;
        this.history = history;
        this.accounts = accounts;
        this.access = access;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Raises a ticket for the acting account: OPEN, unassigned, and numbered from its ID. */
    public HelpdeskTicketDetail create(HelpdeskActor actor, CreateHelpdeskTicketRequest request) {
        HelpdeskCategory category = categories.findById(request.categoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, CATEGORY_MISSING));
        if (!category.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CATEGORY_INACTIVE);
        }
        HelpdeskTicketPriority priority = request.priority() == null ? HelpdeskTicketPriority.NORMAL : request.priority();
        if (!priority.isRequesterChoice()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, URGENT_NOT_ALLOWED);
        }
        UserAccount requester = accounts.getReferenceById(actor.accountId());
        HelpdeskTicket ticket = tickets.saveAndFlush(
                new HelpdeskTicket(requester, category, request.subject(), request.description(), priority));
        // The number comes from the generated ID, in the same transaction, so it is unique and never missing.
        ticket.assignNumber();
        tickets.saveAndFlush(ticket);
        record(ticket, requester, HelpdeskTicketHistoryAction.CREATED, null, ticket.getStatus().name());
        return detailOf(ticket);
    }

    /** Adds a message as the acting account: the requester, or an admin allowed to reply to every ticket. */
    public HelpdeskMessageResponse addMessage(HelpdeskActor actor, Long ticketId, String text) {
        HelpdeskTicket ticket = locked(ticketId);
        if (!access.canView(actor, ticket)) {
            throw notFound();
        }
        if (!access.canReply(actor, ticket)) {
            throw new ApiException(HttpStatus.FORBIDDEN, CANNOT_REPLY);
        }
        requireOpen(ticket);
        UserAccount author = accounts.getReferenceById(actor.accountId());
        HelpdeskTicketMessage message = messages.saveAndFlush(new HelpdeskTicketMessage(ticket, author, text));
        record(ticket, author, HelpdeskTicketHistoryAction.MESSAGE_ADDED, null, null);
        notifications.replied(ticket, actor.accountId());
        return HelpdeskMessageResponse.of(message);
    }

    /**
     * Assigns the ticket to an active admin account by login ID, or unassigns it with null. The same assignee changes
     * nothing.
     */
    public HelpdeskTicketDetail assign(HelpdeskActor actor, Long ticketId, String assigneeLoginId) {
        HelpdeskTicket ticket = managed(actor, ticketId);
        UserAccount assignee = null;
        if (assigneeLoginId != null && !assigneeLoginId.isBlank()) {
            assignee = accounts.findByLoginId(assigneeLoginId.strip())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ASSIGNEE_UNKNOWN));
            if (!isAssignable(assignee)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ASSIGNEE_NOT_ADMIN);
            }
        }
        UserAccount current = ticket.getAssignedTo();
        Long currentId = current == null ? null : current.getId();
        Long targetId = assignee == null ? null : assignee.getId();
        if (currentId == null ? targetId == null : currentId.equals(targetId)) {
            return detailOf(ticket);
        }
        String from = current == null ? null : current.getLoginId();
        ticket.assignTo(assignee);
        tickets.saveAndFlush(ticket);
        record(ticket, accounts.getReferenceById(actor.accountId()),
                assignee == null ? HelpdeskTicketHistoryAction.UNASSIGNED : HelpdeskTicketHistoryAction.ASSIGNED,
                from, assignee == null ? null : assignee.getLoginId());
        notifications.assigned(ticket, actor.accountId());
        return detailOf(ticket);
    }

    /** Sets any priority, URGENT included. The same priority changes nothing. */
    public HelpdeskTicketDetail changePriority(HelpdeskActor actor, Long ticketId, HelpdeskTicketPriority priority) {
        HelpdeskTicket ticket = managed(actor, ticketId);
        if (ticket.getPriority() != priority) {
            String from = ticket.getPriority().name();
            ticket.changePriority(priority);
            tickets.saveAndFlush(ticket);
            record(ticket, accounts.getReferenceById(actor.accountId()), HelpdeskTicketHistoryAction.PRIORITY_CHANGED,
                    from, priority.name());
        }
        return detailOf(ticket);
    }

    /**
     * Moves the ticket forward in its lifecycle and tells the requester. The same status changes nothing, also on a
     * closed ticket; any move the lifecycle does not allow, such as back to OPEN or out of CLOSED, is refused with 409.
     */
    public HelpdeskTicketDetail changeStatus(HelpdeskActor actor, Long ticketId, HelpdeskTicketStatus target) {
        HelpdeskTicket ticket = lockedForManager(actor, ticketId);
        HelpdeskTicketStatus from = ticket.getStatus();
        if (from == target) {
            return detailOf(ticket);
        }
        if (!from.canMoveTo(target)) {
            throw new ApiException(HttpStatus.CONFLICT, from == HelpdeskTicketStatus.CLOSED ? CLOSED
                    : "A " + wordOf(from) + " ticket cannot move to " + wordOf(target) + ".");
        }
        ticket.moveTo(target, clock.instant());
        tickets.saveAndFlush(ticket);
        record(ticket, accounts.getReferenceById(actor.accountId()), HelpdeskTicketHistoryAction.STATUS_CHANGED,
                from.name(), target.name());
        notifications.statusChanged(ticket, actor.accountId());
        return detailOf(ticket);
    }

    /** Whether tickets can be assigned to the account: an active account holding ADMIN, as the assignee list shows. */
    static boolean isAssignable(UserAccount account) {
        return account.hasRole(RoleName.ADMIN) && account.getStatus() == AccountStatus.ACTIVE;
    }

    // The one place history is written, so no change can forget it.
    private void record(HelpdeskTicket ticket, UserAccount actor, HelpdeskTicketHistoryAction action, String from,
            String to) {
        history.saveAndFlush(new HelpdeskTicketHistory(ticket, actor, action, from, to));
    }

    private HelpdeskTicket managed(HelpdeskActor actor, Long ticketId) {
        HelpdeskTicket ticket = lockedForManager(actor, ticketId);
        requireOpen(ticket);
        return ticket;
    }

    private HelpdeskTicket lockedForManager(HelpdeskActor actor, Long ticketId) {
        if (!access.canManage(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, NOT_MANAGER);
        }
        return locked(ticketId);
    }

    private HelpdeskTicket locked(Long ticketId) {
        if (ticketId == null) {
            throw notFound();
        }
        return tickets.findByIdForUpdate(ticketId).orElseThrow(HelpdeskTicketCommandService::notFound);
    }

    private static void requireOpen(HelpdeskTicket ticket) {
        if (ticket.isClosed()) {
            throw new ApiException(HttpStatus.CONFLICT, CLOSED);
        }
    }

    private HelpdeskTicketDetail detailOf(HelpdeskTicket ticket) {
        List<HelpdeskTicketMessage> conversation = messages.findAllWithAuthorByTicketId(ticket.getId());
        List<HelpdeskTicketHistory> trail = history.findAllWithActorByTicketId(ticket.getId());
        return HelpdeskTicketDetail.of(ticket, conversation, trail);
    }

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, TICKET_NOT_FOUND);
    }

    private static String wordOf(HelpdeskTicketStatus status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
