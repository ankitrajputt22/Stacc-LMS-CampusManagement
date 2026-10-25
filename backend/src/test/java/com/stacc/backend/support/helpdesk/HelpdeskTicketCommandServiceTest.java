package com.stacc.backend.support.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.CreateHelpdeskTicketRequest;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskHistoryResponse;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class HelpdeskTicketCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T09:30:00.123456789Z");

    private final UserAccount student = HelpdeskFixtures.account(70, "student1", RoleName.STUDENT);
    private final UserAccount otherStudent = HelpdeskFixtures.account(71, "student2", RoleName.STUDENT);
    private final UserAccount admin = HelpdeskFixtures.account(1, "admin1", RoleName.ADMIN);
    private final UserAccount secondAdmin = HelpdeskFixtures.account(2, "facultyadmin1", RoleName.FACULTY, RoleName.ADMIN);
    private final UserAccount faculty = HelpdeskFixtures.account(80, "faculty1", RoleName.FACULTY);
    private final HelpdeskCategory lms = HelpdeskFixtures.category(3, "LMS", true);
    private final HelpdeskCategory retired = HelpdeskFixtures.category(4, "OLD", false);

    private final HelpdeskTicketRepository tickets = mock(HelpdeskTicketRepository.class);
    private final HelpdeskCategoryRepository categories = mock(HelpdeskCategoryRepository.class);
    private final HelpdeskTicketMessageRepository messages = mock(HelpdeskTicketMessageRepository.class);
    private final HelpdeskTicketHistoryRepository history = mock(HelpdeskTicketHistoryRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final HelpdeskNotifications notifications = mock(HelpdeskNotifications.class);
    private final HelpdeskTicketCommandService service = new HelpdeskTicketCommandService(tickets, categories, messages,
            history, accounts, new HelpdeskAccessService(), notifications, Clock.fixed(NOW, ZoneOffset.UTC));

    private final List<HelpdeskTicketHistory> trail = new ArrayList<>();
    private final List<HelpdeskTicketMessage> conversation = new ArrayList<>();
    private HelpdeskTicket ticket;

    private final HelpdeskActor studentActor = HelpdeskFixtures.requester(70, "ROLE_STUDENT");
    private final HelpdeskActor adminActor = HelpdeskFixtures.admin(1);

    @BeforeEach
    void setUp() {
        ticket = HelpdeskFixtures.ticket(42, student, lms);
        Map<Long, UserAccount> byId = Map.of(70L, student, 71L, otherStudent, 1L, admin, 2L, secondAdmin, 80L, faculty);
        when(accounts.getReferenceById(anyLong())).thenAnswer(call -> byId.get(call.<Long>getArgument(0)));
        for (UserAccount account : byId.values()) {
            when(accounts.findByLoginId(account.getLoginId())).thenReturn(Optional.of(account));
        }
        when(categories.findById(3L)).thenReturn(Optional.of(lms));
        when(categories.findById(4L)).thenReturn(Optional.of(retired));
        when(tickets.findByIdForUpdate(42L)).thenAnswer(call -> Optional.of(ticket));
        when(tickets.saveAndFlush(any())).thenAnswer(call -> {
            HelpdeskTicket saved = call.getArgument(0);
            return saved.getId() == null ? HelpdeskFixtures.withId(saved, 42) : saved;
        });
        when(history.saveAndFlush(any())).thenAnswer(call -> {
            trail.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(messages.saveAndFlush(any())).thenAnswer(call -> {
            conversation.add(HelpdeskFixtures.withId(call.getArgument(0), 7));
            return call.getArgument(0);
        });
        when(history.findAllWithActorByTicketId(42L)).thenReturn(trail);
        when(messages.findAllWithAuthorByTicketId(42L)).thenReturn(conversation);
    }

    private static void assertRefused(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, refusal -> assertThat(refusal.getStatus()).isEqualTo(status));
    }

    private static CreateHelpdeskTicketRequest request(long categoryId, HelpdeskTicketPriority priority) {
        return new CreateHelpdeskTicketRequest(categoryId, "  Cannot open CS302 ", "Listed but not under Courses.",
                priority);
    }

    private HelpdeskTicket closed() {
        ticket.moveTo(HelpdeskTicketStatus.CLOSED, NOW);
        return ticket;
    }

    // Creating

    @Test
    void aNewTicketIsOpenNumberedAndRecordedAsCreatedByTheRequester() {
        HelpdeskTicketDetail created = service.create(studentActor, request(3, null));

        assertThat(created.ticketNumber()).isEqualTo("STC-000042");
        assertThat(created.status()).isEqualTo("OPEN");
        assertThat(created.priority()).isEqualTo("NORMAL");
        assertThat(created.subject()).isEqualTo("Cannot open CS302");
        assertThat(created.requesterLoginId()).isEqualTo("student1");
        assertThat(created.assignedToLoginId()).isNull();
        assertThat(created.history()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo("CREATED");
            assertThat(entry.actorLoginId()).isEqualTo("student1");
            assertThat(entry.toValue()).isEqualTo("OPEN");
        });
        verifyNoInteractions(notifications);
    }

    @Test
    void requestersMayChooseLowNormalOrHighButNotUrgent() {
        assertThat(service.create(studentActor, request(3, HelpdeskTicketPriority.HIGH)).priority()).isEqualTo("HIGH");

        assertRefused(() -> service.create(studentActor, request(3, HelpdeskTicketPriority.URGENT)),
                HttpStatus.BAD_REQUEST);
        assertRefused(() -> service.create(adminActor, request(3, HelpdeskTicketPriority.URGENT)),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void theCategoryMustExistAndBeActive() {
        assertRefused(() -> service.create(studentActor, request(99, null)), HttpStatus.NOT_FOUND);
        assertRefused(() -> service.create(studentActor, request(4, null)), HttpStatus.BAD_REQUEST);
        verify(tickets, never()).saveAndFlush(any());
    }

    // Messages

    @Test
    void theRequesterRepliesAndTheNotifierDecidesWhoHears() {
        var message = service.addMessage(studentActor, 42L, "  The course code is CS302. ");

        assertThat(message.authorLoginId()).isEqualTo("student1");
        assertThat(message.message()).isEqualTo("The course code is CS302.");
        assertThat(trail).extracting(HelpdeskTicketHistory::getAction)
                .containsExactly(HelpdeskTicketHistoryAction.MESSAGE_ADDED);
        verify(notifications).replied(ticket, 70L);
    }

    @Test
    void anAdminReplyNeverChangesTheStatus() {
        ticket.moveTo(HelpdeskTicketStatus.RESOLVED, NOW);

        service.addMessage(adminActor, 42L, "Please check again now.");

        assertThat(ticket.getStatus()).isEqualTo(HelpdeskTicketStatus.RESOLVED);
        verify(notifications).replied(ticket, 1L);
    }

    @Test
    void anotherRequestersTicketLooksMissing() {
        assertRefused(() -> service.addMessage(HelpdeskFixtures.requester(71, "ROLE_STUDENT"), 42L, "Hello"),
                HttpStatus.NOT_FOUND);
        assertRefused(() -> service.addMessage(studentActor, 404L, "Hello"), HttpStatus.NOT_FOUND);
        verify(messages, never()).saveAndFlush(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void anAdminWhoMayOnlyViewCannotReply() {
        assertRefused(() -> service.addMessage(HelpdeskFixtures.actor(1, "ROLE_ADMIN", HelpdeskActor.VIEW_ALL), 42L,
                "Hello"), HttpStatus.FORBIDDEN);
        verify(messages, never()).saveAndFlush(any());
    }

    @Test
    void aClosedTicketTakesNoMessages() {
        closed();
        assertRefused(() -> service.addMessage(studentActor, 42L, "Hello?"), HttpStatus.CONFLICT);
        assertRefused(() -> service.addMessage(adminActor, 42L, "Hello?"), HttpStatus.CONFLICT);
        verify(messages, never()).saveAndFlush(any());
        assertThat(trail).isEmpty();
    }

    // Assignment

    @Test
    void assigningRecordsTheLoginIdsAndTellsTheAssignee() {
        HelpdeskTicketDetail assigned = service.assign(adminActor, 42L, "facultyadmin1");

        assertThat(assigned.assignedToLoginId()).isEqualTo("facultyadmin1");
        assertThat(assigned.history()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo("ASSIGNED");
            assertThat(entry.fromValue()).isNull();
            assertThat(entry.toValue()).isEqualTo("facultyadmin1");
            assertThat(entry.actorLoginId()).isEqualTo("admin1");
        });
        verify(notifications).assigned(ticket, 1L);
    }

    @Test
    void reassigningAndUnassigningAreRecorded() {
        service.assign(adminActor, 42L, "admin1");
        service.assign(adminActor, 42L, "facultyadmin1");
        HelpdeskTicketDetail unassigned = service.assign(adminActor, 42L, null);

        assertThat(unassigned.assignedToLoginId()).isNull();
        assertThat(unassigned.history()).extracting(HelpdeskHistoryResponse::action, HelpdeskHistoryResponse::fromValue,
                HelpdeskHistoryResponse::toValue).containsExactly(
                        tuple("ASSIGNED", null, "admin1"),
                        tuple("ASSIGNED", "admin1", "facultyadmin1"),
                        tuple("UNASSIGNED", "facultyadmin1", null));
    }

    @Test
    void theSameAssigneeChangesNothing() {
        service.assign(adminActor, 42L, "admin1");
        service.assign(adminActor, 42L, " admin1 ");
        service.assign(adminActor, 42L, null);
        service.assign(adminActor, 42L, "");

        assertThat(trail).hasSize(2);
        verify(notifications, times(2)).assigned(any(), anyLong());
    }

    @Test
    void onlyExistingAdminAccountsCanBeAssigned() {
        assertRefused(() -> service.assign(adminActor, 42L, "faculty1"), HttpStatus.BAD_REQUEST);
        assertRefused(() -> service.assign(adminActor, 42L, "student1"), HttpStatus.BAD_REQUEST);
        assertRefused(() -> service.assign(adminActor, 42L, "nobody"), HttpStatus.BAD_REQUEST);
        assertThat(ticket.getAssignedTo()).isNull();
        assertThat(trail).isEmpty();
    }

    // Priority

    @Test
    void adminsMaySetAnyPriorityAndTheSameOneChangesNothing() {
        HelpdeskTicketDetail urgent = service.changePriority(adminActor, 42L, HelpdeskTicketPriority.URGENT);
        service.changePriority(adminActor, 42L, HelpdeskTicketPriority.URGENT);

        assertThat(urgent.priority()).isEqualTo("URGENT");
        assertThat(trail).singleElement().satisfies(entry -> {
            assertThat(entry.getAction()).isEqualTo(HelpdeskTicketHistoryAction.PRIORITY_CHANGED);
            assertThat(entry.getFromValue()).isEqualTo("NORMAL");
            assertThat(entry.getToValue()).isEqualTo("URGENT");
        });
        verifyNoInteractions(notifications);
    }

    // Status

    @Test
    void aRealStatusChangeIsRecordedTimedAndToldToTheRequester() {
        service.changeStatus(adminActor, 42L, HelpdeskTicketStatus.IN_PROGRESS);
        HelpdeskTicketDetail resolved = service.changeStatus(adminActor, 42L, HelpdeskTicketStatus.RESOLVED);

        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.resolvedAt()).isEqualTo(Instant.parse("2026-10-25T09:30:00.123456Z"));
        assertThat(resolved.closedAt()).isNull();
        assertThat(resolved.history()).extracting(HelpdeskHistoryResponse::fromValue, HelpdeskHistoryResponse::toValue)
                .containsExactly(tuple("OPEN", "IN_PROGRESS"),
                        tuple("IN_PROGRESS", "RESOLVED"));
        verify(notifications, times(2)).statusChanged(ticket, 1L);
    }

    @Test
    void theSameStatusChangesNothingAndNotifiesNobody() {
        service.changeStatus(adminActor, 42L, HelpdeskTicketStatus.OPEN);

        assertThat(trail).isEmpty();
        verifyNoInteractions(notifications);
    }

    @Test
    void movesTheLifecycleDoesNotAllowAreConflicts() {
        ticket.moveTo(HelpdeskTicketStatus.RESOLVED, NOW);
        assertRefused(() -> service.changeStatus(adminActor, 42L, HelpdeskTicketStatus.OPEN), HttpStatus.CONFLICT);
        assertRefused(() -> service.changeStatus(adminActor, 42L, HelpdeskTicketStatus.IN_PROGRESS), HttpStatus.CONFLICT);
        assertThat(trail).isEmpty();
        verifyNoInteractions(notifications);
    }

    @Test
    void aClosedTicketCannotBeReopenedOrChangedButClosedAgainIsANoOp() {
        closed();
        Instant closedAt = ticket.getClosedAt();

        assertThat(service.changeStatus(adminActor, 42L, HelpdeskTicketStatus.CLOSED).closedAt()).isEqualTo(closedAt);
        for (HelpdeskTicketStatus target : List.of(HelpdeskTicketStatus.OPEN, HelpdeskTicketStatus.IN_PROGRESS,
                HelpdeskTicketStatus.RESOLVED)) {
            assertRefused(() -> service.changeStatus(adminActor, 42L, target), HttpStatus.CONFLICT);
        }
        assertRefused(() -> service.changePriority(adminActor, 42L, HelpdeskTicketPriority.HIGH), HttpStatus.CONFLICT);
        assertRefused(() -> service.assign(adminActor, 42L, "admin1"), HttpStatus.CONFLICT);
        assertThat(trail).isEmpty();
        verifyNoInteractions(notifications);
    }

    @Test
    void onlyAdminsWithTheManagePermissionManageTickets() {
        for (HelpdeskActor actor : List.of(studentActor, HelpdeskFixtures.requester(1, "ROLE_ADMIN"),
                HelpdeskFixtures.actor(80, "ROLE_FACULTY", HelpdeskActor.MANAGE))) {
            assertRefused(() -> service.assign(actor, 42L, "admin1"), HttpStatus.FORBIDDEN);
            assertRefused(() -> service.changePriority(actor, 42L, HelpdeskTicketPriority.LOW), HttpStatus.FORBIDDEN);
            assertRefused(() -> service.changeStatus(actor, 42L, HelpdeskTicketStatus.CLOSED), HttpStatus.FORBIDDEN);
        }
        verify(tickets, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void aMissingTicketIs404() {
        assertRefused(() -> service.changeStatus(adminActor, 404L, HelpdeskTicketStatus.CLOSED), HttpStatus.NOT_FOUND);
        assertRefused(() -> service.assign(adminActor, null, "admin1"), HttpStatus.NOT_FOUND);
    }
}
