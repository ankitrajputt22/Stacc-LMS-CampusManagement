package com.stacc.backend.academic.admin;

/**
 * The method rules of the admin academic-master routes. Reading needs the admin role and the view permission;
 * changing needs the manage permission as well. There is no admin pass anywhere else.
 */
final class AdminAcademicAccess {

    static final String VIEW = "hasRole('ADMIN') and hasAuthority('ACADEMIC_MASTER_VIEW')";
    static final String MANAGE =
            "hasRole('ADMIN') and hasAuthority('ACADEMIC_MASTER_VIEW') and hasAuthority('ACADEMIC_MASTER_MANAGE')";

    private AdminAcademicAccess() {
    }
}
