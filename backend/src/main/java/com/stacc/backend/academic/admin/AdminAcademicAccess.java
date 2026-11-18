package com.stacc.backend.academic.admin;

final class AdminAcademicAccess {

    static final String VIEW = "hasRole('ADMIN') and hasAuthority('ACADEMIC_MASTER_VIEW')";
    static final String MANAGE =
            "hasRole('ADMIN') and hasAuthority('ACADEMIC_MASTER_VIEW') and hasAuthority('ACADEMIC_MASTER_MANAGE')";

    static final String OFFERING_VIEW = "hasRole('ADMIN') and hasAuthority('ACADEMIC_OFFERING_VIEW')";
    static final String OFFERING_MANAGE =
            "hasRole('ADMIN') and hasAuthority('ACADEMIC_OFFERING_VIEW') and hasAuthority('ACADEMIC_OFFERING_MANAGE')";

    static final String ENROLLMENT_VIEW = "hasRole('ADMIN') and hasAuthority('ACADEMIC_ENROLLMENT_VIEW')";
    static final String ENROLLMENT_MANAGE =
            "hasRole('ADMIN') and hasAuthority('ACADEMIC_ENROLLMENT_VIEW') and hasAuthority('ACADEMIC_ENROLLMENT_MANAGE')";

    static final String CALENDAR_VIEW = "hasRole('ADMIN') and hasAuthority('ACADEMIC_CALENDAR_VIEW')";
    static final String CALENDAR_MANAGE =
            "hasRole('ADMIN') and hasAuthority('ACADEMIC_CALENDAR_VIEW') and hasAuthority('ACADEMIC_CALENDAR_MANAGE')";

    static final String FACULTY_ASSIGNMENT_VIEW = "hasRole('ADMIN') and hasAuthority('FACULTY_ASSIGNMENT_VIEW')";
    static final String FACULTY_ASSIGNMENT_MANAGE =
            "hasRole('ADMIN') and hasAuthority('FACULTY_ASSIGNMENT_VIEW') and hasAuthority('FACULTY_ASSIGNMENT_MANAGE')";

    static final String DEPARTMENT_HEAD_VIEW = "hasRole('ADMIN') and hasAuthority('DEPARTMENT_HEAD_VIEW')";
    static final String DEPARTMENT_HEAD_MANAGE =
            "hasRole('ADMIN') and hasAuthority('DEPARTMENT_HEAD_VIEW') and hasAuthority('DEPARTMENT_HEAD_MANAGE')";

    static final String PROFILE_VIEW = "hasRole('ADMIN') and hasAuthority('ACADEMIC_PROFILE_VIEW')";
    static final String PROFILE_MANAGE =
            "hasRole('ADMIN') and hasAuthority('ACADEMIC_PROFILE_VIEW') and hasAuthority('ACADEMIC_PROFILE_MANAGE')";

    private AdminAcademicAccess() {
    }
}
