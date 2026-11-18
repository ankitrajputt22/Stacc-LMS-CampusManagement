package com.stacc.backend.auth.session;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stacc.session")
public record SessionProperties(Duration maxDuration, Duration idleTimeout, boolean secureCookie) {

    public static final String COOKIE_NAME = "STACC_SESSION";
    public static final String COOKIE_PATH = "/api";

    public SessionProperties {
        if (maxDuration == null || maxDuration.compareTo(Duration.ofMinutes(15)) < 0
                || maxDuration.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("STACC_SESSION_MAX_DURATION must be between 15m and 24h");
        }
        if (idleTimeout == null || idleTimeout.compareTo(Duration.ofMinutes(5)) < 0 || idleTimeout.compareTo(maxDuration) > 0) {
            throw new IllegalArgumentException(
                    "STACC_SESSION_IDLE_TIMEOUT must be at least 5m and no longer than STACC_SESSION_MAX_DURATION");
        }
    }
}
