package com.stacc.backend.common.storage;

/** Object storage could not do what was asked: it is unreachable, misconfigured, or refused. */
public class ObjectStorageException extends RuntimeException {

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
