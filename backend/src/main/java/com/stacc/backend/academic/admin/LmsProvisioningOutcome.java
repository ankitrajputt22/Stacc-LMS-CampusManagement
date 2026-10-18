package com.stacc.backend.academic.admin;

/** What happened on the LMS side after an official course enrollment was saved. */
public enum LmsProvisioningOutcome {
    /** The student has an active LMS membership in the offering's LMS course. */
    PROVISIONED,
    /** The offering has no LMS course yet, so there was nothing to join. None is ever created here. */
    NO_LMS_COURSE,
    /** Membership could not be given just now. The official enrollment stands, and provisioning can be retried. */
    FAILED
}
