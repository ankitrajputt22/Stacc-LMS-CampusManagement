package com.stacc.backend.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApprovalNotificationsTest {

    private final NotificationService service = mock(NotificationService.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final ApprovalNotifications notifications = new ApprovalNotifications(service, accounts);
    private final ApprovalRequest request = ApprovalFixtures.request(12,
            ApprovalFixtures.workflow(4, "TWO_STEP", RoleName.FACULTY, RoleName.ADMIN),
            ApprovalFixtures.account(80, "faculty1", RoleName.FACULTY));

    @Test
    @SuppressWarnings("unchecked")
    void aStepWaitingForARoleTellsItsActiveHoldersButNotTheRequester() {
        when(accounts.findIdsByRoleNameAndStatus(RoleName.FACULTY, AccountStatus.ACTIVE)).thenReturn(List.of(80L, 81L, 2L));

        notifications.actionRequired(request, request.getSteps().get(0));

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        ArgumentCaptor<Collection<Long>> recipients = ArgumentCaptor.forClass(Collection.class);
        verify(service).notifyUsers(message.capture(), recipients.capture());
        assertThat(recipients.getValue()).containsExactly(81L, 2L);
        assertThat(message.getValue().type()).isEqualTo(NotificationType.APPROVAL_ACTION_REQUIRED);
        assertThat(message.getValue().message()).isEqualTo("An approval request requires your review: Demo approval 12.");
        assertThat(message.getValue().sourceType()).isEqualTo(NotificationSourceType.APPROVAL_REQUEST);
        assertThat(message.getValue().sourceId()).isEqualTo(12L);
        assertThat(message.getValue().actionPath()).isNull();
        verify(accounts, never()).findIdsByRoleNameAndStatus(eq(RoleName.ADMIN), eq(AccountStatus.ACTIVE));
    }

    @Test
    void theRequesterIsToldTheOutcomeWithoutTheReason() {
        notifications.approved(request);
        notifications.rejected(request);

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(service, times(2)).notifyUser(message.capture(), eq(80L));
        assertThat(message.getAllValues()).extracting(NotificationMessage::type)
                .containsExactly(NotificationType.APPROVAL_APPROVED, NotificationType.APPROVAL_REJECTED);
        assertThat(message.getAllValues().get(1).message()).isEqualTo("Your approval request was rejected: Demo approval 12.");
        assertThat(message.getAllValues()).allSatisfy(sent -> assertThat(sent.actionPath()).isNull());
    }

    @Test
    void aCancellationTellsTheRequesterUnlessTheyCancelled() {
        notifications.cancelled(request, 80L);
        verify(service, never()).notifyUser(any(), eq(80L));

        notifications.cancelled(request, null);
        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(service).notifyUser(message.capture(), eq(80L));
        assertThat(message.getValue().type()).isEqualTo(NotificationType.APPROVAL_CANCELLED);
    }
}
