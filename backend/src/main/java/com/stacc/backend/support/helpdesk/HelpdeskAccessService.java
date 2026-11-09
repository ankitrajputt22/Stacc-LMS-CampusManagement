package com.stacc.backend.support.helpdesk;

import org.springframework.stereotype.Component;

/**
 * The one place that says who may see, answer, and manage a ticket. Requesters reach only their own tickets, with the
 * matching "own" permissions; admins reach every ticket only with the explicit "all" permissions and the admin role.
 * There is no other pass. The routes check roles and permissions too; these rules decide per ticket.
 */
@Component
public class HelpdeskAccessService {

    private final HelpdeskTeamMemberRepository members;

    @org.springframework.beans.factory.annotation.Autowired
    public HelpdeskAccessService(HelpdeskTeamMemberRepository members) {
        this.members = members;
    }

    /** Without team memberships: only the requester and admin rules apply. */
    public HelpdeskAccessService() {
        this(null);
    }

    /** A staff or admin member of the ticket's active team, with HELPDESK_TEAM_WORK. */
    public boolean worksOnTeam(HelpdeskActor actor, HelpdeskTicket ticket) {
        return actor.teamWork() && members != null && ticket.getTeam() != null && actor.accountId() != null
                && members.isActiveMember(ticket.getTeam().getId(), actor.accountId());
    }

    /** The support side of a ticket: an admin who sees every ticket, or a member of its team. They see internal notes. */
    public boolean isSupportSide(HelpdeskActor actor, HelpdeskTicket ticket) {
        return canViewAll(actor) || worksOnTeam(actor, ticket);
    }

    /** Who may change the ticket's status, assignment, and notes: an admin with MANAGE, or a member of its team. */
    public boolean canWork(HelpdeskActor actor, HelpdeskTicket ticket) {
        return canManage(actor) || worksOnTeam(actor, ticket);
    }

    /** The requester with VIEW_OWN, or an admin with VIEW_ALL. */
    public boolean canView(HelpdeskActor actor, HelpdeskTicket ticket) {
        return (owns(actor, ticket) && canViewOwn(actor)) || canViewAll(actor) || worksOnTeam(actor, ticket);
    }

    /** Whether the actor may see every ticket: an admin with VIEW_ALL. Lists such as search use it for their scope. */
    public boolean canViewAll(HelpdeskActor actor) {
        return actor.admin() && actor.viewAll();
    }

    /** Whether the actor may see its own tickets: VIEW_OWN. */
    public boolean canViewOwn(HelpdeskActor actor) {
        return actor.viewOwn();
    }

    /** The requester with REPLY_OWN, or an admin who can see every ticket and has REPLY_ALL. */
    public boolean canReply(HelpdeskActor actor, HelpdeskTicket ticket) {
        return (owns(actor, ticket) && actor.replyOwn()) || (actor.admin() && actor.viewAll() && actor.replyAll())
                || worksOnTeam(actor, ticket);
    }

    /** An admin with MANAGE: assignment, priority, and status. */
    public boolean canManage(HelpdeskActor actor) {
        return actor.admin() && actor.manage();
    }

    public boolean owns(HelpdeskActor actor, HelpdeskTicket ticket) {
        return actor.accountId() != null && actor.accountId().equals(ticket.getRequester().getId());
    }
}
