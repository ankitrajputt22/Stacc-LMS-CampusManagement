package com.stacc.backend.hostel.visitor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostelVisitRepository extends JpaRepository<HostelVisit, Long> {

    /** A day's visits at every hostel, with the resident and their hostel, for the security desk. */
    @Query("""
            select visit from HostelVisit visit join fetch visit.allocation allocation
            join fetch allocation.bed bed join fetch bed.room room join fetch room.hostel
            join fetch allocation.hostelApplication application join fetch application.studentProfile profile
            join fetch profile.userAccount
            where visit.expectedOn = :day order by room.hostel.name, visit.id
            """)
    List<HostelVisit> findForDay(@Param("day") LocalDate day);

    /** The resident's visits from a date on, newest first. */
    @Query("""
            select visit from HostelVisit visit join visit.allocation allocation
            where allocation.hostelApplication.studentProfile.userAccount.id = :accountId and visit.expectedOn >= :from
            order by visit.expectedOn desc, visit.id desc
            """)
    List<HostelVisit> findOwnFrom(@Param("accountId") Long accountId, @Param("from") LocalDate from);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select visit from HostelVisit visit where visit.id = :visitId")
    Optional<HostelVisit> findByIdForUpdate(@Param("visitId") Long visitId);
}
