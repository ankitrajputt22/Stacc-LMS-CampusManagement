package com.stacc.backend.auth.throttle;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two steps of counting a failure, each in a transaction of its own. An {@code insert ignore} leaves a shared lock
 * on an existing row; taking the row lock in that same transaction would let simultaneous failures deadlock one another.
 */
@Component
class SignInThrottleRows {

    private final SignInThrottleRepository throttles;

    SignInThrottleRows(SignInThrottleRepository throttles) {
        this.throttles = throttles;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensure(String accountKey, String addressKey, Instant now) {
        throttles.ensure(SignInThrottle.Scope.ACCOUNT.name(), accountKey, now);
        throttles.ensure(SignInThrottle.Scope.ADDRESS.name(), addressKey, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void countFailure(String accountKey, String addressKey, Instant now, SignInThrottleProperties properties) {
        fail(SignInThrottle.Scope.ACCOUNT, accountKey, properties.accountThreshold(), now, properties);
        fail(SignInThrottle.Scope.ADDRESS, addressKey, properties.addressThreshold(), now, properties);
    }

    private void fail(SignInThrottle.Scope scope, String keyHash, int threshold, Instant now,
            SignInThrottleProperties properties) {
        SignInThrottle throttle = throttles.findForUpdate(scope, keyHash)
                .orElseThrow(() -> new IllegalStateException("The sign-in throttle row is missing"));
        Duration window = properties.window();
        throttle.fail(now, threshold, window, properties.baseDelay(), properties.maxDelay());
    }
}
