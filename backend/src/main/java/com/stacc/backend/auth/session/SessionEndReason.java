package com.stacc.backend.auth.session;

/** Why a session ended. Stored as text. */
public enum SessionEndReason {
    /** The person signed out in this browser. */
    SIGNED_OUT,
    /** The person signed out of every browser at once. */
    SIGNED_OUT_EVERYWHERE,
    /** Unused for longer than the idle timeout. */
    IDLE,
    /** The account was disabled, or its access changed, by the college. */
    ENDED_BY_COLLEGE
}
