package com.stacc.backend.common.storage;

import java.io.InputStream;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;

public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketReady;

    public MinioObjectStorageService(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void store(String key, InputStream content, long sizeBytes, String contentType) {
        ensureBucket();
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(content, sizeBytes, -1L)
                    .contentType(contentType)
                    .build());
        } catch (MinioException | RuntimeException failure) {
            throw new ObjectStorageException("Could not store an object", failure);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (MinioException | RuntimeException failure) {
            throw new ObjectStorageException("Could not read an object", failure);
        }
    }

    @Override
    public boolean isReachable() {
        try {
            client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            return true;
        } catch (MinioException | RuntimeException failure) {
            return false;
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (MinioException | RuntimeException failure) {
            throw new ObjectStorageException("Could not remove an object", failure);
        }
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
            } catch (ErrorResponseException failure) {
                if (!"BucketAlreadyOwnedByYou".equals(failure.errorResponse().code())) {
                    throw new ObjectStorageException("Could not prepare the storage bucket", failure);
                }
            } catch (MinioException | RuntimeException failure) {
                throw new ObjectStorageException("Could not prepare the storage bucket", failure);
            }
            bucketReady = true;
        }
    }
}
