package com.stacc.backend.lms.management;

import com.stacc.backend.lms.course.LmsCourseStatus;

public record LmsCourseChange(Long lmsCourseId, Long courseOfferingId, LmsCourseStatus status, Integer currentStudentCount) {
}
