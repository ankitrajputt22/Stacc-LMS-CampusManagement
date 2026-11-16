package com.stacc.backend.hostel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostelRoomChangeRepository extends JpaRepository<HostelRoomChange, Long> {

    /** The room changes where the account is the requester or the swap partner, newest first, with both sides. */
    @Query("""
            select change from HostelRoomChange change
            join fetch change.requesterAllocation requester join fetch requester.bed requesterBed
            join fetch requesterBed.room requesterRoom join fetch requesterRoom.hostel
            join fetch requester.hostelApplication requesterApplication join fetch requesterApplication.studentProfile requesterProfile
            join fetch requesterProfile.userAccount requesterAccount
            left join fetch change.partnerAllocation partner left join fetch partner.bed partnerBed
            left join fetch partnerBed.room partnerRoom left join fetch partnerRoom.hostel
            left join fetch partner.hostelApplication partnerApplication left join fetch partnerApplication.studentProfile partnerProfile
            left join fetch partnerProfile.userAccount partnerAccount
            where requesterAccount.id = :accountId or partnerAccount.id = :accountId
            order by change.createdAt desc, change.id desc
            """)
    List<HostelRoomChange> findAllForAccount(@Param("accountId") Long accountId);

    /** Every room change in these statuses, oldest first, with both sides. */
    @Query("""
            select change from HostelRoomChange change
            join fetch change.requesterAllocation requester join fetch requester.bed requesterBed
            join fetch requesterBed.room requesterRoom join fetch requesterRoom.hostel
            join fetch requester.hostelApplication requesterApplication join fetch requesterApplication.studentProfile requesterProfile
            join fetch requesterProfile.userAccount
            left join fetch change.partnerAllocation partner left join fetch partner.bed partnerBed
            left join fetch partnerBed.room partnerRoom left join fetch partnerRoom.hostel
            left join fetch partner.hostelApplication partnerApplication left join fetch partnerApplication.studentProfile partnerProfile
            left join fetch partnerProfile.userAccount
            where change.status in :statuses
            order by change.createdAt, change.id
            """)
    List<HostelRoomChange> findAllInStatuses(@Param("statuses") Collection<HostelRoomChange.Status> statuses);

    /** Whether an open room change involves either allocation, on either side. Called under the student row locks. */
    @Query("""
            select count(change) > 0 from HostelRoomChange change
            where change.status = com.stacc.backend.hostel.HostelRoomChange.Status.OPEN
              and (change.requesterAllocation.id in :allocationIds or change.partnerAllocation.id in :allocationIds)
            """)
    boolean existsOpenFor(@Param("allocationIds") Collection<Long> allocationIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select change from HostelRoomChange change where change.id = :changeId")
    Optional<HostelRoomChange> findByIdForUpdate(@Param("changeId") Long changeId);
}
