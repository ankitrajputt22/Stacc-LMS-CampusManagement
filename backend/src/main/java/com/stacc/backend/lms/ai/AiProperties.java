package com.stacc.backend.lms.ai;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "stacc.ai")
public record AiProperties(
        boolean enabled,
        String serviceUrl,
        String internalToken,
        Duration connectTimeout,
        Duration readTimeout,
        Duration syncReadTimeout,
        int maxSyncSources,
        DataSize maxSyncBytes,
        Duration buildingTimeout) {

    public static final int MIN_TOKEN_LENGTH = 32;

    public AiProperties {
        connectTimeout = positive(connectTimeout, Duration.ofSeconds(3));
        readTimeout = positive(readTimeout, Duration.ofSeconds(120));
        syncReadTimeout = positive(syncReadTimeout, Duration.ofMinutes(15));
        buildingTimeout = positive(buildingTimeout, Duration.ofMinutes(30));
        maxSyncSources = maxSyncSources <= 0 ? 100 : maxSyncSources;
        maxSyncBytes = maxSyncBytes == null || maxSyncBytes.toBytes() <= 0 ? DataSize.ofMegabytes(40) : maxSyncBytes;
        serviceUrl = serviceUrl == null ? "" : serviceUrl.strip();
        internalToken = internalToken == null ? "" : internalToken.strip();
    }

    public boolean usable() {
        return enabled && internalToken.length() >= MIN_TOKEN_LENGTH && validUrl();
    }

    private boolean validUrl() {
        try {
            URI uri = URI.create(serviceUrl);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }

    @Override
    public String toString() {
        return "AiProperties[enabled=" + enabled + ", serviceUrl=" + serviceUrl + "]";
    }
}
