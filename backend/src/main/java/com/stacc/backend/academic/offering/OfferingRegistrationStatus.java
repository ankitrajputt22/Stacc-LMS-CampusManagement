package com.stacc.backend.academic.offering;

/**
 * Whether an offering is open for course registration. Stored as text. Nothing acts on it until course registration
 * arrives (v0.4); admins can already set it.
 */
public enum OfferingRegistrationStatus {
    DRAFT,
    OPEN,
    CLOSED
}
