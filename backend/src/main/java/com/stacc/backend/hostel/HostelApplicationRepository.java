package com.stacc.backend.hostel;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Hostel applications. Their approval status is read from the linked approval request, never stored here. */
public interface HostelApplicationRepository extends JpaRepository<HostelApplication, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from HostelApplication application where application.id = :applicationId")
    Optional<HostelApplication> findByIdForUpdate(@Param("applicationId") Long applicationId);

    @Query("select application.studentProfile.id from HostelApplication application where application.id = :applicationId")
    Optional<Long> findStudentProfileIdById(@Param("applicationId") Long applicationId);

    @Query("""
            select application
            from HostelApplication application
            join fetch application.studentProfile studentProfile
            join fetch studentProfile.userAccount
            join fetch application.academicSession
            left join fetch application.approvalRequest
            where application.id = :applicationId
            """)
    Optional<HostelApplication> findWithContextById(@Param("applicationId") Long applicationId);

    /** A student's applications, newest first. */
    @Query("""
            select application
            from HostelApplication application
            join fetch application.academicSession
            left join fetch application.approvalRequest
            where application.studentProfile.id = :studentProfileId
            order by application.submittedAt desc, application.id desc
            """)
    List<HostelApplication> findAllForStudent(@Param("studentProfileId") Long studentProfileId);

    /** Whether the student has an application for the session whose approval is still PENDING or already APPROVED. */
    @Query("""
            select count(application) > 0
            from HostelApplication application
            where application.studentProfile.id = :studentProfileId
              and application.academicSession.id = :academicSessionId
              and application.approvalRequest.status in (
                  com.stacc.backend.approval.ApprovalRequestStatus.PENDING,
                  com.stacc.backend.approval.ApprovalRequestStatus.APPROVED)
            """)
    boolean existsCurrent(@Param("studentProfileId") Long studentProfileId,
            @Param("academicSessionId") Long academicSessionId);

    /** The sessions in which the student has a PENDING or APPROVED application. */
    @Query("""
            select distinct application.academicSession.id
            from HostelApplication application
            where application.studentProfile.id = :studentProfileId
              and application.approvalRequest.status in (
                  com.stacc.backend.approval.ApprovalRequestStatus.PENDING,
                  com.stacc.backend.approval.ApprovalRequestStatus.APPROVED)
            """)
    List<Long> findSessionIdsWithCurrentApplication(@Param("studentProfileId") Long studentProfileId);

    /** One page of every application, newest first, narrowed by each filter that is given. */
    @Query(value = """
            select application
            from HostelApplication application
            join fetch application.studentProfile studentProfile
            join fetch studentProfile.userAccount account
            join fetch application.academicSession academicSession
            join fetch application.approvalRequest approval
            where (:academicSessionId is null or academicSession.id = :academicSessionId)
              and (:approvalStatus is null or approval.status = :approvalStatus)
              and (:studentLoginId is null or account.loginId = :studentLoginId)
              and (:hasActiveAllocation is null
                   or (:hasActiveAllocation = true and exists (
                       select allocation.id from HostelAllocation allocation
                       where allocation.hostelApplication = application
                         and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE))
                   or (:hasActiveAllocation = false and not exists (
                       select allocation.id from HostelAllocation allocation
                       where allocation.hostelApplication = application
                         and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE)))
            order by application.submittedAt desc, application.id desc
            """,
            countQuery = """
            select count(application)
            from HostelApplication application
            where (:academicSessionId is null or application.academicSession.id = :academicSessionId)
              and (:approvalStatus is null or application.approvalRequest.status = :approvalStatus)
              and (:studentLoginId is null or application.studentProfile.userAccount.loginId = :studentLoginId)
              and (:hasActiveAllocation is null
                   or (:hasActiveAllocation = true and exists (
                       select allocation.id from HostelAllocation allocation
                       where allocation.hostelApplication = application
                         and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE))
                   or (:hasActiveAllocation = false and not exists (
                       select allocation.id from HostelAllocation allocation
                       where allocation.hostelApplication = application
                         and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE)))
            """)
    Page<HostelApplication> findQueue(@Param("academicSessionId") Long academicSessionId,
            @Param("approvalStatus") ApprovalRequestStatus approvalStatus,
            @Param("studentLoginId") String studentLoginId,
            @Param("hasActiveAllocation") Boolean hasActiveAllocation, Pageable page);
}
