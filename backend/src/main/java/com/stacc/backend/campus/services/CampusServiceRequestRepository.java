package com.stacc.backend.campus.services;

import java.util.Optional;

import com.stacc.backend.approval.ApprovalRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Campus service requests. Status filters match the effective status: a SUBMITTED request whose approval is decided or
 * cancelled counts as that outcome. The caller passes the status and, for APPROVED, REJECTED, and CANCELLED, the
 * approval status that leads to it ({@link CampusServiceRequestStatus#approvalEquivalent()}).
 */
public interface CampusServiceRequestRepository extends JpaRepository<CampusServiceRequest, Long> {

    /** Locks the request row. Its approval request is not loaded, so the engine can lock and read it fresh. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from CampusServiceRequest request where request.id = :requestId")
    Optional<CampusServiceRequest> findByIdForUpdate(@Param("requestId") Long requestId);

    @Query("""
            select request
            from CampusServiceRequest request
            join fetch request.serviceDefinition
            join fetch request.requester
            left join fetch request.approvalRequest
            where request.id = :requestId
            """)
    Optional<CampusServiceRequest> findWithContextById(@Param("requestId") Long requestId);

    /** One page of an account's own requests, newest first. */
    @Query(value = """
            select request
            from CampusServiceRequest request
            join fetch request.serviceDefinition service
            left join fetch request.approvalRequest approval
            where request.requester.id = :requesterId
              and (:serviceId is null or service.id = :serviceId)
              and (:status is null
                   or (request.status = :status
                       and (request.status <> com.stacc.backend.campus.services.CampusServiceRequestStatus.SUBMITTED
                            or approval is null
                            or approval.status = com.stacc.backend.approval.ApprovalRequestStatus.PENDING))
                   or (:approvalStatus is not null
                       and request.status = com.stacc.backend.campus.services.CampusServiceRequestStatus.SUBMITTED
                       and approval.status = :approvalStatus))
            order by request.submittedAt desc, request.id desc
            """,
            countQuery = """
            select count(request)
            from CampusServiceRequest request
            left join request.approvalRequest approval
            where request.requester.id = :requesterId
              and (:serviceId is null or request.serviceDefinition.id = :serviceId)
              and (:status is null
                   or (request.status = :status
                       and (request.status <> com.stacc.backend.campus.services.CampusServiceRequestStatus.SUBMITTED
                            or approval is null
                            or approval.status = com.stacc.backend.approval.ApprovalRequestStatus.PENDING))
                   or (:approvalStatus is not null
                       and request.status = com.stacc.backend.campus.services.CampusServiceRequestStatus.SUBMITTED
                       and approval.status = :approvalStatus))
            """)
    Page<CampusServiceRequest> findOwnPage(@Param("requesterId") Long requesterId, @Param("serviceId") Long serviceId,
            @Param("status") CampusServiceRequestStatus status,
            @Param("approvalStatus") ApprovalRequestStatus approvalStatus, Pageable page);

    /** One page of every request, newest first, narrowed by each filter that is given. */
    @Query(value = """
            select request
            from CampusServiceRequest request
            join fetch request.serviceDefinition service
            join fetch request.requester requester
            left join fetch request.approvalRequest approval
            where (:serviceId is null or service.id = :serviceId)
              and (:requesterLoginId is null or requester.loginId = :requesterLoginId)
              and (:status is null
                   or (request.status = :status
                       and (request.status <> com.stacc.backend.campus.services.CampusServiceRequestStatus.SUBMITTED
                            or approval is null
                            or approval.status = com.stacc.backend.approval.ApprovalRequestStatus.PENDING))
                   or (:approvalStatus is not null
                       and request.status = com.stacc.backend.campus.services.CampusServiceRequestStatus.SUBMITTED
                       and approval.status = :approvalStatus))
            order by request.submittedAt desc, request.id desc
            """,
            countQuery = """
            select count(request)
            from CampusServiceRequest request
            left join request.approvalRequest approval
            where (:serviceId is null or request.serviceDefinition.id = :serviceId)
              and (:requesterLoginId is null or request.requester.loginId = :requesterLoginId)
              and (:status is null
                   or (request.status = :status
                       and (request.status <> com.stacc.backend.campus.services.CampusServiceRequestStatus.SUBMITTED
                            or approval is null
                            or approval.status = com.stacc.backend.approval.ApprovalRequestStatus.PENDING))
                   or (:approvalStatus is not null
                       and request.status = com.stacc.backend.campus.services.CampusServiceRequestStatus.SUBMITTED
                       and approval.status = :approvalStatus))
            """)
    Page<CampusServiceRequest> findQueue(@Param("serviceId") Long serviceId,
            @Param("requesterLoginId") String requesterLoginId, @Param("status") CampusServiceRequestStatus status,
            @Param("approvalStatus") ApprovalRequestStatus approvalStatus, Pageable page);
}
