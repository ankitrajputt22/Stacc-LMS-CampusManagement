package com.stacc.backend.approval;

import java.util.Optional;

/**
 * Implemented by the module that owns a relationship (such as academic for heads of department), so the engine never
 * depends on business modules. It only reads.
 */
public interface ApproverRelationshipResolver {

    ApproverRelationship relationship();

    /** The account that decides for this requester now, or empty when there is none. */
    Optional<Long> approverFor(Long requesterAccountId);
}
