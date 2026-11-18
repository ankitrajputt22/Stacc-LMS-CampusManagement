package com.stacc.backend.lms.access;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseStatus;

public final class LmsStudentAccessPolicy {

    private LmsStudentAccessPolicy() {
    }

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
