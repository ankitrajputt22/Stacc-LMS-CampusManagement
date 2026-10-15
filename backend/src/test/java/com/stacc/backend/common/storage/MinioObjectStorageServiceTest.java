package com.stacc.backend.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.errors.MinioException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The MinIO client is a stand-in here, so no storage server is needed. Live checks are done against MinIO itself. */
class MinioObjectStorageServiceTest {

    private final MinioClient client = mock(MinioClient.class);
    private final MinioObjectStorageService storage = new MinioObjectStorageService(client, "stacc-test");

    @Test
    void storingCreatesTheMissingBucketOnceKeepsItPrivateAndUsesTheGivenKey() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        storage.store("lms/courses/7/topics/31/abc", new ByteArrayInputStream(new byte[] {1, 2, 3}), 3, "application/pdf");
        storage.store("lms/courses/7/topics/31/def", new ByteArrayInputStream(new byte[] {4}), 1, "application/pdf");

        verify(client, times(1)).bucketExists(any(BucketExistsArgs.class));
        ArgumentCaptor<MakeBucketArgs> made = ArgumentCaptor.forClass(MakeBucketArgs.class);
        verify(client).makeBucket(made.capture());
        assertEquals("stacc-test", made.getValue().bucket());
        verify(client, never()).setBucketPolicy(any(SetBucketPolicyArgs.class));

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(client, times(2)).putObject(put.capture());
        assertEquals("stacc-test", put.getAllValues().get(0).bucket());
        assertEquals("lms/courses/7/topics/31/abc", put.getAllValues().get(0).object());
        assertEquals("application/pdf", put.getAllValues().get(0).contentType().toString());
    }

    @Test
    void existingBucketIsNotCreatedAgain() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        storage.store("key", new ByteArrayInputStream(new byte[] {1}), 1, "text/plain");

        verify(client, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void openingAndDeletingUseTheStoredKey() throws Exception {
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(client.getObject(any(GetObjectArgs.class))).thenReturn(response);

        assertSame(response, storage.open("lms/courses/7/topics/31/abc"));
        storage.delete("lms/courses/7/topics/31/abc");

        ArgumentCaptor<GetObjectArgs> got = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(client).getObject(got.capture());
        assertEquals("lms/courses/7/topics/31/abc", got.getValue().object());
        ArgumentCaptor<RemoveObjectArgs> removed = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).removeObject(removed.capture());
        assertEquals("lms/courses/7/topics/31/abc", removed.getValue().object());
    }

    @Test
    void storageFailuresBecomeOneKindOfError() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenThrow(new MinioException("unreachable"));
        assertThrows(ObjectStorageException.class,
                () -> storage.store("key", new ByteArrayInputStream(new byte[] {1}), 1, "text/plain"));

        when(client.getObject(any(GetObjectArgs.class))).thenThrow(new MinioException("missing"));
        assertThrows(ObjectStorageException.class, () -> storage.open("key"));
    }
}
