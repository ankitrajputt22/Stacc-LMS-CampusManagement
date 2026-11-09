package com.stacc.backend.approval;

/**
 * A relationship to the requester that names one approver for a step, resolved once when a request starts. Each needs
 * an {@link ApproverRelationshipResolver} from the module that owns the relationship, and the role it is checked with.
 */
public enum ApproverRelationship {
    /** The current head of the department of the requesting student's program. Decided with the HOD role. */
    STUDENT_DEPARTMENT_HEAD("HOD");

    private final String roleCode;

    ApproverRelationship(String roleCode) {
        this.roleCode = roleCode;
    }

    /** The role the approver must also hold. */
    public String roleCode() {
        return roleCode;
    }
}
