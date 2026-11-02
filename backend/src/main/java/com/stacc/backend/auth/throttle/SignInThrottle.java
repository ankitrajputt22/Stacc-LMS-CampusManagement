package com.stacc.backend.auth.throttle;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Failed sign-ins counted for one hashed key: a login ID or a network address. */
@Entity
@Table(name = "sign_in_throttles")
public class SignInThrottle {

    public enum Scope { ACCOUNT, ADDRESS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Scope scope;

    @Column(name = "key_hash", nullable = false, length = 64, updatable = false)
    private String keyHash;

    @Column(nullable = false)
    private int failures;

    @Column(name = "first_failure_at")
    private Instant firstFailureAt;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SignInThrottle() {
        // Required by JPA.
    }

    public int getFailures() {
        return failures;
    }

    public Instant getBlockedUntil() {
        return blockedUntil;
    }

    /** How long this key must still wait at this moment; zero when it may try. */
    public Duration waitAt(Instant now) {
        return blockedUntil == null || !now.isBefore(blockedUntil) ? Duration.ZERO : Duration.between(now, blockedUntil);
    }

    /**
     * Counts one more failure. Failures older than the window are forgotten unless the key is still blocked. From the
     * threshold on, each failure blocks the key for the base delay, doubling each time, up to the maximum.
     */
    void fail(Instant now, int threshold, Duration window, Duration baseDelay, Duration maxDelay) {
        boolean blocked = blockedUntil != null && now.isBefore(blockedUntil);
        if (firstFailureAt == null || (!blocked && !now.isBefore(firstFailureAt.plus(window)))) {
            failures = 0;
            firstFailureAt = now;
            blockedUntil = null;
        }
        failures++;
        if (failures >= threshold) {
            int doublings = Math.min(failures - threshold, 20);
            Duration delay = baseDelay.multipliedBy(1L << doublings);
            blockedUntil = now.plus(delay.compareTo(maxDelay) > 0 ? maxDelay : delay);
        }
        updatedAt = now;
    }

    /** A successful sign-in forgets this key's failures. */
    void clear(Instant now) {
        failures = 0;
        firstFailureAt = null;
        blockedUntil = null;
        updatedAt = now;
    }
}
