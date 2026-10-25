package com.stacc.backend.approval;

import java.util.List;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.stereotype.Component;

/**
 * The Inbox notifications about approvals, worded in one place and sent only on real transitions. There is no link:
 * approvals have no pages of their own yet, and the business module that uses a workflow adds its own.
 */
@Component
public class ApprovalNotifications {

    private final NotificationService notifications;
    private final UserAccountRepository accounts;

    public ApprovalNotifications(NotificationService notifications, UserAccountRepository accounts) {
        this.notifications = notifications;
        this.accounts = accounts;
    }

    /**
     * Tells every active account holding the step's role that a decision is needed, except the requester, who never
     * decides their own request. Accounts are told once each.
     */
    public void actionRequired(ApprovalRequest request, ApprovalRequestStep step) {
        RoleName role;
        try {
            role = RoleName.valueOf(step.getRequiredRoleCode());
        } catch (IllegalArgumentException unknown) {
            return;
        }
        Long requesterId = request.getRequester().getId();
        List<Long> approvers = accounts.findIdsByRoleNameAndStatus(role, AccountStatus.ACTIVE).stream()
                .filter(accountId -> !accountId.equals(requesterId))
                .toList();
        notifications.notifyUsers(message(request, NotificationType.APPROVAL_ACTION_REQUIRED, "Approval required",
                "An approval request requires your review: " + request.getSubject() + "."), approvers);
    }

    public void approved(ApprovalRequest request) {
        notifications.notifyUser(message(request, NotificationType.APPROVAL_APPROVED, "Approval request approved",
                "Your approval request was approved: " + request.getSubject() + "."), request.getRequester().getId());
    }

    /** The reason stays on the request; the Inbox only says that it was rejected. */
    public void rejected(ApprovalRequest request) {
        notifications.notifyUser(message(request, NotificationType.APPROVAL_REJECTED, "Approval request rejected",
                "Your approval request was rejected: " + request.getSubject() + "."), request.getRequester().getId());
    }

    /** Tells the requester, unless they cancelled it themselves. */
    public void cancelled(ApprovalRequest request, Long actorAccountId) {
        Long requesterId = request.getRequester().getId();
        if (!requesterId.equals(actorAccountId)) {
            notifications.notifyUser(message(request, NotificationType.APPROVAL_CANCELLED, "Approval request cancelled",
                    "Your approval request was cancelled: " + request.getSubject() + "."), requesterId);
        }
    }

    private static NotificationMessage message(ApprovalRequest request, NotificationType type, String title, String text) {
        return new NotificationMessage(type, title, text, NotificationSourceType.APPROVAL_REQUEST, request.getId(), null);
    }
}
