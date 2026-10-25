package com.stacc.backend.support.helpdesk;

import org.springframework.stereotype.Component;

/**
 * The one place that says who may see, answer, and manage a ticket. Requesters reach only their own tickets, with the
 * matching "own" permissions; admins reach every ticket only with the explicit "all" permissions and the admin role.
 * There is no other pass. The routes check roles and permissions too; these rules decide per ticket.
 */
@Component
public class HelpdeskAccessService {

    /** The requester with VIEW_OWN, or an admin with VIEW_ALL. */
    public boolean canView(HelpdeskActor actor, HelpdeskTicket ticket) {
        return (owns(actor, ticket) && actor.viewOwn()) || (actor.admin() && actor.viewAll());
    }

    /** The requester with REPLY_OWN, or an admin who can see every ticket and has REPLY_ALL. */
    public boolean canReply(HelpdeskActor actor, HelpdeskTicket ticket) {
        return (owns(actor, ticket) && actor.replyOwn()) || (actor.admin() && actor.viewAll() && actor.replyAll());
    }

    /** An admin with MANAGE: assignment, priority, and status. */
    public boolean canManage(HelpdeskActor actor) {
        return actor.admin() && actor.manage();
    }

    public boolean owns(HelpdeskActor actor, HelpdeskTicket ticket) {
        return actor.accountId() != null && actor.accountId().equals(ticket.getRequester().getId());
    }
}
