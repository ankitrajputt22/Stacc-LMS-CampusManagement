package com.stacc.backend.search;

import java.util.Optional;
import java.util.Set;

import com.stacc.backend.support.helpdesk.HelpdeskAccessService;
import com.stacc.backend.support.helpdesk.HelpdeskActor;
import com.stacc.backend.support.helpdesk.HelpdeskTicket;
import com.stacc.backend.support.helpdesk.HelpdeskTicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Helpdesk tickets, scoped by {@link HelpdeskAccessService}, the helpdesk's one access rule: an admin with
 * HELPDESK_TICKET_VIEW_ALL searches every ticket; anyone else with HELPDESK_TICKET_VIEW_OWN only their own. Another
 * person's ticket never appears, and is never counted. The description is not searched. Every result opens the shared
 * ticket page, which checks access again.
 */
@Component
public class HelpdeskSearchProvider implements SearchProvider {

    private final HelpdeskAccessService access;
    private final HelpdeskTicketRepository tickets;

    public HelpdeskSearchProvider(HelpdeskAccessService access, HelpdeskTicketRepository tickets) {
        this.access = access;
        this.tickets = tickets;
    }

    @Override
    public Set<SearchResultType> types() {
        return Set.of(SearchResultType.HELPDESK_TICKET);
    }

    @Override
    public Optional<Page<SearchResult>> search(SearchResultType type, SearchContext context, Pageable page) {
        SearchActor actor = context.actor();
        if (type != SearchResultType.HELPDESK_TICKET || !actor.holdsAnyCollegeRole()) {
            return Optional.empty();
        }
        HelpdeskActor helpdesk = HelpdeskActor.of(actor.accountId(), actor.grantedAuthorities());
        SearchText text = context.text();
        if (access.canViewAll(helpdesk)) {
            return Optional.of(tickets.searchAll(text.exact(), text.prefix(), text.contains(), page)
                    .map(ticket -> resultOf(ticket, true)));
        }
        if (access.canViewOwn(helpdesk)) {
            return Optional.of(tickets.searchOwn(actor.accountId(), text.exact(), text.prefix(), text.contains(), page)
                    .map(ticket -> resultOf(ticket, false)));
        }
        return Optional.empty();
    }

    private static SearchResult resultOf(HelpdeskTicket ticket, boolean everyTicket) {
        return new SearchResult(SearchResultType.HELPDESK_TICKET, ticket.getSubject(),
                SearchFormat.join(ticket.getTicketNumber(), ticket.getCategory().getName(),
                        SearchFormat.words(ticket.getStatus()), everyTicket ? ticket.getRequester().getLoginId() : null),
                null, "/support/tickets/" + ticket.getId(), ticket.getCreatedAt());
    }
}
