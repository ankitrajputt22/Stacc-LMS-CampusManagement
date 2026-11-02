package com.stacc.backend.auth.throttle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Slows down repeated failed sign-ins, per login ID and per network address, without ever locking an account. A
 * blocked try is refused before the password is checked, and unknown login IDs are counted exactly like real ones, so
 * the answer never reveals whether an account exists. Only SHA-256 hashes of the keys are stored, and nothing about a
 * sign-in is logged.
 */
@Service
@EnableConfigurationProperties(SignInThrottleProperties.class)
public class SignInThrottleService {

    private final SignInThrottleRepository throttles;
    private final SignInThrottleProperties properties;
    private final Clock clock;
    private final SignInThrottleRows rows;

    public SignInThrottleService(SignInThrottleRepository throttles, SignInThrottleProperties properties, Clock clock,
            SignInThrottleRows rows) {
        this.throttles = throttles;
        this.properties = properties;
        this.clock = clock;
        this.rows = rows;
    }

    public SignInThrottleProperties properties() {
        return properties;
    }

    /** How long a sign-in for this login ID from this address must still wait; zero when it may try now. */
    @Transactional(readOnly = true)
    public Duration waitFor(String loginId, String address) {
        Instant now = clock.instant();
        return throttles.findAllByKeyHashIn(List.of(accountKey(loginId), addressKey(address))).stream()
                .map(throttle -> throttle.waitAt(now))
                .max(Duration::compareTo)
                .orElse(Duration.ZERO);
    }

    /**
     * Counts a failed sign-in for the login ID and the address, each under its own row lock. The rows are first made
     * sure to exist in a transaction of their own: an {@code insert ignore} leaves a shared lock on an existing row, and
     * taking the row lock in that same transaction would let simultaneous failures deadlock one another.
     */
    public void recordFailure(String loginId, String address) {
        Instant now = clock.instant();
        String account = accountKey(loginId);
        String place = addressKey(address);
        rows.ensure(account, place, now);
        rows.countFailure(account, place, now, properties);
    }

    /** A successful sign-in forgets the login ID's failures. The address keeps its count until its window passes. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String loginId) {
        throttles.findForUpdate(SignInThrottle.Scope.ACCOUNT, accountKey(loginId))
                .ifPresent(throttle -> throttle.clear(clock.instant()));
    }

    static String accountKey(String loginId) {
        return sha256("account:" + (loginId == null ? "" : loginId.strip().toLowerCase(Locale.ROOT)));
    }

    static String addressKey(String address) {
        return sha256("address:" + (address == null ? "unknown" : address));
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
