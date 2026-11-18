package com.stacc.backend.auth.throttle;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class SignInThrottleRows {

    private final SignInThrottleRepository throttles;

    SignInThrottleRows(SignInThrottleRepository throttles) {
        this.throttles = throttles;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensure(SignInThrottle.Scope scope, String keyHash, Instant now) {
        if (!throttles.existsByScopeAndKeyHash(scope, keyHash)) {
            throttles.ensure(scope.name(), keyHash, now);
        }
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
