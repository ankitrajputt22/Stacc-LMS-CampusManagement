package com.stacc.backend.auth.throttle;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How failed sign-ins slow down further tries. After {@code accountThreshold} failures for one login ID, or
 * {@code addressThreshold} from one network address, within {@code window}, each further failure blocks that key for
 * {@code baseDelay}, doubling each time up to {@code maxDelay}. Accounts are never locked. {@code trustProxy} reads the
 * address from the {@code X-Real-IP} header set by Stacc's own proxy; it must stay false when the backend can be
 * reached directly, or anyone could choose their address.
 */
@ConfigurationProperties(prefix = "stacc.sign-in")
public record SignInThrottleProperties(int accountThreshold, int addressThreshold, Duration window, Duration baseDelay,
        Duration maxDelay, boolean trustProxy) {

    public SignInThrottleProperties {
        if (accountThreshold < 3 || accountThreshold > 50) {
            throw new IllegalArgumentException("STACC_SIGN_IN_ACCOUNT_THRESHOLD must be from 3 to 50");
        }
        if (addressThreshold < accountThreshold || addressThreshold > 500) {
            throw new IllegalArgumentException(
                    "STACC_SIGN_IN_ADDRESS_THRESHOLD must be at least the account threshold and at most 500");
        }
        if (window == null || window.compareTo(Duration.ofMinutes(1)) < 0 || window.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("STACC_SIGN_IN_WINDOW must be between 1m and 24h");
        }
        if (baseDelay == null || baseDelay.compareTo(Duration.ofSeconds(1)) < 0
                || maxDelay == null || maxDelay.compareTo(baseDelay) < 0 || maxDelay.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException(
                    "STACC_SIGN_IN_BASE_DELAY must be at least 1s and STACC_SIGN_IN_MAX_DELAY from it up to 1h");
        }
    }
}
