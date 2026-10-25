package com.stacc.backend.approval;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The steps of approval requests. */
public interface ApprovalRequestStepRepository extends JpaRepository<ApprovalRequestStep, Long> {

    /** Which request a step belongs to, without loading the step, so the request can be locked before it is read. */
    @Query("select step.approvalRequest.id from ApprovalRequestStep step where step.id = :stepId")
    Optional<Long> findRequestIdByStepId(@Param("stepId") Long stepId);

    @Query("""
            select step
            from ApprovalRequestStep step
            left join fetch step.actedBy
            where step.approvalRequest.id = :requestId
            order by step.stepOrder asc
            """)
    List<ApprovalRequestStep> findAllWithActorByRequestId(@Param("requestId") Long requestId);

    /**
     * One page of the steps an approver can decide now: PENDING steps of PENDING requests that need one of these roles,
     * leaving out the approver's own requests. Oldest first, then by step ID.
     */
    @Query(value = """
            select step
            from ApprovalRequestStep step
            join fetch step.approvalRequest request
            join fetch request.workflowDefinition
            join fetch request.requester requester
            where step.status = com.stacc.backend.approval.ApprovalRequestStepStatus.PENDING
              and request.status = com.stacc.backend.approval.ApprovalRequestStatus.PENDING
              and step.requiredRoleCode in :roleCodes
              and requester.id <> :approverId
            order by step.createdAt asc, step.id asc
            """,
            countQuery = """
            select count(step)
            from ApprovalRequestStep step
            where step.status = com.stacc.backend.approval.ApprovalRequestStepStatus.PENDING
              and step.approvalRequest.status = com.stacc.backend.approval.ApprovalRequestStatus.PENDING
              and step.requiredRoleCode in :roleCodes
              and step.approvalRequest.requester.id <> :approverId
            """)
    Page<ApprovalRequestStep> findTasks(@Param("roleCodes") Collection<String> roleCodes,
            @Param("approverId") Long approverId, Pageable page);
}
