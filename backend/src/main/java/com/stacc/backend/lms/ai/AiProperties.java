package com.stacc.backend.lms.ai;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * How the backend reaches the internal AI service. AI is off unless {@code AI_ENABLED=true}; then the service address
 * and the shared internal token must be set. The token is never logged or sent anywhere but the AI service.
 *
 * @param enabled         whether AI learning features are switched on
 * @param serviceUrl      the AI service's internal address, such as http://ai-service:8000 in Docker
 * @param internalToken   the secret the AI service expects in {@code X-Stacc-AI-Token}
 * @param connectTimeout  how long to wait for a connection
 * @param readTimeout     how long to wait for an answer, summary, practice set, or assessment draft
 * @param syncReadTimeout how long to wait for a knowledge refresh, which extracts and embeds every source
 * @param maxSyncSources  the most published sources sent in one refresh; the rest are skipped and counted
 * @param maxSyncBytes    the most study-material bytes read and sent in one refresh
 * @param buildingTimeout after this long, a refresh still marked as building is treated as abandoned
 */
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

    /** The AI service refuses shorter tokens, so a shorter one means AI is not usable. */
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

    /** Switched on and fully configured. When false, every AI route answers 503 and nothing calls the AI service. */
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

    // The default text form of a record would include the token.
    @Override
    public String toString() {
        return "AiProperties[enabled=" + enabled + ", serviceUrl=" + serviceUrl + "]";
    }
}
