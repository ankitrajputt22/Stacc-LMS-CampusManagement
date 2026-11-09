package com.stacc.backend.notification;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.identity.person.PersonProfile;
import com.stacc.backend.identity.person.PersonProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends notification emails in plain text to the college-recorded address on each person's profile, only for active
 * accounts and only when mail is enabled. Instant emails go after the change has committed, so a rolled-back change
 * never emails anyone. A failure is logged without any address or content and never affects the change.
 */
@Component
@EnableConfigurationProperties(MailProperties.class)
public class NotificationMailer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationMailer.class);

    /** What an email says: a subject and plain-text lines. */
    public record Mail(String subject, String body) {
    }

    private final MailProperties properties;
    private final ObjectProvider<JavaMailSender> sender;
    private final PersonProfileRepository profiles;

    public NotificationMailer(MailProperties properties, ObjectProvider<JavaMailSender> sender, PersonProfileRepository profiles) {
        this.properties = properties;
        this.sender = sender;
        this.profiles = profiles;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /** One notification as an email. */
    public Mail mailOf(Notification notification) {
        StringBuilder body = new StringBuilder(notification.getMessage()).append("\n");
        if (notification.getActionPath() != null) {
            body.append("\nOpen it in Stacc: ").append(properties.linkBaseUrl()).append(notification.getActionPath()).append("\n");
        }
        body.append("\nYou can change which emails you get in Stacc under Inbox, Notification settings.\n");
        return new Mail("Stacc: " + notification.getTitle(), body.toString());
    }

    public String linkBaseUrl() {
        return properties.linkBaseUrl();
    }

    /** Sends the same email to each account once the current transaction has committed. */
    public void sendAfterCommit(Mail mail, Collection<Long> accountIds) {
        if (!properties.enabled() || accountIds.isEmpty()) {
            return;
        }
        List<Long> ids = List.copyOf(accountIds);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(mail, ids);
                }
            });
        } else {
            send(mail, ids);
        }
    }

    /** Sends one email per account right now; each account gets its own. */
    public void send(Mail mail, Collection<Long> accountIds) {
        if (!properties.enabled()) {
            return;
        }
        JavaMailSender mailSender = sender.getIfAvailable();
        if (mailSender == null) {
            LOGGER.warn("Email is enabled but no mail server is configured");
            return;
        }
        Map<Long, String> addresses = addressesOf(accountIds);
        int failed = 0;
        for (String address : addresses.values()) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.from());
            message.setTo(address);
            message.setSubject(mail.subject());
            message.setText(mail.body());
            try {
                mailSender.send(message);
            } catch (MailException failure) {
                failed++;
            }
        }
        if (failed > 0) {
            LOGGER.warn("{} notification email(s) could not be sent", failed);
        }
    }

    // Only active accounts with a recorded address. Addresses are never logged.
    private Map<Long, String> addressesOf(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return profiles.findAllByUserAccountIdIn(accountIds).stream()
                .filter(profile -> profile.getEmail() != null && !profile.getEmail().isBlank())
                .filter(profile -> profile.getUserAccount().getStatus() == AccountStatus.ACTIVE)
                .collect(Collectors.toMap(profile -> profile.getUserAccount().getId(), PersonProfile::getEmail, (a, b) -> a));
    }
}
