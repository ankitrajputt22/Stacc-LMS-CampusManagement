package com.stacc.backend.hostel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostelAllocationRepository extends JpaRepository<HostelAllocation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select allocation from HostelAllocation allocation where allocation.id = :allocationId")
    Optional<HostelAllocation> findByIdForUpdate(@Param("allocationId") Long allocationId);

    @Query("""
            select count(allocation) > 0 from HostelAllocation allocation
            where allocation.bed.id = :bedId and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE
            """)
    boolean existsActiveForBed(@Param("bedId") Long bedId);

    @Query("""
            select count(allocation) > 0 from HostelAllocation allocation
            where allocation.hostelApplication.id = :applicationId
              and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE
            """)
    boolean existsActiveForApplication(@Param("applicationId") Long applicationId);

    @Query("""
            select count(allocation) > 0 from HostelAllocation allocation
            where allocation.hostelApplication.studentProfile.id = :studentProfileId
              and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE
            """)
    boolean existsActiveForStudent(@Param("studentProfileId") Long studentProfileId);

    @Query("""
            select count(allocation) > 0 from HostelAllocation allocation
            where allocation.bed.room.id = :roomId and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE
            """)
    boolean existsActiveInRoom(@Param("roomId") Long roomId);

    @Query("""
            select count(allocation) > 0 from HostelAllocation allocation
            where allocation.bed.room.hostel.id = :hostelId
              and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE
            """)
    boolean existsActiveInHostel(@Param("hostelId") Long hostelId);

    @Query("select count(allocation) > 0 from HostelAllocation allocation where allocation.bed.room.id = :roomId")
    boolean existsAnyInRoom(@Param("roomId") Long roomId);

    @Query("select count(allocation) > 0 from HostelAllocation allocation where allocation.bed.id = :bedId")
    boolean existsAnyForBed(@Param("bedId") Long bedId);

    @Query("""
            select count(allocation) from HostelAllocation allocation
            where allocation.bed.room.hostel.id = :hostelId
              and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE
            """)
    long countActiveInHostel(@Param("hostelId") Long hostelId);

    @Query("""
            select allocation.bed.id from HostelAllocation allocation
            where allocation.bed.room.id = :roomId and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE
            """)
    List<Long> findOccupiedBedIdsInRoom(@Param("roomId") Long roomId);

    @Query("select distinct allocation.bed.id from HostelAllocation allocation where allocation.bed.room.id = :roomId")
    List<Long> findAllocatedBedIdsInRoom(@Param("roomId") Long roomId);

    @Query("""
            select allocation.bed.room.id as roomId, count(allocation) as total from HostelAllocation allocation
            where allocation.bed.room.hostel.id = :hostelId
              and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE
            group by allocation.bed.room.id
            """)
    List<HostelBedRepository.RoomCount> countOccupiedByRoom(@Param("hostelId") Long hostelId);

    @Query("select distinct allocation.bed.room.id from HostelAllocation allocation where allocation.bed.room.hostel.id = :hostelId")
    List<Long> findAllocatedRoomIdsInHostel(@Param("hostelId") Long hostelId);

    @Query("""
            select allocation
            from HostelAllocation allocation
            join fetch allocation.bed bed
            join fetch bed.room room
            join fetch room.hostel
            join fetch allocation.allocatedBy
            left join fetch allocation.vacatedBy
            where allocation.hostelApplication.id in :applicationIds
            order by allocation.allocatedAt asc, allocation.id asc
            """)
    List<HostelAllocation> findAllForApplications(@Param("applicationIds") Collection<Long> applicationIds);

    @Query("""
            select allocation
            from HostelAllocation allocation
            join fetch allocation.hostelApplication application
            join fetch application.studentProfile studentProfile
            join fetch studentProfile.userAccount
            join fetch application.academicSession
            join fetch allocation.bed bed
            join fetch bed.room room
            join fetch room.hostel
            join fetch allocation.allocatedBy
            left join fetch allocation.vacatedBy
            where allocation.id = :allocationId
            """)
    Optional<HostelAllocation> findWithContextById(@Param("allocationId") Long allocationId);

    @Query(value = """
            select allocation
            from HostelAllocation allocation
            join fetch allocation.hostelApplication application
            join fetch application.studentProfile studentProfile
            join fetch studentProfile.userAccount account
            join fetch application.academicSession academicSession
            join fetch allocation.bed bed
            join fetch bed.room room
            join fetch room.hostel hostel
            join fetch allocation.allocatedBy
            left join fetch allocation.vacatedBy
            where (:status is null or allocation.status = :status)
              and (:hostelId is null or hostel.id = :hostelId)
              and (:academicSessionId is null or academicSession.id = :academicSessionId)
              and (:studentLoginId is null or account.loginId = :studentLoginId)
            order by allocation.allocatedAt desc, allocation.id desc
            """,
            countQuery = """
            select count(allocation)
            from HostelAllocation allocation
            where (:status is null or allocation.status = :status)
              and (:hostelId is null or allocation.bed.room.hostel.id = :hostelId)
              and (:academicSessionId is null or allocation.hostelApplication.academicSession.id = :academicSessionId)
              and (:studentLoginId is null or allocation.hostelApplication.studentProfile.userAccount.loginId = :studentLoginId)
            """)
    Page<HostelAllocation> findPage(@Param("status") HostelAllocationStatus status, @Param("hostelId") Long hostelId,
            @Param("academicSessionId") Long academicSessionId, @Param("studentLoginId") String studentLoginId,
            Pageable page);

    @Query("select allocation.status as status, count(allocation) as total from HostelAllocation allocation group by allocation.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        HostelAllocationStatus getStatus();

        Long getTotal();
    }

    @Query("""
            select allocation from HostelAllocation allocation
            join fetch allocation.bed bed join fetch bed.room room join fetch room.hostel
            join allocation.hostelApplication application
            where application.studentProfile.userAccount.id = :accountId
              and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE
            """)
    Optional<HostelAllocation> findActiveForStudentAccount(@Param("accountId") Long accountId);

    interface HostelCount {
        Long getHostelId();
        long getCount();
    }

    @Query("""
            select bed.room.hostel.id as hostelId, count(allocation) as count from HostelAllocation allocation join allocation.bed bed
            where allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE group by bed.room.hostel.id
            """)
    List<HostelCount> countOccupiedByHostel();

    @Query("""
            select allocation from HostelAllocation allocation
            join fetch allocation.bed bed join fetch bed.room room
            join fetch allocation.hostelApplication application join fetch application.studentProfile profile join fetch profile.userAccount
            where room.hostel.id = :hostelId and allocation.status = com.stacc.backend.hostel.HostelAllocationStatus.ACTIVE
            """)
    List<HostelAllocation> findActiveInHostel(@Param("hostelId") Long hostelId);
}
