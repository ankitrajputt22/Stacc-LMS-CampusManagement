package com.stacc.backend.lms.access;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseStatus;

/**
 * The one place that says when a student may use an LMS course. Access is worked out live from the official
 * enrollment and the course's state; no copied membership decides it, so nothing can drift. The single-course check,
 * the course list, and every feature that asks for the student's place in a course all ask here.
 */
public final class LmsStudentAccessPolicy {

    private LmsStudentAccessPolicy() {
    }

    /**
     * True only when the course enrollment belongs to the student behind this account and all of these hold: the
     * student profile is active, the semester enrollment and the course enrollment are both still enrolled, the LMS
     * course is published, and both are for the same course offering.
     *
     * <p>It only reads what it is given. Offerings are compared by ID, because the two sides may hold different Java
     * objects for the same offering.
     */
    public static boolean grantsCurrentAccess(CourseEnrollment courseEnrollment, LmsCourse lmsCourse, Long userAccountId) {
        SemesterEnrollment semesterEnrollment = courseEnrollment.getSemesterEnrollment();
        StudentProfile studentProfile = semesterEnrollment.getStudentProfile();
        Long courseOfferingId = courseEnrollment.getCourseOffering().getId();
        return userAccountId != null
                && userAccountId.equals(studentProfile.getUserAccount().getId())
                && studentProfile.getStatus() == StudentProfileStatus.ACTIVE
                && semesterEnrollment.getStatus() == SemesterEnrollmentStatus.ENROLLED
                && courseEnrollment.getStatus() == CourseEnrollmentStatus.ENROLLED
                && lmsCourse.getStatus() == LmsCourseStatus.PUBLISHED
                && courseOfferingId != null
                && courseOfferingId.equals(lmsCourse.getCourseOffering().getId());
    }
}
