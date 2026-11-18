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
    static final String ASSIGNEE_NOT_ADMIN = "Tickets can only be assigned to active admin accounts or members of the ticket's team.";
    static final String NOT_MANAGER = "You cannot manage helpdesk tickets.";
    static final String REOPEN_CLOSED = "This ticket can no longer be reopened. Raise a new ticket instead.";

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
        HelpdeskTicket created = new HelpdeskTicket(requester, category, request.subject(), request.description(), priority);
        if (category.getDefaultTeam() != null && category.getDefaultTeam().isActive()) {
            created.routeTo(category.getDefaultTeam());
        }
        HelpdeskTicket ticket = tickets.saveAndFlush(created);
        ticket.assignNumber();
        tickets.saveAndFlush(ticket);
        record(ticket, requester, HelpdeskTicketHistoryAction.CREATED, null, ticket.getStatus().name());
        return detailOf(actor, ticket);
    }

    public HelpdeskMessageResponse addMessage(HelpdeskActor actor, Long ticketId, String text) {
        return addMessage(actor, ticketId, text, false);
    }

    public HelpdeskMessageResponse addMessage(HelpdeskActor actor, Long ticketId, String text, boolean internal) {
        HelpdeskTicket ticket = locked(ticketId);
        if (!access.canView(actor, ticket)) {
            throw notFound();
        }
        if (internal ? !access.isSupportSide(actor, ticket) : !access.canReply(actor, ticket)) {
            throw new ApiException(HttpStatus.FORBIDDEN, CANNOT_REPLY);
        }
        requireOpen(ticket);
        UserAccount author = accounts.getReferenceById(actor.accountId());
        HelpdeskTicketMessage message = messages.saveAndFlush(new HelpdeskTicketMessage(ticket, author, text, internal));
        if (internal) {
            record(ticket, author, HelpdeskTicketHistoryAction.NOTE_ADDED, null, null);
            return HelpdeskMessageResponse.of(message);
        }
        record(ticket, author, HelpdeskTicketHistoryAction.MESSAGE_ADDED, null, null);
        if (access.owns(actor, ticket)) {
            if (ticket.getStatus() == HelpdeskTicketStatus.WAITING_ON_REQUESTER) {
                ticket.moveTo(HelpdeskTicketStatus.IN_PROGRESS, clock.instant());
                record(ticket, author, HelpdeskTicketHistoryAction.STATUS_CHANGED,
                        HelpdeskTicketStatus.WAITING_ON_REQUESTER.name(), HelpdeskTicketStatus.IN_PROGRESS.name());
            }
        } else {
            ticket.responded(clock.instant());
        }
        tickets.saveAndFlush(ticket);
        notifications.replied(ticket, actor.accountId());
        return HelpdeskMessageResponse.of(message);
    }

    public HelpdeskTicketDetail reopen(HelpdeskActor actor, Long ticketId) {
        HelpdeskTicket ticket = locked(ticketId);
        if (!access.canView(actor, ticket)) {
            throw notFound();
        }
        if (!access.owns(actor, ticket)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the person who raised a ticket can reopen it.");
        }
        if (!ticket.canReopenAt(clock.instant())) {
            throw new ApiException(HttpStatus.CONFLICT, REOPEN_CLOSED);
        }
        ticket.reopen(clock.instant());
        tickets.saveAndFlush(ticket);
        record(ticket, accounts.getReferenceById(actor.accountId()), HelpdeskTicketHistoryAction.REOPENED,
                HelpdeskTicketStatus.RESOLVED.name(), HelpdeskTicketStatus.IN_PROGRESS.name());
        notifications.replied(ticket, actor.accountId());
        return detailOf(actor, ticket);
    }

    public HelpdeskTicketDetail rate(HelpdeskActor actor, Long ticketId, int stars, String comment) {
        HelpdeskTicket ticket = locked(ticketId);
        if (!access.canView(actor, ticket)) {
            throw notFound();
        }
        if (!access.owns(actor, ticket)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the person who raised a ticket can rate it.");
        }
        try {
            ticket.rate(stars, comment, clock.instant());
        } catch (IllegalStateException refused) {
            throw new ApiException(HttpStatus.CONFLICT, refused.getMessage() + ".");
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage() + ".");
        }
        tickets.saveAndFlush(ticket);
        record(ticket, accounts.getReferenceById(actor.accountId()), HelpdeskTicketHistoryAction.RATED, null, String.valueOf(stars));
        return detailOf(actor, ticket);
    }

    public HelpdeskTicketDetail changeTeam(HelpdeskActor actor, Long ticketId, HelpdeskTeam team) {
        if (!access.canManage(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, NOT_MANAGER);
        }
        HelpdeskTicket ticket = locked(ticketId);
        requireOpen(ticket);
        Long current = ticket.getTeam() == null ? null : ticket.getTeam().getId();
        Long target = team == null ? null : team.getId();
        if (current == null ? target == null : current.equals(target)) {
            return detailOf(actor, ticket);
        }
        String from = ticket.getTeam() == null ? null : ticket.getTeam().getCode();
        ticket.routeTo(team);
        tickets.saveAndFlush(ticket);
        record(ticket, accounts.getReferenceById(actor.accountId()), HelpdeskTicketHistoryAction.TEAM_CHANGED, from,
                team == null ? null : team.getCode());
        return detailOf(actor, ticket);
    }

    public HelpdeskTicketDetail assign(HelpdeskActor actor, Long ticketId, String assigneeLoginId) {
        HelpdeskTicket ticket = managed(actor, ticketId);
        UserAccount assignee = null;
        if (assigneeLoginId != null && !assigneeLoginId.isBlank()) {
            assignee = accounts.findByLoginId(assigneeLoginId.strip())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ASSIGNEE_UNKNOWN));
            if (!isAssignable(assignee) && !isTeamMember(ticket, assignee)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ASSIGNEE_NOT_ADMIN);
            }
        }
        UserAccount current = ticket.getAssignedTo();
        Long currentId = current == null ? null : current.getId();
        Long targetId = assignee == null ? null : assignee.getId();
        if (currentId == null ? targetId == null : currentId.equals(targetId)) {
            return detailOf(actor, ticket);
        }
        String from = current == null ? null : current.getLoginId();
        ticket.assignTo(assignee);
        tickets.saveAndFlush(ticket);
        record(ticket, accounts.getReferenceById(actor.accountId()),
                assignee == null ? HelpdeskTicketHistoryAction.UNASSIGNED : HelpdeskTicketHistoryAction.ASSIGNED,
                from, assignee == null ? null : assignee.getLoginId());
        notifications.assigned(ticket, actor.accountId());
        return detailOf(actor, ticket);
    }

    public HelpdeskTicketDetail changePriority(HelpdeskActor actor, Long ticketId, HelpdeskTicketPriority priority) {
        HelpdeskTicket ticket = managed(actor, ticketId);
        if (ticket.getPriority() != priority) {
            String from = ticket.getPriority().name();
            ticket.changePriority(priority);
            tickets.saveAndFlush(ticket);
            record(ticket, accounts.getReferenceById(actor.accountId()), HelpdeskTicketHistoryAction.PRIORITY_CHANGED,
                    from, priority.name());
        }
        return detailOf(actor, ticket);
    }

    public HelpdeskTicketDetail changeStatus(HelpdeskActor actor, Long ticketId, HelpdeskTicketStatus target) {
        HelpdeskTicket ticket = lockedForManager(actor, ticketId);
        HelpdeskTicketStatus from = ticket.getStatus();
        if (from == target) {
            return detailOf(actor, ticket);
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
        return detailOf(actor, ticket);
    }

    private boolean isTeamMember(HelpdeskTicket ticket, UserAccount account) {
        return ticket.getTeam() != null && account.getStatus() == AccountStatus.ACTIVE
                && access.worksOnTeam(new HelpdeskActor(account.getId(), false, false, false, false, false, false, true), ticket);
    }

    static boolean isAssignable(UserAccount account) {
        return account.hasRole(RoleName.ADMIN) && account.getStatus() == AccountStatus.ACTIVE;
    }

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
        if (!access.canManage(actor) && !actor.teamWork()) {
            throw new ApiException(HttpStatus.FORBIDDEN, NOT_MANAGER);
        }
        HelpdeskTicket ticket = locked(ticketId);
        if (!access.canWork(actor, ticket)) {
            if (!access.canView(actor, ticket)) {
                throw notFound();
            }
            throw new ApiException(HttpStatus.FORBIDDEN, NOT_MANAGER);
        }
        return ticket;
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

    private HelpdeskTicketDetail detailOf(HelpdeskActor actor, HelpdeskTicket ticket) {
        List<HelpdeskTicketMessage> conversation = messages.findAllWithAuthorByTicketId(ticket.getId());
        List<HelpdeskTicketHistory> trail = history.findAllWithActorByTicketId(ticket.getId());
        return HelpdeskTicketDetail.of(ticket, conversation, trail, access.isSupportSide(actor, ticket),
                access.owns(actor, ticket), clock.instant());
    }

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, TICKET_NOT_FOUND);
    }

    private static String wordOf(HelpdeskTicketStatus status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
