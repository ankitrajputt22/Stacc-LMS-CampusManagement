package com.stacc.backend.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.notification.Notification;
import com.stacc.backend.notification.NotificationRecipient;
import com.stacc.backend.notification.NotificationRecipientRepository;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationInboxServiceTest {

    private static final Long USER_A = 70L;
    private static final Long USER_B = 71L;
    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00.123456789Z");

    private final NotificationRecipientRepository recipients = mock(NotificationRecipientRepository.class);
    private final NotificationInboxService service =
            new NotificationInboxService(recipients, Clock.fixed(NOW, ZoneOffset.UTC));

    private static NotificationRecipient row(long id, long accountId) {
        Notification notification = new Notification(NotificationType.ASSIGNMENT_PUBLISHED, "New assignment published",
                "Linked lists was published in CS301 Data Structures.", NotificationSourceType.ASSIGNMENT, 44L,
                "/courses/12/assignments/44");
        ReflectionTestUtils.setField(notification, "id", id + 1000);
        ReflectionTestUtils.setField(notification, "createdAt", Instant.parse("2026-10-20T10:00:00Z"));
        UserAccount account = new UserAccount("ACC-" + accountId, "hash");
        ReflectionTestUtils.setField(account, "id", accountId);
        NotificationRecipient recipient = new NotificationRecipient(notification, account);
        ReflectionTestUtils.setField(recipient, "id", id);
        return recipient;
    }

    private static void assertRefused(HttpStatus status, String message, Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, refusal -> assertThat(refusal.getStatus()).isEqualTo(status))
                .hasMessage(message);
    }

    @Test
    void theInboxIsTheAccountsOwnPageNewestFirstWithoutRecipientDetails() {
        NotificationRecipient own = row(310, USER_A);
        when(recipients.findInbox(USER_A, true, PageRequest.of(1, 5)))
                .thenReturn(new PageImpl<>(List.of(own), PageRequest.of(1, 5), 6));

        NotificationPageResponse page = service.inbox(USER_A, 1, 5, true);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.totalPages()).isEqualTo(2);
        NotificationInboxItem item = page.items().get(0);
        assertThat(item.notificationRecipientId()).isEqualTo(310L);
        assertThat(item.notificationId()).isEqualTo(1310L);
        assertThat(item.type()).isEqualTo("ASSIGNMENT_PUBLISHED");
        assertThat(item.sourceType()).isEqualTo("ASSIGNMENT");
        assertThat(item.actionPath()).isEqualTo("/courses/12/assignments/44");
        assertThat(item.readAt()).isNull();
    }

    @Test
    void pageAndSizeMustBeSensible() {
        assertRefused(HttpStatus.BAD_REQUEST, NotificationInboxService.BAD_PAGE, () -> service.inbox(USER_A, -1, 20, false));
        assertRefused(HttpStatus.BAD_REQUEST, NotificationInboxService.BAD_SIZE, () -> service.inbox(USER_A, 0, 0, false));
        assertRefused(HttpStatus.BAD_REQUEST, NotificationInboxService.BAD_SIZE, () -> service.inbox(USER_A, 0, 101, false));
        verifyNoInteractions(recipients);
    }

    @Test
    void readAndUnreadAreIdempotentAndKeepTheFirstReadTime() {
        NotificationRecipient own = row(310, USER_A);
        when(recipients.findOwn(310L, USER_A)).thenReturn(Optional.of(own));
        when(recipients.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.markRead(USER_A, 310L).readAt()).isEqualTo(Instant.parse("2026-10-21T08:00:00.123456Z"));
        NotificationInboxService later = new NotificationInboxService(recipients,
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
        assertThat(later.markRead(USER_A, 310L).readAt()).isEqualTo(Instant.parse("2026-10-21T08:00:00.123456Z"));

        assertThat(service.markUnread(USER_A, 310L).readAt()).isNull();
        assertThat(service.markUnread(USER_A, 310L).readAt()).isNull();
    }

    @Test
    void anotherAccountsRowLooksMissing() {
        when(recipients.findOwn(310L, USER_B)).thenReturn(Optional.empty());

        assertRefused(HttpStatus.NOT_FOUND, NotificationInboxService.NOT_FOUND, () -> service.markRead(USER_B, 310L));
        assertRefused(HttpStatus.NOT_FOUND, NotificationInboxService.NOT_FOUND, () -> service.markUnread(USER_B, 310L));
        assertRefused(HttpStatus.NOT_FOUND, NotificationInboxService.NOT_FOUND, () -> service.markRead(USER_B, null));
    }

    @Test
    void unreadCountAndReadAllConcernOnlyTheAccount() {
        when(recipients.countByUserAccountIdAndReadAtIsNull(USER_A)).thenReturn(3L);
        when(recipients.markAllRead(USER_A, Instant.parse("2026-10-21T08:00:00.123456Z"))).thenReturn(3);

        assertThat(service.unreadCount(USER_A).unreadCount()).isEqualTo(3);
        NotificationReadAllResponse done = service.markAllRead(USER_A);

        assertThat(done.markedRead()).isEqualTo(3);
        assertThat(done.unreadCount()).isZero();
        verify(recipients).markAllRead(USER_A, Instant.parse("2026-10-21T08:00:00.123456Z"));
    }
}
