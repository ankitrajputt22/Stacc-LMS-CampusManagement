package com.stacc.backend.approval;

import java.util.Optional;

public interface ApproverRelationshipResolver {

    ApproverRelationship relationship();

    Optional<Long> approverFor(Long requesterAccountId);

    default Optional<Long> approverFor(Long requesterAccountId, String sourceType, String sourceId) {
        return approverFor(requesterAccountId);
    }
}
