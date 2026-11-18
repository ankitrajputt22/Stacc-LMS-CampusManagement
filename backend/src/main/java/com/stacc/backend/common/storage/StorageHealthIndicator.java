package com.stacc.backend.common.storage;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("storageHealthIndicator")
public class StorageHealthIndicator implements HealthIndicator {

    private final ObjectStorageService storage;

    public StorageHealthIndicator(ObjectStorageService storage) {
        this.storage = storage;
    }

    @Override
    public Health health() {
        return storage.isReachable() ? Health.up().build() : Health.down().build();
    }
}
