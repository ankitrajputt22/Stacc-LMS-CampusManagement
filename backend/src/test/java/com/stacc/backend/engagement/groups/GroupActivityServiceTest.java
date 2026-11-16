package com.stacc.backend.engagement.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Only officers and the office write; outsiders get 404; an announcement tells active members, never its author. */
class GroupActivityServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    private final CampusGroupRepository groups = mock(CampusGroupRepository.class);
    private final CampusGroupMembershipRepository memberships = mock(CampusGroupMembershipRepository.class);
    private final CampusGroupOfficerRepository officers = mock(CampusGroupOfficerRepository.class);
    private final GroupSessionRepository sessions = mock(GroupSessionRepository.class);
    private final GroupAnnouncementRepository announcements = mock(GroupAnnouncementRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final GroupActivityService service = new GroupActivityService(groups, memberships, officers, sessions, announcements,
            notifications, Clock.fixed(NOW, ZoneOffset.UTC));

    private static UserAccount account(long id) {
        UserAccount account = new UserAccount("user" + id, "hash");
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    @Test
    void anOfficersAnnouncementReachesActiveMembersButNotItsAuthor() {
        CampusGroup group = mock(CampusGroup.class);
        when(group.getName()).thenReturn("Robotics Club");
        when(group.getKind()).thenReturn(CampusGroup.Kind.CLUB);
        when(group.getId()).thenReturn(5L);
        when(officers.isOfficer(5L, 1L)).thenReturn(true);
        when(groups.findById(5L)).thenReturn(Optional.of(group));
        when(announcements.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        CampusGroupMembership author = mock(CampusGroupMembership.class);
        when(author.getStatus()).thenReturn(CampusGroupMembership.Status.ACTIVE);
        when(author.getAccount()).thenReturn(account(1));
        CampusGroupMembership member = mock(CampusGroupMembership.class);
        when(member.getStatus()).thenReturn(CampusGroupMembership.Status.ACTIVE);
        when(member.getAccount()).thenReturn(account(2));
        CampusGroupMembership pending = mock(CampusGroupMembership.class);
        when(pending.getStatus()).thenReturn(CampusGroupMembership.Status.PENDING);
        when(pending.getAccount()).thenReturn(account(3));
        when(memberships.findCurrentForGroup(5L)).thenReturn(List.of(author, member, pending));

        service.announce(1L, false, 5L, "Meeting moved", "Room 204 this week.");

        verify(notifications).notifyUsers(any(), argThat(accounts -> accounts.size() == 1 && accounts.contains(2L)));
    }

    @Test
    void outsidersCannotReadOrWrite() {
        when(memberships.findCurrentForAccount(9L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.activity(9L, false, 5L)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.addSession(9L, false, 5L, GroupSession.Kind.MEETING, "Kickoff", null, NOW, NOW.plusSeconds(3600)))
                .isInstanceOf(ApiException.class);
        assertThat(service.mySessions(9L, NOW, NOW.plusSeconds(86_400))).isEmpty();
        assertThatThrownBy(() -> new GroupSession(mock(CampusGroup.class), GroupSession.Kind.PRACTICE, "Nets", null, NOW, NOW, 1L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
