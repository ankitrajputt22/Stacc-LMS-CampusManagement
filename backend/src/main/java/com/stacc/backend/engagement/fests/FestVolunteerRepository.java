package com.stacc.backend.engagement.fests;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestVolunteerRepository extends JpaRepository<FestVolunteer, Long> {

    @Query("select volunteer from FestVolunteer volunteer join fetch volunteer.account where volunteer.shift.id in :shiftIds order by volunteer.appliedAt, volunteer.id")
    List<FestVolunteer> findForShifts(@Param("shiftIds") Collection<Long> shiftIds);

    /** Accepted volunteers of a shift, read with a locking read. Called while the shift row is locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select volunteer from FestVolunteer volunteer where volunteer.shift.id = :shiftId and volunteer.status = com.stacc.backend.engagement.fests.FestVolunteer.Status.ACCEPTED")
    List<FestVolunteer> findAcceptedForUpdate(@Param("shiftId") Long shiftId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select volunteer from FestVolunteer volunteer join fetch volunteer.shift join fetch volunteer.account where volunteer.id = :volunteerId")
    Optional<FestVolunteer> findByIdForUpdate(@Param("volunteerId") Long volunteerId);

    @Query("select volunteer from FestVolunteer volunteer join fetch volunteer.shift shift join fetch shift.fest where volunteer.account.id = :accountId order by shift.startsAt")
    List<FestVolunteer> findForAccount(@Param("accountId") Long accountId);

    /** Accepted volunteering of the account, with shift and fest. */
    @Query("select v from FestVolunteer v join fetch v.shift s join fetch s.fest where v.account.id = :accountId and v.status = com.stacc.backend.engagement.fests.FestVolunteer.Status.ACCEPTED order by s.startsAt desc")
    List<FestVolunteer> findAcceptedByAccount(@Param("accountId") Long accountId);
}
