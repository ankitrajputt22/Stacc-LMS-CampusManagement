package com.stacc.backend.academic.admin;

import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentStatusChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseEnrollmentCreatedResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseEnrollmentResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.LmsProvisioningResponse;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Course enrollment changes followed by their LMS step. The official enrollment is saved and committed first, in its
 * own transaction; only then is LMS membership given (for a new enrollment) or switched off (for an ended one).
 * This class is deliberately not transactional, so an LMS problem can never undo or block the official record: a
 * failure is logged and reported, and provisioning can be retried. No LMS course is ever created here.
 */
@Service
public class CourseEnrollmentWorkflow {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourseEnrollmentWorkflow.class);

    static final String NOT_ENROLLED = "Only an enrolled course enrollment can be given LMS access.";
    static final String NO_LMS_COURSE = "This course offering has no LMS course yet.";
    static final String PROVISIONING_FAILED = "LMS access could not be provisioned just now. Try again later.";

    private final AcademicEnrollmentManagementService enrollments;
    private final CourseEnrollmentLmsPort lms;

    public CourseEnrollmentWorkflow(AcademicEnrollmentManagementService enrollments, CourseEnrollmentLmsPort lms) {
        this.enrollments = enrollments;
        this.lms = lms;
    }

    public CourseEnrollmentCreatedResponse create(Long semesterEnrollmentId, CourseEnrollmentCreate request) {
        CourseEnrollmentResponse created = enrollments.createCourseEnrollment(semesterEnrollmentId, request);
        LmsProvisioningOutcome outcome;
        try {
            outcome = lms.provision(created.courseEnrollmentId());
        } catch (RuntimeException failure) {
            LOGGER.warn("LMS membership could not be provisioned for a new course enrollment", failure);
            outcome = LmsProvisioningOutcome.FAILED;
        }
        return new CourseEnrollmentCreatedResponse(created, outcome.name());
    }

    /**
     * Changes the official status. When the enrollment is no longer {@code ENROLLED}, its LMS membership is made
     * inactive as well. The student access policy already refuses an ended enrollment, so a failure here is only
     * logged.
     */
    public CourseEnrollmentResponse changeStatus(Long courseEnrollmentId, CourseEnrollmentStatusChange request) {
        CourseEnrollmentResponse changed = enrollments.changeCourseEnrollmentStatus(courseEnrollmentId, request);
        if (!CourseEnrollmentStatus.ENROLLED.name().equals(changed.status())) {
            try {
                lms.deactivate(changed.courseEnrollmentId());
            } catch (RuntimeException failure) {
                LOGGER.warn("LMS membership could not be made inactive for an ended course enrollment", failure);
            }
        }
        return changed;
    }

    /** Tries LMS membership again for an enrolled course enrollment whose offering has an LMS course. */
    public LmsProvisioningResponse retryProvisioning(Long courseEnrollmentId) {
        CourseEnrollmentResponse enrollment = enrollments.requireEnrolledCourseEnrollment(courseEnrollmentId);
        LmsProvisioningOutcome outcome;
        try {
            outcome = lms.provision(enrollment.courseEnrollmentId());
        } catch (ApiException refusal) {
            throw refusal;
        } catch (RuntimeException failure) {
            LOGGER.warn("LMS membership could not be provisioned on retry", failure);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, PROVISIONING_FAILED);
        }
        if (outcome == LmsProvisioningOutcome.NO_LMS_COURSE) {
            throw AcademicMasterErrors.conflict(NO_LMS_COURSE);
        }
        return new LmsProvisioningResponse(enrollment.courseEnrollmentId(), outcome.name());
    }
}
