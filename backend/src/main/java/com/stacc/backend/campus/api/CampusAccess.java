package com.stacc.backend.campus.api;

/**
 * The route rules of the campus API, in one place. Students and faculty request services and manage their own
 * requests; every role holding CAMPUS_NOTICE_VIEW reads the notice feed; admins manage services, requests, and notices
 * with their own permissions. Approval decisions are not here: they go through the approval task routes, which check
 * the step's role and APPROVAL_TASK_DECIDE. CAMPUS_SERVICE_MANAGE is no approval pass.
 */
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
