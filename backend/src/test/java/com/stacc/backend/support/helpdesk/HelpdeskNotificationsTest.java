package com.stacc.backend.support.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;

import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HelpdeskNotificationsTest {

    private final NotificationService service = mock(NotificationService.class);
    private final HelpdeskNotifications notifications = new HelpdeskNotifications(service);
    private final HelpdeskTicket ticket = HelpdeskFixtures.ticket(42,
            HelpdeskFixtures.account(70, "student1", RoleName.STUDENT), HelpdeskFixtures.category(3, "LMS", true));

    private NotificationMessage sentTo(long accountId) {
        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(service).notifyUser(message.capture(), eq(accountId));
        return message.getValue();
    }

    @Test
    void anAdminReplyTellsTheRequesterWithoutALinkYet() {
        notifications.replied(ticket, 1L);

        NotificationMessage message = sentTo(70);
        assertThat(message.type()).isEqualTo(NotificationType.HELPDESK_REPLY);
        assertThat(message.message()).contains("STC-000042").contains("Cannot open my course");
        assertThat(message.sourceType()).isEqualTo(NotificationSourceType.HELPDESK_TICKET);
        assertThat(message.sourceId()).isEqualTo(42L);
        assertThat(message.actionPath()).isNull();
    }

    @Test
    void nobodyIsToldAboutTheirOwnAction() {
        notifications.replied(ticket, 70L);
        notifications.statusChanged(ticket, 70L);
        ticket.assignTo(HelpdeskFixtures.account(1, "admin1", RoleName.ADMIN));
        notifications.assigned(ticket, 1L);

        verifyNoInteractions(service);
    }

    @Test
    void aStatusChangeTellsTheRequesterTheNewStatusInWords() {
        ticket.moveTo(HelpdeskTicketStatus.IN_PROGRESS, Instant.parse("2026-10-25T09:00:00Z"));
        notifications.statusChanged(ticket, 1L);

        NotificationMessage message = sentTo(70);
        assertThat(message.type()).isEqualTo(NotificationType.HELPDESK_STATUS_CHANGED);
        assertThat(message.message()).endsWith("is now in progress.");
    }

    @Test
    void anAssignmentTellsTheAssigneeAndUnassigningTellsNobody() {
        ticket.assignTo(HelpdeskFixtures.account(2, "facultyadmin1", RoleName.ADMIN));
        notifications.assigned(ticket, 1L);
        assertThat(sentTo(2).type()).isEqualTo(NotificationType.HELPDESK_ASSIGNED);

        ticket.assignTo(null);
        notifications.assigned(ticket, 1L);
        verify(service, never()).notifyUser(any(), eq(1L));
        verify(service).notifyUser(any(), anyLong());
    }
}
