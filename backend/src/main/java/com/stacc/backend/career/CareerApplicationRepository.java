package com.stacc.backend.career;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareerApplicationRepository extends JpaRepository<CareerApplication, Long> {

    @Query("select a from CareerApplication a join fetch a.opportunity o left join fetch o.company where a.applicant.id = :accountId order by a.appliedAt desc")
    List<CareerApplication> findAllForApplicant(@Param("accountId") Long accountId);

    @Query("select a from CareerApplication a join fetch a.applicant left join fetch a.resume where a.opportunity.id = :id order by a.appliedAt asc")
    List<CareerApplication> findAllForOpportunity(@Param("id") Long opportunityId);

    boolean existsByOpportunityIdAndApplicantId(Long opportunityId, Long applicantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CareerApplication a where a.id = :id")
    Optional<CareerApplication> findByIdForUpdate(@Param("id") Long id);
}
