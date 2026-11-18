package com.stacc.backend.hostel.api;

final class HostelAccess {

    static final String APPLY = "hasRole('STUDENT') and hasAuthority('HOSTEL_APPLICATION_CREATE')";
    static final String VIEW_OWN = "hasRole('STUDENT') and hasAuthority('HOSTEL_APPLICATION_VIEW_OWN')";
    static final String CANCEL = "hasRole('STUDENT') and hasAuthority('HOSTEL_APPLICATION_CANCEL')";
    static final String MANAGE = "hasRole('ADMIN') and hasAuthority('HOSTEL_MANAGE')";
    static final String WARDEN = "hasRole('WARDEN') and hasAuthority('HOSTEL_WARDEN_VIEW')";

    private HostelAccess() {
    }
}
