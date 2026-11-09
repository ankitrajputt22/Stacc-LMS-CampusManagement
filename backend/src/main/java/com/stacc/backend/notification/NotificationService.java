package com.stacc.backend.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final NotificationPreferenceRepository preferences;
    private final NotificationDigestItemRepository digestItems;
    private final NotificationMailer mailer;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, NotificationRecipientRepository recipients,
            UserAccountRepository accounts, NotificationPreferenceRepository preferences,
            NotificationDigestItemRepository digestItems, NotificationMailer mailer, Clock clock) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.accounts = accounts;
        this.preferences = preferences;
        this.digestItems = digestItems;
        this.mailer = mailer;
        this.clock = clock;
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
        // Each person's choice for this category; without a row, the Inbox is on and nothing is emailed.
        Map<Long, NotificationPreference> chosen = preferences
                .findAllByCategoryAndUserAccountIdIn(NotificationCategory.of(message.type()), unique).stream()
                .collect(Collectors.toMap(NotificationPreference::getUserAccountId, Function.identity()));
        List<Long> inbox = new ArrayList<>();
        List<Long> instant = new ArrayList<>();
        List<Long> daily = new ArrayList<>();
        for (Long accountId : unique) {
            NotificationPreference preference = chosen.get(accountId);
            if (preference == null || preference.isInApp()) {
                inbox.add(accountId);
            }
            EmailDelivery email = preference == null ? EmailDelivery.OFF : preference.getEmail();
            if (email == EmailDelivery.INSTANT) {
                instant.add(accountId);
            } else if (email == EmailDelivery.DAILY && mailer.enabled()) {
                daily.add(accountId);
            }
        }
        // References only: the accounts themselves are never loaded or changed.
        recipients.saveAll(inbox.stream()
                .map(accountId -> new NotificationRecipient(notification, accounts.getReferenceById(accountId)))
                .toList());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        digestItems.saveAll(daily.stream().map(accountId -> new NotificationDigestItem(accountId, notification, now)).toList());
        mailer.sendAfterCommit(mailer.mailOf(notification), instant);
        return Optional.of(notification);
    }
}
