package com.stacc.backend.lms.membership;

import java.util.Optional;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and switches off LMS student memberships, one official course enrollment at a time.
 *
 * <p>The course enrollment is the official record and is only ever read here. Nothing in this
 * service changes academic data, creates an LMS course, or decides whether a student may open
 * a course. It is called explicitly: nothing runs it automatically when an enrollment changes.
 */
@Service
public class LmsStudentMembershipProvisioningService {

    static final String ENROLLMENT_NOT_FOUND_MESSAGE = "Course enrollment not found.";
    static final String NOT_ENROLLED_MESSAGE =
            "Only a course enrollment that is still enrolled can be given LMS membership.";
    static final String NO_LMS_COURSE_MESSAGE = "This course offering has no LMS course yet.";

    private final CourseEnrollmentRepository courseEnrollments;
    private final LmsCourseRepository lmsCourses;
    private final LmsStudentMembershipRepository memberships;

    public LmsStudentMembershipProvisioningService(
            CourseEnrollmentRepository courseEnrollments,
            LmsCourseRepository lmsCourses,
            LmsStudentMembershipRepository memberships) {
        this.courseEnrollments = courseEnrollments;
        this.lmsCourses = lmsCourses;
        this.memberships = memberships;
    }

    /**
     * Gives the student of an official course enrollment an active membership in the LMS course
     * of the same course offering. It is safe to call again for the same enrollment: a membership
     * is created only when none exists, and an inactive one is made active again.
     *
     * <p>The LMS course does not have to be published. An active membership does not by itself
     * give access to the course.
     */
    @Transactional
    public LmsStudentMembership provision(Long courseEnrollmentId) {
        Require.notNull(courseEnrollmentId, "courseEnrollmentId");
        CourseEnrollment courseEnrollment = courseEnrollments.findById(courseEnrollmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ENROLLMENT_NOT_FOUND_MESSAGE));
        if (courseEnrollment.getStatus() != CourseEnrollmentStatus.ENROLLED) {
            throw new ApiException(HttpStatus.CONFLICT, NOT_ENROLLED_MESSAGE);
        }

        Long courseOfferingId = courseEnrollment.getCourseOffering().getId();
        LmsCourse lmsCourse = lmsCourses.findByCourseOfferingId(courseOfferingId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NO_LMS_COURSE_MESSAGE));
        requireSameCourseOffering(courseEnrollmentId, courseOfferingId, lmsCourse);

        Optional<LmsStudentMembership> existing = memberships.findByCourseEnrollmentId(courseEnrollmentId);
        if (existing.isEmpty()) {
            return memberships.save(new LmsStudentMembership(courseEnrollment, lmsCourse));
        }

        LmsStudentMembership membership = existing.get();
        // A membership is never moved to another LMS course, so a wrong link is reported, not repaired.
        requireSameCourseOffering(courseEnrollmentId, courseOfferingId, membership.getLmsCourse());
        // Already active: nothing changes. Inactive: active again. The change is saved when the
        // transaction ends.
        membership.activate();
        return membership;
    }

    /**
     * Makes the membership of a course enrollment inactive. Nothing happens when the enrollment
     * has no membership or the membership is already inactive. The course enrollment itself is
     * neither read nor changed: its official status is the business of the academic side.
     */
    @Transactional
    public void deactivate(Long courseEnrollmentId) {
        Require.notNull(courseEnrollmentId, "courseEnrollmentId");
        memberships.findByCourseEnrollmentId(courseEnrollmentId).ifPresent(LmsStudentMembership::deactivate);
    }

    // Compared by ID, because the two sides may hold different Java objects for the same offering.
    // A mismatch means the stored data is inconsistent. It is a server problem, not a user mistake.
    private static void requireSameCourseOffering(Long courseEnrollmentId, Long courseOfferingId, LmsCourse lmsCourse) {
        Long lmsCourseOfferingId = lmsCourse.getCourseOffering().getId();
        if (courseOfferingId == null || !courseOfferingId.equals(lmsCourseOfferingId)) {
            throw new IllegalStateException(
                    "The LMS course for course enrollment " + courseEnrollmentId
                            + " does not belong to the enrollment's course offering");
        }
    }
}
