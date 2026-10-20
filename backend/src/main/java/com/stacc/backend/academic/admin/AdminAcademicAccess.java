package com.stacc.backend.academic.admin;

/**
 * The method rules of the admin academic routes. Reading needs the admin role and the view permission; changing
 * needs the manage permission as well. Masters, course offerings, enrollments, and the academic calendar each have
 * their own pair of permissions. There is no admin pass anywhere else.
 */
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

    private AdminAcademicAccess() {
    }
}
