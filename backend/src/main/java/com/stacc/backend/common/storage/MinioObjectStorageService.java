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

/**
 * Object storage on MinIO, or any S3-compatible server the MinIO client can reach. The bucket is created
 * on first use if it is missing, and it is left private: no access policy is ever set, so objects can only
 * be read with the application's credentials.
 */
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
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (MinioException | RuntimeException failure) {
            throw new ObjectStorageException("Could not remove an object", failure);
        }
    }

    // Checked once per run. Startup does not depend on storage being up; the first upload creates the bucket.
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
                // Another instance created it at the same moment: that is the outcome we wanted.
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
