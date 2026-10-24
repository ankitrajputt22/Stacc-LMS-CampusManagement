package com.stacc.backend.notification.api;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.notification.NotificationRecipient;
import com.stacc.backend.notification.NotificationRecipientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in account's Inbox: its own notifications, page by page, its unread count, and its read state. Every
 * method takes the account from the access token and only ever touches that account's rows; another account's row
 * answers the same 404 as one that does not exist.
 */
@Service
@Transactional
public class NotificationInboxService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    static final String NOT_FOUND = "Notification not found.";
    static final String BAD_PAGE = "The page must be 0 or more.";
    static final String BAD_SIZE = "The page size must be between 1 and " + MAX_PAGE_SIZE + ".";

    private final NotificationRecipientRepository recipients;
    private final Clock clock;

    public NotificationInboxService(NotificationRecipientRepository recipients, Clock clock) {
        this.recipients = recipients;
        this.clock = clock;
    }

    /** One page of the Inbox, newest first; with {@code unreadOnly}, unread notifications only. */
    @Transactional(readOnly = true)
    public NotificationPageResponse inbox(Long userAccountId, int page, int size, boolean unreadOnly) {
        if (page < 0) {
            throw badRequest(BAD_PAGE);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest(BAD_SIZE);
        }
        Page<NotificationRecipient> rows = recipients.findInbox(userAccountId, unreadOnly, PageRequest.of(page, size));
        return new NotificationPageResponse(rows.getContent().stream().map(NotificationInboxItem::of).toList(),
                page, size, rows.getTotalElements(), rows.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCount unreadCount(Long userAccountId) {
        return new UnreadNotificationCount(recipients.countByUserAccountIdAndReadAtIsNull(userAccountId));
    }

    /** Marks one of the account's notifications read now. Already read keeps its first read time. */
    public NotificationInboxItem markRead(Long userAccountId, Long recipientId) {
        NotificationRecipient recipient = own(userAccountId, recipientId);
        recipient.markRead(clock.instant());
        return NotificationInboxItem.of(recipients.saveAndFlush(recipient));
    }

    /** Marks one of the account's notifications unread again. Already unread changes nothing. */
    public NotificationInboxItem markUnread(Long userAccountId, Long recipientId) {
        NotificationRecipient recipient = own(userAccountId, recipientId);
        recipient.markUnread();
        return NotificationInboxItem.of(recipients.saveAndFlush(recipient));
    }

    /** Marks every unread notification of the account read, in one statement. Other accounts are never touched. */
    public NotificationReadAllResponse markAllRead(Long userAccountId) {
        int marked = recipients.markAllRead(userAccountId, clock.instant().truncatedTo(ChronoUnit.MICROS));
        return new NotificationReadAllResponse(marked, 0);
    }

    private NotificationRecipient own(Long userAccountId, Long recipientId) {
        if (userAccountId == null || recipientId == null) {
            throw notFound();
        }
        return recipients.findOwn(recipientId, userAccountId).orElseThrow(NotificationInboxService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
