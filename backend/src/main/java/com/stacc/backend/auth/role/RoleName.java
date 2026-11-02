package com.stacc.backend.auth.role;

/**
 * The roles the college can give an account. An account may hold several. Staff roles (from HOD on) get the
 * permissions of their own work only when that work exists in Stacc; until then they open nothing but the account's
 * own pages.
 */
public enum RoleName {
    STUDENT,
    FACULTY,
    ADMIN,
    /** Head of a department. */
    HOD,
    WARDEN,
    SECURITY,
    MAINTENANCE,
    LIBRARIAN,
    PLACEMENT_OFFICER
}
