package com.stacc.backend.common.storage;

import java.io.InputStream;

public interface ObjectStorageService {

    void store(String key, InputStream content, long sizeBytes, String contentType);

    InputStream open(String key);

    void delete(String key);

    boolean isReachable();
}
