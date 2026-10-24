package com.stacc.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationServiceTest {

    private static final NotificationMessage MESSAGE = new NotificationMessage(NotificationType.ACADEMIC_CALENDAR_PUBLISHED,
            "Academic calendar updated", "Autumn break was added to the academic calendar.",
            NotificationSourceType.ACADEMIC_CALENDAR_EVENT, 14L, "/calendar");

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationRecipientRepository recipients = mock(NotificationRecipientRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final NotificationService service = new NotificationService(notifications, recipients, accounts);

    @SuppressWarnings("unchecked")
    private List<NotificationRecipient> savedRecipients() {
        ArgumentCaptor<Iterable<NotificationRecipient>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(recipients).saveAll(saved.capture());
        return (List<NotificationRecipient>) saved.getValue();
    }

    private void accountsExist() {
        when(notifications.save(any())).thenAnswer(call -> call.getArgument(0));
        when(accounts.getReferenceById(anyLong())).thenAnswer(call -> {
            UserAccount account = new UserAccount("ACC-" + call.getArgument(0), "hash");
            ReflectionTestUtils.setField(account, "id", call.getArgument(0));
            return account;
        });
    }

    @Test
    void oneNotificationReachesEachAccountOnceWhateverRepeatsTheCallerSent() {
        accountsExist();

        Notification created = service.notifyUsers(MESSAGE, Arrays.asList(5L, 7L, 5L, null, 7L, 9L)).orElseThrow();

        assertThat(created.getType()).isEqualTo(NotificationType.ACADEMIC_CALENDAR_PUBLISHED);
        assertThat(created.getActionPath()).isEqualTo("/calendar");
        assertThat(savedRecipients()).extracting(recipient -> recipient.getUserAccount().getId()).containsExactly(5L, 7L, 9L);
        assertThat(savedRecipients()).allSatisfy(recipient -> {
            assertThat(recipient.getNotification()).isSameAs(created);
            assertThat(recipient.getReadAt()).isNull();
        });
    }

    @Test
    void withNobodyToTellNothingIsCreated() {
        assertThat(service.notifyUsers(MESSAGE, List.of())).isEmpty();
        assertThat(service.notifyUser(MESSAGE, null)).isEmpty();

        verifyNoInteractions(notifications, recipients, accounts);
    }

    @Test
    void oneAccountGetsOneRow() {
        accountsExist();

        service.notifyUser(MESSAGE, 5L);

        assertThat(savedRecipients()).hasSize(1);
    }
}
