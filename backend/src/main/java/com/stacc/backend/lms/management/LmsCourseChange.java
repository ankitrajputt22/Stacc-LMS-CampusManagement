package com.stacc.backend.lms.management;

import com.stacc.backend.lms.course.LmsCourseStatus;

/**
 * The state of an LMS course after a management change.
 *
 * @param currentStudentCount how many students are currently enrolled in the offering, or null for archiving
 */
public record LmsCourseChange(Long lmsCourseId, Long courseOfferingId, LmsCourseStatus status, Integer currentStudentCount) {
}
