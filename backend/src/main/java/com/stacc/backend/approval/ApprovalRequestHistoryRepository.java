package com.stacc.backend.approval;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The append-only history of approval requests. Nothing here changes or deletes an entry. */
public interface ApprovalRequestHistoryRepository extends JpaRepository<ApprovalRequestHistory, Long> {

    @Query("""
            select entry
            from ApprovalRequestHistory entry
            left join fetch entry.actor
            where entry.approvalRequest.id = :requestId
            order by entry.createdAt asc, entry.id asc
            """)
    List<ApprovalRequestHistory> findAllWithActorByRequestId(@Param("requestId") Long requestId);
}
