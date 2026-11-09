package com.stacc.backend.approval;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Approval requests. Every change locks the request's own row first. */
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    /**
     * One request, locked until the transaction ends ({@code SELECT ... FOR UPDATE}). Decisions and cancellations take
     * this lock before they read the steps, so two decisions on the same request take turns and the second one sees the
     * first one's result.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ApprovalRequest request where request.id = :requestId")
    Optional<ApprovalRequest> findByIdForUpdate(@Param("requestId") Long requestId);

    @Query("""
            select request
            from ApprovalRequest request
            join fetch request.workflowDefinition
            join fetch request.requester
            where request.id = :requestId
            """)
    Optional<ApprovalRequest> findWithContextById(@Param("requestId") Long requestId);

    /** One page of the requests one account made, newest first, narrowed by status when it is given. */
    @Query(value = """
            select request
            from ApprovalRequest request
            join fetch request.workflowDefinition
            where request.requester.id = :requesterId
              and (:status is null or request.status = :status)
            order by request.createdAt desc, request.id desc
            """,
            countQuery = """
            select count(request)
            from ApprovalRequest request
            where request.requester.id = :requesterId
              and (:status is null or request.status = :status)
            """)
    Page<ApprovalRequest> findOwn(@Param("requesterId") Long requesterId, @Param("status") ApprovalRequestStatus status,
            Pageable page);

    /** Every request for one business record, oldest first, for the module that owns the record. */
    @Query("""
            select request
            from ApprovalRequest request
            join fetch request.workflowDefinition
            where request.sourceType = :sourceType and request.sourceId = :sourceId
            order by request.createdAt asc, request.id asc
            """)
    List<ApprovalRequest> findAllForSource(@Param("sourceType") String sourceType, @Param("sourceId") String sourceId);

    @Query("""
            select count(request) > 0
            from ApprovalRequest request
            where request.sourceType = :sourceType
              and request.sourceId = :sourceId
              and request.workflowDefinition.code = :workflowCode
              and request.status = com.stacc.backend.approval.ApprovalRequestStatus.PENDING
            """)
    boolean existsPendingForSource(@Param("sourceType") String sourceType, @Param("sourceId") String sourceId,
            @Param("workflowCode") String workflowCode);
}
