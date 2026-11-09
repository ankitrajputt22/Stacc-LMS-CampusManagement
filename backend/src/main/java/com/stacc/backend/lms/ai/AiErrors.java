package com.stacc.backend.lms.ai;

import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** The client-safe answers to AI failures. They never name the AI service, model, host, or index. */
public final class AiErrors {

    public static final String UNAVAILABLE = "AI learning features are temporarily unavailable.";
    public static final String BAD_RESPONSE = "The AI service couldn't produce a usable result. Try again.";
    public static final String STUDY_NOT_READY = "AI study content is not ready for this course yet.";
    public static final String TEACHING_NOT_READY = "AI knowledge is not ready for this course. Refresh AI knowledge first.";
    public static final String NO_CONTENT = "No published course content is available for AI indexing.";

    private AiErrors() {
    }

    public static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
    }

    public static ApiException noContent() {
        return new ApiException(HttpStatus.CONFLICT, NO_CONTENT);
    }

    /** The API answer for a failed call; {@code notReadyMessage} is the student or faculty wording for 409. */
    public static ApiException of(AiServiceException exception, String notReadyMessage) {
        return switch (exception.reason()) {
            case DISABLED, UNAVAILABLE -> unavailable();
            case BAD_RESPONSE -> new ApiException(HttpStatus.BAD_GATEWAY, BAD_RESPONSE);
            case INDEX_NOT_READY -> new ApiException(HttpStatus.CONFLICT, notReadyMessage);
            case NO_CONTENT -> noContent();
        };
    }

    /** The fixed message stored as a failed refresh's last error and shown to faculty. */
    public static String refreshFailure(AiServiceException.Reason reason) {
        return switch (reason) {
            case DISABLED, UNAVAILABLE -> "The AI service was unavailable.";
            case BAD_RESPONSE -> "The AI service could not process the course content.";
            case NO_CONTENT -> "No published course content could be indexed.";
            case INDEX_NOT_READY -> "The refresh failed.";
        };
    }
}
