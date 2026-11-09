package com.stacc.backend.engagement.groups;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusGroupRepository extends JpaRepository<CampusGroup, Long> {

    boolean existsByCode(String code);

    List<CampusGroup> findAllByOrderByNameAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from CampusGroup g where g.id = :id")
    Optional<CampusGroup> findByIdForUpdate(@Param("id") Long id);
}
