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

    @Transactional(readOnly = true)
    public Duration waitFor(String loginId, String address) {
        Instant now = clock.instant();
        return throttles.findAllByKeyHashIn(List.of(accountKey(loginId), addressKey(address))).stream()
                .map(throttle -> throttle.waitAt(now))
                .max(Duration::compareTo)
                .orElse(Duration.ZERO);
    }

    public void recordFailure(String loginId, String address) {
        Instant now = clock.instant();
        String account = accountKey(loginId);
        String place = addressKey(address);
        retrying(() -> rows.ensure(SignInThrottle.Scope.ACCOUNT, account, now));
        retrying(() -> rows.ensure(SignInThrottle.Scope.ADDRESS, place, now));
        retrying(() -> rows.countFailure(account, place, now, properties));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String loginId) {
        throttles.findForUpdate(SignInThrottle.Scope.ACCOUNT, accountKey(loginId))
                .ifPresent(throttle -> throttle.clear(clock.instant()));
    }

    private static final int ATTEMPTS = 5;

    private static void retrying(Runnable step) {
        for (int attempt = 1; ; attempt++) {
            try {
                step.run();
                return;
            } catch (org.springframework.dao.PessimisticLockingFailureException lost) {
                if (attempt >= ATTEMPTS) {
                    throw lost;
                }
                try {
                    Thread.sleep(10L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw lost;
                }
            }
        }
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
