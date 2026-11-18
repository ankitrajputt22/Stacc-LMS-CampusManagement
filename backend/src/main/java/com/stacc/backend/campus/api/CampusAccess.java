package com.stacc.backend.campus.api;

final class CampusAccess {

    static final String REQUEST = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('CAMPUS_SERVICE_REQUEST_CREATE')";
    static final String VIEW_OWN = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('CAMPUS_SERVICE_REQUEST_VIEW_OWN')";
    static final String CANCEL = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('CAMPUS_SERVICE_REQUEST_CANCEL')";
    static final String MANAGE_SERVICES = "hasRole('ADMIN') and hasAuthority('CAMPUS_SERVICE_MANAGE')";
    static final String VIEW_NOTICES = "hasAnyRole('STUDENT', 'FACULTY', 'ADMIN') and hasAuthority('CAMPUS_NOTICE_VIEW')";
    static final String MANAGE_NOTICES = "hasRole('ADMIN') and hasAuthority('CAMPUS_NOTICE_MANAGE')";

    private CampusAccess() {
    }
}
