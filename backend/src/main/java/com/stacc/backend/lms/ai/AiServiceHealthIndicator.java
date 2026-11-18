package com.stacc.backend.lms.ai;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

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
