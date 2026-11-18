package com.stacc.backend.auth.session;

public enum SessionEndReason {
    SIGNED_OUT,
    SIGNED_OUT_EVERYWHERE,
    IDLE,
    PASSWORD_CHANGED,
    ENDED_BY_COLLEGE
}
