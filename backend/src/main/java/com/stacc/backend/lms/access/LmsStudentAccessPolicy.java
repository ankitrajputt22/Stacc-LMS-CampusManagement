package com.stacc.backend.lms.access;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseStatus;
import com.stacc.backend.lms.membership.LmsStudentMembership;
import com.stacc.backend.lms.membership.LmsStudentMembershipStatus;

/**
 * The one place that says when a membership currently lets a student use its LMS course.
 * The single-course check and the student's course list both ask here, so they cannot disagree.
 */
public final class LmsStudentAccessPolicy {

    private LmsStudentAccessPolicy() {
    }

    /**
     * True only when the membership belongs to the student behind this account and all of these
     * hold: the student profile is active, the semester enrollment and the course enrollment are
     * both still enrolled, the membership is active, the LMS course is published, and the course
     * enrollment is for the same course offering as the LMS course.
     *
     * <p>It only reads what it is given. Offerings are compared by ID, because the two sides may
     * hold different Java objects for the same offering.
     */
    public static boolean grantsCurrentAccess(LmsStudentMembership membership, Long userAccountId) {
        CourseEnrollment courseEnrollment = membership.getCourseEnrollment();
        SemesterEnrollment semesterEnrollment = courseEnrollment.getSemesterEnrollment();
        StudentProfile studentProfile = semesterEnrollment.getStudentProfile();
        LmsCourse lmsCourse = membership.getLmsCourse();
        Long courseOfferingId = courseEnrollment.getCourseOffering().getId();

        return userAccountId != null
                && userAccountId.equals(studentProfile.getUserAccount().getId())
                && studentProfile.getStatus() == StudentProfileStatus.ACTIVE
                && semesterEnrollment.getStatus() == SemesterEnrollmentStatus.ENROLLED
                && courseEnrollment.getStatus() == CourseEnrollmentStatus.ENROLLED
                && membership.getStatus() == LmsStudentMembershipStatus.ACTIVE
                && lmsCourse.getStatus() == LmsCourseStatus.PUBLISHED
                && courseOfferingId != null
                && courseOfferingId.equals(lmsCourse.getCourseOffering().getId());
    }
}
