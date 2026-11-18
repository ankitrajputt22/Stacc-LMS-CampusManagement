package com.stacc.backend.report.api;

final class ReportAccess {

    static final String FACULTY = "hasRole('FACULTY') and hasAuthority('FACULTY_REPORT_VIEW')";
    static final String ADMIN = "hasRole('ADMIN') and hasAuthority('ADMIN_REPORT_VIEW')";

    private ReportAccess() {
    }
}
