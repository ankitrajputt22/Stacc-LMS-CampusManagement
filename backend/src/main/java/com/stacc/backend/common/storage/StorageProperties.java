package com.stacc.backend.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stacc.storage")
public record StorageProperties(String endpoint, String accessKey, String secretKey, String bucket) {

    public StorageProperties {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("STACC_STORAGE_ENDPOINT must be set, for example to http://localhost:9000");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("STACC_STORAGE_BUCKET must be set");
        }
        endpoint = endpoint.strip();
        bucket = bucket.strip();
    }

    public boolean hasCredentials() {
        return accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
    }

    @Override
    public String toString() {
        return "StorageProperties[endpoint=" + endpoint + ", bucket=" + bucket + "]";
    }
}
