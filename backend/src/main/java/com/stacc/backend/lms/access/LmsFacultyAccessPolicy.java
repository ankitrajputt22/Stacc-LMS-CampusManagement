package com.stacc.backend.lms.access;

import java.util.Set;

import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentStatus;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseStatus;

/**
 * The one place that says when a teaching assignment currently lets a faculty member use an LMS
 * course. The single-course check and the faculty member's course list both ask here, so they
 * cannot disagree.
 *
 * <p>Faculty may use a draft course as well as a published one, because they prepare a course
 * before students see it. Students only ever get published courses; that rule is in
 * {@link LmsStudentAccessPolicy} and is not affected by this one.
 */
public final class LmsFacultyAccessPolicy {

    // Listed explicitly, so a status added later grants nothing until it is chosen here.
    private static final Set<LmsCourseStatus> OPEN_TO_FACULTY = Set.of(LmsCourseStatus.DRAFT, LmsCourseStatus.PUBLISHED);

    private LmsFacultyAccessPolicy() {
    }

    /** Whether an LMS course in this status can be used by its faculty at all. Archived courses cannot. */
    public static boolean isOpenToFaculty(LmsCourseStatus status) {
        return OPEN_TO_FACULTY.contains(status);
    }

    /**
     * True only when the assignment belongs to the faculty member behind this account and all of
     * these hold: the faculty profile is active, the teaching assignment is active, the LMS course
     * is a draft or published, and the assignment is for the same course offering as the LMS course.
     *
     * <p>It only reads what it is given. Offerings are compared by ID, because the two sides may
     * hold different Java objects for the same offering.
     */
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
