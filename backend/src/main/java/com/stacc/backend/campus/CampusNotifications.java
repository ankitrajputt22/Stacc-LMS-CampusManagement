package com.stacc.backend.campus;

import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.campus.notices.CampusNotice;
import com.stacc.backend.campus.services.CampusServiceRequest;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.stereotype.Component;

@Component
public class CampusNotifications {

    static final String CAMPUS_PATH = "/campus";

    private final NotificationService notifications;
    private final UserAccountRepository accounts;

    public CampusNotifications(NotificationService notifications, UserAccountRepository accounts) {
        this.notifications = notifications;
        this.accounts = accounts;
    }

    public void noticePublished(CampusNotice notice) {
        List<RoleName> roles = new ArrayList<>();
        if (notice.isVisibleToStudent()) {
            roles.add(RoleName.STUDENT);
        }
        if (notice.isVisibleToFaculty()) {
            roles.add(RoleName.FACULTY);
        }
        if (notice.isVisibleToAdmin()) {
            roles.add(RoleName.ADMIN);
        }
        if (roles.isEmpty()) {
            return;
        }
        List<Long> recipients = accounts.findDistinctIdsByRoleNamesAndStatus(roles, AccountStatus.ACTIVE);
        notifications.notifyUsers(new NotificationMessage(NotificationType.CAMPUS_NOTICE_PUBLISHED,
                "New campus notice", notice.getTitle(), NotificationSourceType.CAMPUS_NOTICE, notice.getId(), CAMPUS_PATH),
                recipients);
    }

    public void serviceReady(CampusServiceRequest request) {
        notifications.notifyUser(new NotificationMessage(NotificationType.CAMPUS_SERVICE_READY,
                "Service request ready",
                "Your request " + request.getRequestNumber() + " (" + request.getServiceDefinition().getName()
                        + ") is ready.",
                NotificationSourceType.CAMPUS_SERVICE_REQUEST, request.getId(), "/campus/requests/" + request.getId()),
                request.getRequester().getId());
    }

    public void serviceCompleted(CampusServiceRequest request) {
        notifications.notifyUser(new NotificationMessage(NotificationType.CAMPUS_SERVICE_COMPLETED,
                "Service request completed",
                "Your request " + request.getRequestNumber() + " (" + request.getServiceDefinition().getName()
                        + ") has been completed.",
                NotificationSourceType.CAMPUS_SERVICE_REQUEST, request.getId(), "/campus/requests/" + request.getId()),
                request.getRequester().getId());
    }
}
