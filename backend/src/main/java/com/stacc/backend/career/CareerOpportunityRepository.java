package com.stacc.backend.career;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareerOpportunityRepository extends JpaRepository<CareerOpportunity, Long> {

    @Query("select o from CareerOpportunity o left join fetch o.company where o.status = :status order by o.deadline asc")
    List<CareerOpportunity> findAllByStatus(@Param("status") CareerOpportunity.Status status);

    @Query("select o from CareerOpportunity o left join fetch o.company order by o.createdAt desc")
    List<CareerOpportunity> findAllForManagers();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CareerOpportunity o where o.id = :id")
    Optional<CareerOpportunity> findByIdForUpdate(@Param("id") Long id);
}
