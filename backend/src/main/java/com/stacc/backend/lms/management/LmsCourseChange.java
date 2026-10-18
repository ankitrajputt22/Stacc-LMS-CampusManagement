package com.stacc.backend.lms.management;

import com.stacc.backend.lms.course.LmsCourseStatus;

/**
 * The state of an LMS course after a management change.
 *
 * @param synchronizedCount how many current students have an active membership after the change, or null when the
 *                          change did not synchronize memberships
 */
public record LmsCourseChange(Long lmsCourseId, Long courseOfferingId, LmsCourseStatus status, Integer synchronizedCount) {
}
