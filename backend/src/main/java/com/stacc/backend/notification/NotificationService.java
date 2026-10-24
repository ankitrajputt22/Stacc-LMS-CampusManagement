package com.stacc.backend.notification;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.stacc.backend.auth.account.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates notifications for trusted backend code only: feature services call it when a real change happens, in the
 * same transaction as that change, so a change that rolls back leaves no notification behind. There is no endpoint
 * that reaches it. Recipients are account IDs the feature found on the server, never IDs a client sent.
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notifications;
    private final NotificationRecipientRepository recipients;
    private final UserAccountRepository accounts;

    public NotificationService(NotificationRepository notifications, NotificationRecipientRepository recipients,
            UserAccountRepository accounts) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.accounts = accounts;
    }

    /** Notifies one account. Nothing is created without an account. */
    public Optional<Notification> notifyUser(NotificationMessage message, Long userAccountId) {
        return notifyUsers(message, userAccountId == null ? List.of() : List.of(userAccountId));
    }

    /**
     * Notifies several accounts with one notification. Repeated and missing IDs are dropped, so an account reached
     * through several roles still gets it once; the unique constraint backs this up. With nobody left, nothing is
     * created at all.
     */
    public Optional<Notification> notifyUsers(NotificationMessage message, Collection<Long> userAccountIds) {
        Set<Long> unique = new LinkedHashSet<>();
        userAccountIds.stream().filter(Objects::nonNull).forEach(unique::add);
        if (unique.isEmpty()) {
            return Optional.empty();
        }
        Notification notification = notifications.save(new Notification(message.type(), message.title(),
                message.message(), message.sourceType(), message.sourceId(), message.actionPath()));
        // References only: the accounts themselves are never loaded or changed.
        recipients.saveAll(unique.stream()
                .map(accountId -> new NotificationRecipient(notification, accounts.getReferenceById(accountId)))
                .toList());
        return Optional.of(notification);
    }
}
