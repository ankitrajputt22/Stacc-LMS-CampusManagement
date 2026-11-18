package com.stacc.backend.approval;

public enum ApproverRelationship {
    STUDENT_DEPARTMENT_HEAD("HOD"),
    STUDENT_HOSTEL_WARDEN("WARDEN"),
    GROUP_ADVISOR("FACULTY");

    private final String roleCode;

    ApproverRelationship(String roleCode) {
        this.roleCode = roleCode;
    }

    public String roleCode() {
        return roleCode;
    }
}
