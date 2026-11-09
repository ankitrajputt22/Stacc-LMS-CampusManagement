package com.stacc.backend.hostel;

import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.stereotype.Component;

/**
 * The Inbox notifications about hostel allocations, linking to the student's Hostel page. Approval notifications are
 * the approval engine's own and are not repeated here.
 */
@Component
public class HostelNotifications {

    static final String ACTION_PATH = "/hostel";

    private final NotificationService notifications;

    public HostelNotifications(NotificationService notifications) {
        this.notifications = notifications;
    }

    public void allocated(HostelAllocation allocation) {
        notifications.notifyUser(message(allocation, NotificationType.HOSTEL_ALLOCATED, "Hostel allocation ready",
                "Your hostel allocation is ready: " + place(allocation) + "."), studentAccountId(allocation));
    }

    public void vacated(HostelAllocation allocation) {
        notifications.notifyUser(message(allocation, NotificationType.HOSTEL_VACATED, "Hostel allocation ended",
                "Your hostel allocation has ended: " + place(allocation) + "."), studentAccountId(allocation));
    }

    private static String place(HostelAllocation allocation) {
        HostelBed bed = allocation.getBed();
        HostelRoom room = bed.getRoom();
        return room.getHostel().getName() + ", Room " + room.getRoomNumber() + ", Bed " + bed.getBedNumber();
    }

    private static Long studentAccountId(HostelAllocation allocation) {
        return allocation.getHostelApplication().getStudentProfile().getUserAccount().getId();
    }

    private static NotificationMessage message(HostelAllocation allocation, NotificationType type, String title,
            String text) {
        return new NotificationMessage(type, title, text, NotificationSourceType.HOSTEL_ALLOCATION, allocation.getId(),
                ACTION_PATH);
    }
}
