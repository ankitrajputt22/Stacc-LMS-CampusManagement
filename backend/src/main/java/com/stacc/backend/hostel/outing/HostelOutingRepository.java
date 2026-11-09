package com.stacc.backend.hostel.outing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostelOutingRepository extends JpaRepository<HostelOuting, Long> {

    @Query("select o from HostelOuting o where o.studentProfile.userAccount.id = :accountId order by o.createdAt desc, o.id desc")
    List<HostelOuting> findAllForStudentAccount(@Param("accountId") Long accountId, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from HostelOuting o where o.id = :id")
    Optional<HostelOuting> findByIdForUpdate(@Param("id") Long id);

    /** Outings not yet returned that started before now, of these students, for overdue checks. */
    @Query("""
            select o from HostelOuting o join fetch o.studentProfile profile join fetch profile.userAccount
            where profile.userAccount.id in :accountIds and o.returnedAt is null and o.leaveFrom <= CURRENT_TIMESTAMP
            order by o.leaveUntil asc
            """)
    List<HostelOuting> findOpenForStudents(@Param("accountIds") Collection<Long> accountIds);
}
