package com.stacc.backend.report.api;

/**
 * The route rules of the report API. Faculty reports need the FACULTY role with FACULTY_REPORT_VIEW; admin reports the
 * ADMIN role with ADMIN_REPORT_VIEW. Neither role passes for the other, and a multi-role account may hold both.
 */
final class ReportAccess {

    static final String FACULTY = "hasRole('FACULTY') and hasAuthority('FACULTY_REPORT_VIEW')";
    static final String ADMIN = "hasRole('ADMIN') and hasAuthority('ADMIN_REPORT_VIEW')";

    private ReportAccess() {
    }
}
