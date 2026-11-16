package com.stacc.backend.library;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryCopyRepository extends JpaRepository<LibraryCopy, Long> {

    @Query("select c from LibraryCopy c where c.title.id in :titleIds order by c.barcode")
    List<LibraryCopy> findForTitles(@Param("titleIds") Collection<Long> titleIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LibraryCopy c join fetch c.title where c.barcode = :barcode")
    Optional<LibraryCopy> findByBarcodeForUpdate(@Param("barcode") String barcode);

    /** Copies of a title that are not withdrawn, read with a locking read. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from LibraryCopy c where c.title.id = :titleId and c.withdrawnAt is null")
    List<LibraryCopy> findInServiceForShare(@Param("titleId") Long titleId);
}
