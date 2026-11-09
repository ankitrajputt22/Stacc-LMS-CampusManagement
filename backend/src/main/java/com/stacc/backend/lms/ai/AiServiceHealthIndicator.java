package com.stacc.backend.lms.ai;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * The "aiService" part of the backend's health: UNKNOWN while AI is switched off, otherwise whether the internal AI
 * service answers its authenticated health check. Like storage it is not part of readiness: every other feature works
 * without it, and AI routes answer 503 meanwhile. Its address and token are never reported.
 */
@Component("aiServiceHealthIndicator")
public class AiServiceHealthIndicator implements HealthIndicator {

    private final AiServiceClient client;

    public AiServiceHealthIndicator(AiServiceClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        if (!client.isEnabled()) {
            return Health.unknown().build();
        }
        return client.isReachable() ? Health.up().build() : Health.down().build();
    }
}
