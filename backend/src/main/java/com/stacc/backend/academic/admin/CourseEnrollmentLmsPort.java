package com.stacc.backend.academic.admin;

/**
 * What academic enrollment administration asks of the LMS once an official course enrollment has been saved. The
 * academic side defines this and never refers to LMS models; the LMS module implements it with its existing
 * membership provisioning. The official enrollment is the source of truth either way.
 */
public interface CourseEnrollmentLmsPort {

    /**
     * Gives the student of an {@code ENROLLED} course enrollment an active membership in the LMS course of the same
     * offering, when that offering has one. It is safe to call again. It never creates an LMS course and never
     * changes the enrollment.
     *
     * @return {@link LmsProvisioningOutcome#PROVISIONED}, or {@link LmsProvisioningOutcome#NO_LMS_COURSE} when the
     *         offering has no LMS course
     * @throws RuntimeException when membership could not be given
     */
    LmsProvisioningOutcome provision(Long courseEnrollmentId);

    /** Makes the enrollment's LMS membership inactive, if it has one. Nothing is deleted. */
    void deactivate(Long courseEnrollmentId);
}
