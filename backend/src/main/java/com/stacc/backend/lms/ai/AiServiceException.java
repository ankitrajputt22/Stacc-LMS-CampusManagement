package com.stacc.backend.lms.ai;

/** A call to the AI service did not give a usable result. The reason is all callers need; details stay in the log. */
public class AiServiceException extends RuntimeException {

    public enum Reason {
        /** AI is switched off or not configured. */
        DISABLED,
        /** The AI service or its model provider could not be reached, timed out, or failed. */
        UNAVAILABLE,
        /** The AI service answered with something unusable. */
        BAD_RESPONSE,
        /** The AI service has no index matching the content the backend expects. */
        INDEX_NOT_READY,
        /** No source produced any text to index. */
        NO_CONTENT
    }

    private final Reason reason;

    public AiServiceException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public AiServiceException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
