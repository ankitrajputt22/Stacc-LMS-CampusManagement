package com.stacc.backend.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email delivery of notifications. Off unless enabled; then a sender address is required. Links in emails start with
 * the link base URL (the frontend's own address). The mail server itself is set with the spring.mail settings.
 */
@ConfigurationProperties(prefix = "stacc.mail")
public record MailProperties(boolean enabled, String from, String linkBaseUrl, String digestCron) {

    public MailProperties {
        if (enabled && (from == null || from.isBlank())) {
            throw new IllegalArgumentException("MAIL_ENABLED is true, so MAIL_FROM must be set.");
        }
        if (linkBaseUrl == null || !(linkBaseUrl.startsWith("https://") || linkBaseUrl.startsWith("http://"))) {
            throw new IllegalArgumentException("MAIL_LINK_BASE_URL must be an http or https address.");
        }
        linkBaseUrl = linkBaseUrl.replaceAll("/+$", "");
    }
}
