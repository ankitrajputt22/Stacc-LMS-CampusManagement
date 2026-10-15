package com.stacc.backend.common.storage;

import java.io.InputStream;

/**
 * Private storage for file contents, such as study materials. Only metadata lives in the database;
 * the bytes live here, under keys the application generates. Nothing in this storage is public: every
 * download goes through the application, which checks access first.
 *
 * <p>Callers depend on this interface, never on a storage product, so the implementation can be
 * replaced by another private S3-compatible store without touching the features that use it.
 */
public interface ObjectStorageService {

    /** Stores the content under the key. The stream is read to the end but not closed. */
    void store(String key, InputStream content, long sizeBytes, String contentType);

    /** Opens the stored content for reading. The caller must close the stream. */
    InputStream open(String key);

    /** Removes the content. Removing a key that does not exist is not an error. */
    void delete(String key);
}
