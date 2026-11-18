package com.stacc.backend.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
