package com.stacc.backend.library;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryLoanRepository extends JpaRepository<LibraryLoan, Long> {

    @Query("select l from LibraryLoan l where l.copy.id in :copyIds and l.returnedAt is null")
    List<LibraryLoan> findOpenForCopies(@Param("copyIds") Collection<Long> copyIds);

    @Query("select l from LibraryLoan l join fetch l.copy c join fetch c.title where l.borrower.id = :accountId and l.returnedAt is null order by l.dueAt, l.id")
    List<LibraryLoan> findOpenForBorrower(@Param("accountId") Long accountId);

    /** The borrower's open loans, read with a locking read after the borrower's account row lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LibraryLoan l where l.borrower.id = :accountId and l.returnedAt is null")
    List<LibraryLoan> findOpenForBorrowerForUpdate(@Param("accountId") Long accountId);

    /** Open loans on a title's copies, read with a locking read. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select l from LibraryLoan l where l.copy.title.id = :titleId and l.returnedAt is null")
    List<LibraryLoan> findOpenForTitleForShare(@Param("titleId") Long titleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LibraryLoan l join fetch l.borrower where l.copy.id = :copyId and l.returnedAt is null")
    Optional<LibraryLoan> findOpenForCopyForUpdate(@Param("copyId") Long copyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LibraryLoan l join fetch l.copy c join fetch c.title where l.id = :id")
    Optional<LibraryLoan> findByIdForUpdate(@Param("id") Long id);

    @Query("select l from LibraryLoan l join fetch l.copy c join fetch c.title join fetch l.borrower where l.returnedAt is null order by l.dueAt, l.id")
    List<LibraryLoan> findAllOpen();
}
