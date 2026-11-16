package com.stacc.backend.hostel.rollcall;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostelRollCallEntryRepository extends JpaRepository<HostelRollCallEntry, Long> {

    /** A sheet's residents by room and bed, with the student's account. */
    @Query("""
            select entry from HostelRollCallEntry entry join fetch entry.allocation allocation
            join fetch allocation.bed bed join fetch bed.room room
            join fetch allocation.hostelApplication application join fetch application.studentProfile profile
            join fetch profile.userAccount
            where entry.rollCall.id = :rollCallId order by room.roomNumber, bed.bedNumber, entry.id
            """)
    List<HostelRollCallEntry> findAllForRollCall(@Param("rollCallId") Long rollCallId);

    /** How many residents of each sheet are in each status. */
    @Query("""
            select entry.rollCall.id as rollCallId, entry.status as status, count(entry) as residents
            from HostelRollCallEntry entry where entry.rollCall.id in :rollCallIds group by entry.rollCall.id, entry.status
            """)
    List<StatusCount> countByStatus(@Param("rollCallIds") Collection<Long> rollCallIds);

    interface StatusCount {
        Long getRollCallId();

        HostelRollCallEntry.Status getStatus();

        long getResidents();
    }
}
