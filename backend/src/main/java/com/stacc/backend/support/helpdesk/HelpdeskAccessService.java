package com.stacc.backend.support.helpdesk;

import org.springframework.stereotype.Component;

@Component
public class HelpdeskAccessService {

    private final HelpdeskTeamMemberRepository members;

    @org.springframework.beans.factory.annotation.Autowired
    public HelpdeskAccessService(HelpdeskTeamMemberRepository members) {
        this.members = members;
    }

    public HelpdeskAccessService() {
        this(null);
    }

    public boolean worksOnTeam(HelpdeskActor actor, HelpdeskTicket ticket) {
        return actor.teamWork() && members != null && ticket.getTeam() != null && actor.accountId() != null
                && members.isActiveMember(ticket.getTeam().getId(), actor.accountId());
    }

    public boolean isSupportSide(HelpdeskActor actor, HelpdeskTicket ticket) {
        return canViewAll(actor) || worksOnTeam(actor, ticket);
    }

    public boolean canWork(HelpdeskActor actor, HelpdeskTicket ticket) {
        return canManage(actor) || worksOnTeam(actor, ticket);
    }

    public boolean canView(HelpdeskActor actor, HelpdeskTicket ticket) {
        return (owns(actor, ticket) && canViewOwn(actor)) || canViewAll(actor) || worksOnTeam(actor, ticket);
    }

    public boolean canViewAll(HelpdeskActor actor) {
        return actor.admin() && actor.viewAll();
    }

    public boolean canViewOwn(HelpdeskActor actor) {
        return actor.viewOwn();
    }

    public boolean canReply(HelpdeskActor actor, HelpdeskTicket ticket) {
        return (owns(actor, ticket) && actor.replyOwn()) || (actor.admin() && actor.viewAll() && actor.replyAll())
                || worksOnTeam(actor, ticket);
    }

    public boolean canManage(HelpdeskActor actor) {
        return actor.admin() && actor.manage();
    }

    public boolean owns(HelpdeskActor actor, HelpdeskTicket ticket) {
        return actor.accountId() != null && actor.accountId().equals(ticket.getRequester().getId());
    }
}
