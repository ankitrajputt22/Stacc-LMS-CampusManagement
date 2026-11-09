package com.stacc.backend.campus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.campus.notices.CampusNotice;
import com.stacc.backend.campus.services.CampusServiceRequest;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CampusNotificationsTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final CampusNotifications campus = new CampusNotifications(notifications, accounts);

    @SuppressWarnings("unchecked")
    @Test
    void aPublishedNoticeReachesTheActiveAccountsOfItsRolesOnceEach() {
        CampusNotice notice = CampusFixtures.notice(5, true, true, false);
        // The query returns each account once, however many of the roles it holds.
        when(accounts.findDistinctIdsByRoleNamesAndStatus(any(), eq(AccountStatus.ACTIVE))).thenReturn(List.of(70L, 80L, 90L));

        campus.noticePublished(notice);

        ArgumentCaptor<Collection<RoleName>> roles = ArgumentCaptor.forClass(Collection.class);
        verify(accounts).findDistinctIdsByRoleNamesAndStatus(roles.capture(), eq(AccountStatus.ACTIVE));
        assertThat(roles.getValue()).containsExactlyInAnyOrder(RoleName.STUDENT, RoleName.FACULTY);
        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notifications).notifyUsers(message.capture(), eq(List.of(70L, 80L, 90L)));
        assertThat(message.getValue().type()).isEqualTo(NotificationType.CAMPUS_NOTICE_PUBLISHED);
        assertThat(message.getValue().message()).isEqualTo("Notice 5");
        assertThat(message.getValue().sourceType()).isEqualTo(NotificationSourceType.CAMPUS_NOTICE);
        assertThat(message.getValue().sourceId()).isEqualTo(5L);
        assertThat(message.getValue().actionPath()).isEqualTo("/campus");
    }

    @SuppressWarnings("unchecked")
    @Test
    void anAdminOnlyNoticeAsksForAdminsOnly() {
        campus.noticePublished(CampusFixtures.notice(6, false, false, true));

        ArgumentCaptor<Collection<RoleName>> roles = ArgumentCaptor.forClass(Collection.class);
        verify(accounts).findDistinctIdsByRoleNamesAndStatus(roles.capture(), eq(AccountStatus.ACTIVE));
        assertThat(roles.getValue()).containsExactly(RoleName.ADMIN);
    }

    @Test
    void aCompletedRequestTellsItsRequesterWithALinkToTheRequest() {
        UserAccount student = CampusFixtures.account(70, "student1", RoleName.STUDENT);
        CampusServiceRequest request = CampusFixtures.request(43,
                CampusFixtures.service(2, "GENERAL_ADMIN", true, true, null), student, null);

        campus.serviceCompleted(request);

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notifications).notifyUser(message.capture(), eq(70L));
        assertThat(message.getValue().type()).isEqualTo(NotificationType.CAMPUS_SERVICE_COMPLETED);
        assertThat(message.getValue().message()).contains("CSR-000043");
        assertThat(message.getValue().sourceType()).isEqualTo(NotificationSourceType.CAMPUS_SERVICE_REQUEST);
        assertThat(message.getValue().actionPath()).isEqualTo("/campus/requests/43");
    }
}
