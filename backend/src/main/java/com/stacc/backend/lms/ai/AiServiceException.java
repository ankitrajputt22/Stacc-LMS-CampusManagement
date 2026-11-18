package com.stacc.backend.lms.ai;

public class AiServiceException extends RuntimeException {

    public enum Reason {
        DISABLED,
        UNAVAILABLE,
        BAD_RESPONSE,
        INDEX_NOT_READY,
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
