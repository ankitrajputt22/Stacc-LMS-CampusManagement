package com.stacc.backend.common.error;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldValidationError> fieldErrors,
        /* The request's ID, also in the X-Request-ID header and the server's log lines; quoting it helps find them. */
        String requestId) {
}
