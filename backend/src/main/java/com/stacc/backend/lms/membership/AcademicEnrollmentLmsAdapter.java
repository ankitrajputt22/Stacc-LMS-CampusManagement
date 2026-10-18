package com.stacc.backend.lms.membership;

import com.stacc.backend.academic.admin.CourseEnrollmentLmsPort;
import com.stacc.backend.academic.admin.LmsProvisioningOutcome;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connects academic enrollment administration to LMS membership provisioning. It only looks for the offering's
 * existing LMS course and then uses {@link LmsStudentMembershipProvisioningService}, which owns every membership
 * rule. It never creates an LMS course and never changes academic data.
 */
@Component
class AcademicEnrollmentLmsAdapter implements CourseEnrollmentLmsPort {

    private final CourseEnrollmentRepository courseEnrollments;
    private final LmsCourseRepository lmsCourses;
    private final LmsStudentMembershipProvisioningService provisioning;

    AcademicEnrollmentLmsAdapter(CourseEnrollmentRepository courseEnrollments, LmsCourseRepository lmsCourses,
            LmsStudentMembershipProvisioningService provisioning) {
        this.courseEnrollments = courseEnrollments;
        this.lmsCourses = lmsCourses;
        this.provisioning = provisioning;
    }

    @Override
    @Transactional
    public LmsProvisioningOutcome provision(Long courseEnrollmentId) {
        CourseEnrollment courseEnrollment = courseEnrollments.findById(courseEnrollmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        LmsStudentMembershipProvisioningService.ENROLLMENT_NOT_FOUND_MESSAGE));
        if (lmsCourses.findByCourseOfferingId(courseEnrollment.getCourseOffering().getId()).isEmpty()) {
            return LmsProvisioningOutcome.NO_LMS_COURSE;
        }
        provisioning.provision(courseEnrollmentId);
        return LmsProvisioningOutcome.PROVISIONED;
    }

    @Override
    public void deactivate(Long courseEnrollmentId) {
        provisioning.deactivate(courseEnrollmentId);
    }
}
