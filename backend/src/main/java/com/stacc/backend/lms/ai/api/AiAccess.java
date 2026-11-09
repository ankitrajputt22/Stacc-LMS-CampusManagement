package com.stacc.backend.lms.ai.api;

/** The route rules. Each needs the role, the LMS permission, and the AI permission together; there is no admin pass. */
final class AiAccess {

    static final String STUDENT = "hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW') and hasAuthority('AI_STUDY_USE')";
    static final String FACULTY = "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('AI_TEACHING_USE')";

    private AiAccess() {
    }
}
