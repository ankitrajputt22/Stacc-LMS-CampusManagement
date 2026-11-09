package com.stacc.backend.engagement.facilities;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusFacilityRepository extends JpaRepository<CampusFacility, Long> {

    boolean existsByCode(String code);

    List<CampusFacility> findAllByOrderByNameAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from CampusFacility f where f.id = :id")
    Optional<CampusFacility> findByIdForUpdate(@Param("id") Long id);
}
