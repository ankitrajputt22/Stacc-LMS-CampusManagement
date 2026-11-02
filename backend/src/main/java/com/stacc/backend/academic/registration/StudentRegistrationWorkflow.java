package com.stacc.backend.academic.registration;

import java.util.List;

import com.stacc.backend.academic.admin.CourseEnrollmentLmsPort;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegisteredCourse;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Registration and dropping, then LMS access. The official enrollment is saved and committed first; LMS membership
 * follows only when the offering already has an LMS course, and an LMS failure never undoes the enrollment (admins
 * can retry it). A dropped course's membership is made inactive.
 */
@Service
public class StudentRegistrationWorkflow {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudentRegistrationWorkflow.class);

    private final StudentRegistrationService registration;
    private final CourseEnrollmentLmsPort lms;

    public StudentRegistrationWorkflow(StudentRegistrationService registration, CourseEnrollmentLmsPort lms) {
        this.registration = registration;
        this.lms = lms;
    }

    public RegistrationResult register(Long userAccountId, List<Long> courseOfferingIds) {
        RegistrationResult result = registration.register(userAccountId, courseOfferingIds);
        for (RegisteredCourse course : result.registered()) {
            try {
                lms.provision(course.courseEnrollmentId());
            } catch (RuntimeException failure) {
                LOGGER.warn("LMS membership could not be provisioned after a student registration", failure);
            }
        }
        return result;
    }

    public RegisteredCourse drop(Long userAccountId, Long courseEnrollmentId) {
        RegisteredCourse dropped = registration.drop(userAccountId, courseEnrollmentId);
        try {
            lms.deactivate(dropped.courseEnrollmentId());
        } catch (RuntimeException failure) {
            LOGGER.warn("LMS membership could not be made inactive after a drop", failure);
        }
        return dropped;
    }
}
