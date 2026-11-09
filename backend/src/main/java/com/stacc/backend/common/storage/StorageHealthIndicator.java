package com.stacc.backend.common.storage;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * The "storage" part of the backend's health: whether the private file store answers. It is part of the overall health
 * but not of readiness, because the application still works for everything except file uploads and downloads while the
 * store is away. No address, bucket, or credential is ever reported.
 */
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
