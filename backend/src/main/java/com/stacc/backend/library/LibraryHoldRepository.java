package com.stacc.backend.library;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryHoldRepository extends JpaRepository<LibraryHold, Long> {

    @Query("select h from LibraryHold h where h.title.id in :titleIds and h.status = com.stacc.backend.library.LibraryHold.Status.ACTIVE order by h.placedAt, h.id")
    List<LibraryHold> findActiveForTitles(@Param("titleIds") Collection<Long> titleIds);

    /** A title's active holds in queue order, read with a locking read. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from LibraryHold h where h.title.id = :titleId and h.status = com.stacc.backend.library.LibraryHold.Status.ACTIVE order by h.placedAt, h.id")
    List<LibraryHold> findActiveForTitleForUpdate(@Param("titleId") Long titleId);

    @Query("select h from LibraryHold h join fetch h.title where h.account.id = :accountId and h.status = com.stacc.backend.library.LibraryHold.Status.ACTIVE order by h.placedAt, h.id")
    List<LibraryHold> findActiveForAccount(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from LibraryHold h join fetch h.title where h.id = :id")
    Optional<LibraryHold> findByIdForUpdate(@Param("id") Long id);
}
