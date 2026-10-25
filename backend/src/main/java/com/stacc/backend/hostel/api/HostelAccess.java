package com.stacc.backend.hostel.api;

/**
 * The route rules of the hostel API, in one place. Students reach only their own applications; the hostel office
 * needs the admin role with HOSTEL_MANAGE. Approval decisions are not here: they go through the approval task routes,
 * which check the step's role and APPROVAL_TASK_DECIDE. HOSTEL_MANAGE is no approval pass.
 */
final class HostelAccess {

    static final String APPLY = "hasRole('STUDENT') and hasAuthority('HOSTEL_APPLICATION_CREATE')";
    static final String VIEW_OWN = "hasRole('STUDENT') and hasAuthority('HOSTEL_APPLICATION_VIEW_OWN')";
    static final String CANCEL = "hasRole('STUDENT') and hasAuthority('HOSTEL_APPLICATION_CANCEL')";
    static final String MANAGE = "hasRole('ADMIN') and hasAuthority('HOSTEL_MANAGE')";

    private HostelAccess() {
    }
}
