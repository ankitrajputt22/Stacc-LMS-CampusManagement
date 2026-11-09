package com.stacc.backend.hostel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HostelNotificationsTest {

    private final NotificationService service = mock(NotificationService.class);
    private final HostelNotifications notifications = new HostelNotifications(service);
    private final UserAccount studentAccount = HostelFixtures.account(70, "student1", RoleName.STUDENT);
    private final UserAccount admin = HostelFixtures.account(1, "admin1", RoleName.ADMIN);

    private HostelAllocation allocation() {
        HostelApplication application = HostelFixtures.application(42, HostelFixtures.student(5, studentAccount),
                HostelFixtures.session(2, "2026-27", 2026), HostelFixtures.approved(12, studentAccount, admin));
        Hostel hostel = new Hostel("MAIN", "Main Hostel", null);
        HostelBed bed = HostelFixtures.bed(3, HostelFixtures.room(2, hostel, "A-101"), "B1");
        return HostelFixtures.withId(new HostelAllocation(application, bed, admin, HostelFixtures.NOW), 77);
    }

    @Test
    void theStudentIsToldWhereTheyWereAllocatedWithALinkToTheHostelPage() {
        notifications.allocated(allocation());

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(service).notifyUser(message.capture(), eq(70L));
        assertThat(message.getValue().type()).isEqualTo(NotificationType.HOSTEL_ALLOCATED);
        assertThat(message.getValue().message()).isEqualTo("Your hostel allocation is ready: Main Hostel, Room A-101, Bed B1.");
        assertThat(message.getValue().sourceType()).isEqualTo(NotificationSourceType.HOSTEL_ALLOCATION);
        assertThat(message.getValue().sourceId()).isEqualTo(77L);
        assertThat(message.getValue().actionPath()).isEqualTo("/hostel");
    }

    @Test
    void theStudentIsToldWhenTheAllocationEnds() {
        notifications.vacated(allocation());

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(service).notifyUser(message.capture(), eq(70L));
        assertThat(message.getValue().type()).isEqualTo(NotificationType.HOSTEL_VACATED);
        assertThat(message.getValue().actionPath()).isEqualTo("/hostel");
    }
}
