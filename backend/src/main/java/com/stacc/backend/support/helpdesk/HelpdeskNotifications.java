package com.stacc.backend.support.helpdesk;

import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.stereotype.Component;

/**
 * The Inbox notifications about tickets, worded in one place. Nobody is told about their own action. There is no link
 * yet: the helpdesk pages arrive in a later phase, so the action path stays empty rather than pointing nowhere.
 */
@Component
public class HelpdeskNotifications {

    private final NotificationService notifications;

    public HelpdeskNotifications(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** Tells the requester that someone else replied. */
    public void replied(HelpdeskTicket ticket, Long authorAccountId) {
        notifyRequester(ticket, authorAccountId, NotificationType.HELPDESK_REPLY, "New reply on your ticket",
                "There is a new reply on " + ticket.getTicketNumber() + ": " + ticket.getSubject() + ".");
    }

    /** Tells the requester that the status changed. */
    public void statusChanged(HelpdeskTicket ticket, Long actorAccountId) {
        notifyRequester(ticket, actorAccountId, NotificationType.HELPDESK_STATUS_CHANGED, "Ticket status updated",
                ticket.getTicketNumber() + " (" + ticket.getSubject() + ") is now " + wordOf(ticket.getStatus()) + ".");
    }

    /** Tells the newly assigned admin, unless they assigned it to themselves. */
    public void assigned(HelpdeskTicket ticket, Long actorAccountId) {
        Long assignee = ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId();
        if (assignee != null && !assignee.equals(actorAccountId)) {
            notifications.notifyUser(message(ticket, NotificationType.HELPDESK_ASSIGNED, "Ticket assigned to you",
                    ticket.getTicketNumber() + " (" + ticket.getSubject() + ") was assigned to you."), assignee);
        }
    }

    private void notifyRequester(HelpdeskTicket ticket, Long actorAccountId, NotificationType type, String title,
            String text) {
        Long requester = ticket.getRequester().getId();
        if (!requester.equals(actorAccountId)) {
            notifications.notifyUser(message(ticket, type, title, text), requester);
        }
    }

    private static NotificationMessage message(HelpdeskTicket ticket, NotificationType type, String title, String text) {
        return new NotificationMessage(type, title, text, NotificationSourceType.HELPDESK_TICKET, ticket.getId(), null);
    }

    private static String wordOf(HelpdeskTicketStatus status) {
        return switch (status) {
            case OPEN -> "open";
            case IN_PROGRESS -> "in progress";
            case RESOLVED -> "resolved";
            case CLOSED -> "closed";
        };
    }
}
