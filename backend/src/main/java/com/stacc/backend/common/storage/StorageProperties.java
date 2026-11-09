package com.stacc.backend.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where object storage is and how to reach it. The credentials come from the environment and are
 * never written in the code or sent to the browser.
 *
 * @param endpoint  the storage address, such as http://localhost:9000
 * @param accessKey the access key; when blank, storage calls fail with a clear error
 * @param secretKey the secret key
 * @param bucket    the one private bucket the application uses
 */
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

    // The default text form of a record would include the secret key.
    @Override
    public String toString() {
        return "StorageProperties[endpoint=" + endpoint + ", bucket=" + bucket + "]";
    }
}
