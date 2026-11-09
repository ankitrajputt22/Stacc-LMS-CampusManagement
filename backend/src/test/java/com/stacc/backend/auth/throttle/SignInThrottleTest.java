package com.stacc.backend.auth.throttle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SignInThrottleTest {

    private static final Instant T0 = Instant.parse("2026-11-03T09:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BASE = Duration.ofSeconds(30);
    private static final Duration MAX = Duration.ofMinutes(15);

    private static SignInThrottle newThrottle() {
        try {
            var constructor = SignInThrottle.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void failTimes(SignInThrottle throttle, int times, Instant at) {
        for (int i = 0; i < times; i++) {
            throttle.fail(at, 5, WINDOW, BASE, MAX);
        }
    }

    @Test
    void triesSlowDownFromTheThresholdDoublingUpToTheMaximum() {
        SignInThrottle throttle = newThrottle();

        failTimes(throttle, 4, T0);
        assertEquals(Duration.ZERO, throttle.waitAt(T0));

        throttle.fail(T0, 5, WINDOW, BASE, MAX);
        assertEquals(Duration.ofSeconds(30), throttle.waitAt(T0));
        throttle.fail(T0, 5, WINDOW, BASE, MAX);
        assertEquals(Duration.ofSeconds(60), throttle.waitAt(T0));
        failTimes(throttle, 10, T0);
        assertEquals(MAX, throttle.waitAt(T0));
        assertEquals(Duration.ZERO, throttle.waitAt(T0.plus(MAX)));
    }

    @Test
    void oldFailuresAreForgottenOnceTheWindowHasPassedAndNothingIsBlocked() {
        SignInThrottle throttle = newThrottle();
        failTimes(throttle, 4, T0);

        throttle.fail(T0.plus(WINDOW), 5, WINDOW, BASE, MAX);

        assertEquals(1, throttle.getFailures());
        assertEquals(Duration.ZERO, throttle.waitAt(T0.plus(WINDOW)));
    }

    @Test
    void aSuccessClearsEverything() {
        SignInThrottle throttle = newThrottle();
        failTimes(throttle, 7, T0);

        throttle.clear(T0);

        assertEquals(0, throttle.getFailures());
        assertNull(throttle.getBlockedUntil());
    }

    @Test
    void settingsOutsideTheirRangesAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new SignInThrottleProperties(2, 30, WINDOW, BASE, MAX, false));
        assertThrows(IllegalArgumentException.class,
                () -> new SignInThrottleProperties(5, 4, WINDOW, BASE, MAX, false));
        assertThrows(IllegalArgumentException.class,
                () -> new SignInThrottleProperties(5, 30, WINDOW, MAX, BASE, false));
    }

    @Test
    void keysAreHashedAndLoginIdsIgnoreCaseAndSpaces() {
        assertEquals(SignInThrottleService.accountKey(" Student1 "), SignInThrottleService.accountKey("student1"));
        assertEquals(64, SignInThrottleService.addressKey("203.0.113.5").length());
        assertEquals(false, SignInThrottleService.accountKey("x").equals(SignInThrottleService.addressKey("x")));
    }
}
