package com.stacc.backend.lms.ai.api;

final class AiAccess {

    static final String STUDENT = "hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW') and hasAuthority('AI_STUDY_USE')";
    static final String FACULTY = "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('AI_TEACHING_USE')";
    static final String FACULTY_BANK = FACULTY + " and hasAuthority('LMS_QUESTION_BANK_MANAGE')";
    static final String FACULTY_QUIZ = FACULTY_BANK + " and hasAuthority('LMS_QUIZ_MANAGE')";
    static final String FACULTY_PAPER = FACULTY_BANK + " and hasAuthority('LMS_EXAM_PAPER_MANAGE')";

    private AiAccess() {
    }
}
