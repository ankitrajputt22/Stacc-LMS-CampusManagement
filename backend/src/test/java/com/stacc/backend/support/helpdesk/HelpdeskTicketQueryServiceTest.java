package com.stacc.backend.support.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskAssigneeResponse;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketPage;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

class HelpdeskTicketQueryServiceTest {

    private final HelpdeskTicketRepository tickets = mock(HelpdeskTicketRepository.class);
    private final HelpdeskTicketMessageRepository messages = mock(HelpdeskTicketMessageRepository.class);
    private final HelpdeskTicketHistoryRepository history = mock(HelpdeskTicketHistoryRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final HelpdeskTicketQueryService service =
            new HelpdeskTicketQueryService(tickets, messages, history, new HelpdeskAccessService(), accounts);

    private final HelpdeskTicket ticket = HelpdeskFixtures.ticket(42,
            HelpdeskFixtures.account(70, "student1", RoleName.STUDENT), HelpdeskFixtures.category(3, "LMS", true));

    private static void assertRefused(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, refusal -> assertThat(refusal.getStatus()).isEqualTo(status));
    }

    @Test
    void ownTicketsAreTheTokenAccountsWithTheFiltersPassedOn() {
        when(tickets.findOwn(eq(70L), eq(HelpdeskTicketStatus.OPEN), eq(HelpdeskTicketPriority.HIGH), eq(3L), any()))
                .thenReturn(new PageImpl<>(List.of(ticket), PageRequest.of(1, 5), 6));

        HelpdeskTicketPage page = service.ownTickets(HelpdeskFixtures.requester(70, "ROLE_STUDENT"),
                HelpdeskTicketStatus.OPEN, HelpdeskTicketPriority.HIGH, 3L, 1, 5);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.ticketNumber()).isEqualTo("STC-000042");
            assertThat(item.requesterLoginId()).isEqualTo("student1");
        });
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.totalPages()).isEqualTo(2);
        verify(tickets).findOwn(70L, HelpdeskTicketStatus.OPEN, HelpdeskTicketPriority.HIGH, 3L, PageRequest.of(1, 5));
    }

    @Test
    void pagesAreCheckedBeforeAnyQuery() {
        HelpdeskActor actor = HelpdeskFixtures.requester(70, "ROLE_STUDENT");
        assertRefused(() -> service.ownTickets(actor, null, null, null, -1, 20), HttpStatus.BAD_REQUEST);
        assertRefused(() -> service.ownTickets(actor, null, null, null, 0, 0), HttpStatus.BAD_REQUEST);
        assertRefused(() -> service.ownTickets(actor, null, null, null, 0, 101), HttpStatus.BAD_REQUEST);
        assertRefused(() -> service.queue(HelpdeskFixtures.admin(1), null, null, null, false, null, 0, 101),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(tickets);
    }

    @Test
    void theQueueFiltersByAssigneeOnlyForAssignedToMe() {
        when(tickets.findQueue(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.queue(HelpdeskFixtures.admin(1), HelpdeskTicketStatus.IN_PROGRESS, null, null, true, " student1 ", 0, 20);
        service.queue(HelpdeskFixtures.admin(1), null, null, null, false, " ", 0, 100);

        verify(tickets).findQueue(HelpdeskTicketStatus.IN_PROGRESS, null, null, 1L, "student1", PageRequest.of(0, 20));
        verify(tickets).findQueue(null, null, null, null, null, PageRequest.of(0, 100));
    }

    @Test
    void theQueueNeedsAnAdminWhoMaySeeEveryTicket() {
        assertRefused(() -> service.queue(HelpdeskFixtures.requester(1, "ROLE_ADMIN"), null, null, null, false, null, 0, 20),
                HttpStatus.FORBIDDEN);
        assertRefused(() -> service.queue(HelpdeskFixtures.actor(80, "ROLE_FACULTY", HelpdeskActor.VIEW_ALL), null, null,
                null, false, null, 0, 20), HttpStatus.FORBIDDEN);
        verifyNoInteractions(tickets);
    }

    @Test
    void aTicketIsShownToItsRequesterAndAdminsWithViewAll() {
        when(tickets.findWithContextById(42L)).thenReturn(Optional.of(ticket));
        when(messages.findAllWithAuthorByTicketId(42L)).thenReturn(List.of());
        when(history.findAllWithActorByTicketId(42L)).thenReturn(List.of());

        assertThat(service.ticket(HelpdeskFixtures.requester(70, "ROLE_STUDENT"), 42L).ticketNumber())
                .isEqualTo("STC-000042");
        assertThat(service.ticket(HelpdeskFixtures.admin(1), 42L).description())
                .isEqualTo("The course is listed on my enrollment but not under Courses.");
    }

    @Test
    void anInvisibleTicketAnswersTheSame404AsAMissingOne() {
        when(tickets.findWithContextById(42L)).thenReturn(Optional.of(ticket));

        for (HelpdeskActor actor : List.of(HelpdeskFixtures.requester(71, "ROLE_STUDENT"),
                HelpdeskFixtures.requester(1, "ROLE_ADMIN"), HelpdeskFixtures.actor(70, "ROLE_STUDENT"))) {
            assertThatThrownBy(() -> service.ticket(actor, 42L)).hasMessage(HelpdeskTicketCommandService.TICKET_NOT_FOUND);
        }
        assertThatThrownBy(() -> service.ticket(HelpdeskFixtures.admin(1), 404L))
                .hasMessage(HelpdeskTicketCommandService.TICKET_NOT_FOUND);
        verifyNoInteractions(messages, history);
    }

    @Test
    void assigneesAreActiveAdminLoginIdsForManagersOnly() {
        when(accounts.findLoginIdsByRoleNameAndStatus(RoleName.ADMIN, AccountStatus.ACTIVE))
                .thenReturn(List.of("admin1", "facultyadmin1"));

        assertThat(service.assignees(HelpdeskFixtures.admin(1))).extracting(HelpdeskAssigneeResponse::loginId)
                .containsExactly("admin1", "facultyadmin1");

        assertRefused(() -> service.assignees(HelpdeskFixtures.actor(1, "ROLE_ADMIN", HelpdeskActor.VIEW_ALL)),
                HttpStatus.FORBIDDEN);
        assertRefused(() -> service.assignees(HelpdeskFixtures.actor(80, "ROLE_FACULTY", HelpdeskActor.MANAGE)),
                HttpStatus.FORBIDDEN);
        verify(accounts).findLoginIdsByRoleNameAndStatus(RoleName.ADMIN, AccountStatus.ACTIVE);
    }
}
