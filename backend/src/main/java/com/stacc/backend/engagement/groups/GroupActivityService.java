package com.stacc.backend.engagement.groups;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessions and announcements of clubs and sports groups (v0.11.2). The group's officers and the campus office write
 * them; active members, officers, and the campus office read them; anyone else gets the same 404 as for a missing
 * group. Scheduled sessions appear on members' calendars; an announcement tells every active member except its author.
 */
@Service
@Transactional
public class GroupActivityService {

    static final String GROUP_NOT_FOUND = "Group not found.";
    static final String SESSION_NOT_FOUND = "Session not found.";

    public record SessionView(Long sessionId, Long groupId, String groupName, GroupSession.Kind kind, String title, String location,
            Instant startsAt, Instant endsAt, GroupSession.Status status) {

        static SessionView of(GroupSession session) {
            return new SessionView(session.getId(), session.getGroup().getId(), session.getGroup().getName(), session.getKind(),
                    session.getTitle(), session.getLocation(), session.getStartsAt(), session.getEndsAt(), session.getStatus());
        }
    }

    public record AnnouncementView(Long announcementId, String title, String message, Instant createdAt) {

        static AnnouncementView of(GroupAnnouncement announcement) {
            return new AnnouncementView(announcement.getId(), announcement.getTitle(), announcement.getMessage(), announcement.getCreatedAt());
        }
    }

    public record Activity(boolean canManage, List<SessionView> sessions, List<AnnouncementView> announcements) {
    }

    private final CampusGroupRepository groups;
    private final CampusGroupMembershipRepository memberships;
    private final CampusGroupOfficerRepository officers;
    private final GroupSessionRepository sessions;
    private final GroupAnnouncementRepository announcements;
    private final NotificationService notifications;
    private final Clock clock;

    public GroupActivityService(CampusGroupRepository groups, CampusGroupMembershipRepository memberships,
            CampusGroupOfficerRepository officers, GroupSessionRepository sessions, GroupAnnouncementRepository announcements,
            NotificationService notifications, Clock clock) {
        this.groups = groups;
        this.memberships = memberships;
        this.officers = officers;
        this.sessions = sessions;
        this.announcements = announcements;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** The group's sessions from yesterday on and its latest announcements, for members, officers, and the office. */
    @Transactional(readOnly = true)
    public Activity activity(Long accountId, boolean office, Long groupId) {
        boolean manager = office || (groupId != null && officers.isOfficer(groupId, accountId));
        if (!manager && !isActiveMember(accountId, groupId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, GROUP_NOT_FOUND);
        }
        return new Activity(manager,
                sessions.findForGroupFrom(groupId, now().minus(Duration.ofDays(1))).stream().map(SessionView::of).toList(),
                announcements.findRecent(groupId, PageRequest.of(0, 20)).stream().map(AnnouncementView::of).toList());
    }

    public SessionView addSession(Long accountId, boolean office, Long groupId, GroupSession.Kind kind, String title, String location,
            Instant startsAt, Instant endsAt) {
        CampusGroup group = managed(accountId, office, groupId);
        return SessionView.of(sessions.saveAndFlush(valid(() -> new GroupSession(group, kind, title, location, startsAt, endsAt, accountId, now()))));
    }

    public SessionView cancelSession(Long accountId, boolean office, Long sessionId) {
        GroupSession session = (sessionId == null ? Optional.<GroupSession>empty() : sessions.findByIdForUpdate(sessionId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SESSION_NOT_FOUND));
        managed(accountId, office, session.getGroup().getId());
        session.cancel();
        return SessionView.of(session);
    }

    /** Posts an announcement and tells every active member except its author. */
    public AnnouncementView announce(Long accountId, boolean office, Long groupId, String title, String message) {
        CampusGroup group = managed(accountId, office, groupId);
        GroupAnnouncement saved = announcements.saveAndFlush(valid(() -> new GroupAnnouncement(group, title, message, accountId, now())));
        List<Long> members = memberships.findCurrentForGroup(groupId).stream()
                .filter(membership -> membership.getStatus() == CampusGroupMembership.Status.ACTIVE)
                .map(membership -> membership.getAccount().getId())
                .filter(memberId -> !memberId.equals(accountId))
                .distinct().toList();
        notifications.notifyUsers(new NotificationMessage(NotificationType.GROUP_ANNOUNCEMENT, group.getName() + ": " + saved.getTitle(),
                saved.getMessage().length() > 500 ? saved.getMessage().substring(0, 499) + "…" : saved.getMessage(),
                NotificationSourceType.CAMPUS_GROUP, group.getId(), "/campus/" + (group.getKind() == CampusGroup.Kind.SPORT ? "sports" : "clubs")),
                members);
        return AnnouncementView.of(saved);
    }

    /** Scheduled sessions of the groups the account is an active member or officer of, overlapping the range. */
    @Transactional(readOnly = true)
    public List<SessionView> mySessions(Long accountId, Instant from, Instant to) {
        List<Long> groupIds = memberships.findCurrentForAccount(accountId).stream()
                .filter(membership -> membership.getStatus() == CampusGroupMembership.Status.ACTIVE)
                .map(membership -> membership.getGroup().getId()).distinct().toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return sessions.findScheduledBetween(groupIds, from, to).stream().map(SessionView::of).toList();
    }

    private boolean isActiveMember(Long accountId, Long groupId) {
        return groupId != null && memberships.findCurrentForAccount(accountId).stream()
                .anyMatch(membership -> membership.getGroup().getId().equals(groupId)
                        && membership.getStatus() == CampusGroupMembership.Status.ACTIVE);
    }

    private CampusGroup managed(Long accountId, boolean office, Long groupId) {
        if (groupId == null || !(office || officers.isOfficer(groupId, accountId))) {
            throw new ApiException(HttpStatus.NOT_FOUND, GROUP_NOT_FOUND);
        }
        return groups.findById(groupId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, GROUP_NOT_FOUND));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static <T> T valid(Supplier<T> make) {
        try {
            return make.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }
}
