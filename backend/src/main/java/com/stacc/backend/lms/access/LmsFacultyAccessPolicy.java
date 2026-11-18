package com.stacc.backend.lms.access;

import java.util.Set;

import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentStatus;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseStatus;

public final class LmsFacultyAccessPolicy {

    private static final Set<LmsCourseStatus> OPEN_TO_FACULTY = Set.of(LmsCourseStatus.DRAFT, LmsCourseStatus.PUBLISHED);

    private LmsFacultyAccessPolicy() {
    }

    public static boolean isOpenToFaculty(LmsCourseStatus status) {
        return OPEN_TO_FACULTY.contains(status);
    }

    public static boolean grantsCurrentAccess(TeachingAssignment assignment, LmsCourse lmsCourse, Long userAccountId) {
        FacultyProfile facultyProfile = assignment.getFacultyProfile();
        Long courseOfferingId = assignment.getCourseOffering().getId();

        return userAccountId != null
                && userAccountId.equals(facultyProfile.getUserAccount().getId())
                && facultyProfile.getStatus() == FacultyProfileStatus.ACTIVE
                && assignment.getStatus() == TeachingAssignmentStatus.ACTIVE
                && isOpenToFaculty(lmsCourse.getStatus())
                && courseOfferingId != null
                && courseOfferingId.equals(lmsCourse.getCourseOffering().getId());
    }
}
