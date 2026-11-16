package com.stacc.backend.grievance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrievanceCaseRepository extends JpaRepository<GrievanceCase, Long> {

    @Query("select c from GrievanceCase c join fetch c.category where c.complainant.id = :accountId order by c.submittedAt desc, c.id desc")
    List<GrievanceCase> findForComplainant(@Param("accountId") Long accountId);

    /** Cases of these categories, other than the reader's own complaints. */
    @Query("""
            select c from GrievanceCase c join fetch c.category where c.category.id in :categoryIds and c.complainant.id <> :accountId
            order by c.submittedAt desc, c.id desc
            """)
    List<GrievanceCase> findForCommittee(@Param("categoryIds") Collection<Long> categoryIds, @Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GrievanceCase c join fetch c.category where c.id = :id")
    Optional<GrievanceCase> findByIdForUpdate(@Param("id") Long id);
}
