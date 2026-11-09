package com.stacc.backend.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The daily digest, only when email is on: every waiting item is marked sent and its account gets one email listing
 * them. Items are taken under a row lock and marked in the same transaction, so a digest is sent at most once; the
 * emails go out after that commits.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "stacc.mail", name = "enabled", havingValue = "true")
public class NotificationDigestJob {

    private static final int BATCH = 2000;

    private final NotificationDigestItemRepository items;
    private final NotificationMailer mailer;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public NotificationDigestJob(NotificationDigestItemRepository items, NotificationMailer mailer, TransactionTemplate transactions, Clock clock) {
        this.items = items;
        this.mailer = mailer;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(cron = "${stacc.mail.digest-cron}", zone = "UTC")
    public void sendDigests() {
        Map<Long, NotificationMailer.Mail> mails = transactions.execute(status -> collect());
        if (mails != null) {
            mails.forEach((accountId, mail) -> mailer.send(mail, List.of(accountId)));
        }
    }

    private Map<Long, NotificationMailer.Mail> collect() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Map<Long, StringBuilder> bodies = new LinkedHashMap<>();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (NotificationDigestItem item : items.findPendingForUpdate(PageRequest.of(0, BATCH))) {
            Notification notification = item.getNotification();
            StringBuilder body = bodies.computeIfAbsent(item.getUserAccountId(), id -> new StringBuilder("Your Stacc updates:\n"));
            body.append("\n- ").append(notification.getTitle()).append("\n  ").append(notification.getMessage()).append("\n");
            if (notification.getActionPath() != null) {
                body.append("  ").append(mailer.linkBaseUrl()).append(notification.getActionPath()).append("\n");
            }
            counts.merge(item.getUserAccountId(), 1, Integer::sum);
            item.sent(now);
        }
        Map<Long, NotificationMailer.Mail> mails = new LinkedHashMap<>();
        bodies.forEach((accountId, body) -> mails.put(accountId, new NotificationMailer.Mail(
                "Stacc daily digest: " + counts.get(accountId) + (counts.get(accountId) == 1 ? " update" : " updates"),
                body.append("\nYou can change which emails you get in Stacc under Inbox, Notification settings.\n").toString())));
        return mails;
    }
}
