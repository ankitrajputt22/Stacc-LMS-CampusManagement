package com.stacc.backend.academic.enrollment;

public enum CourseEnrollmentStatus {
    ENROLLED,
    COMPLETED,
    WITHDRAWN,
    /** The student dropped the course before the semester's drop deadline. */
    DROPPED
}
