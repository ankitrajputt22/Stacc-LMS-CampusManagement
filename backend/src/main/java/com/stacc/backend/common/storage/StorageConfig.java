package com.stacc.backend.common.storage;

import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageConfig.class);

    // Creating the client does not contact the server, so the application starts even when storage is
    // down. Without credentials every storage call fails, and uploads and downloads say so.
    @Bean
    MinioClient minioClient(StorageProperties properties) {
        MinioClient.Builder builder = MinioClient.builder().endpoint(properties.endpoint());
        if (properties.hasCredentials()) {
            builder.credentials(properties.accessKey(), properties.secretKey());
        } else {
            LOGGER.warn("Object storage has no credentials: set STACC_STORAGE_ACCESS_KEY and STACC_STORAGE_SECRET_KEY "
                    + "to upload and download study materials");
        }
        return builder.build();
    }

    @Bean
    ObjectStorageService objectStorageService(MinioClient minioClient, StorageProperties properties) {
        return new MinioObjectStorageService(minioClient, properties.bucket());
    }
}
