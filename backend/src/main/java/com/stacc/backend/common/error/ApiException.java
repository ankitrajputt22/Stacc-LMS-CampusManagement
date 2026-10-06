package com.stacc.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * An expected failure that should reach the client as a specific HTTP status,
 * in the common error format. The message is shown to the client, so it must be safe to expose.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
