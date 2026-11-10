package com.stacc.backend.engagement.fests;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusFestRepository extends JpaRepository<CampusFest, Long> {

    @Query("select fest from CampusFest fest where fest.status = com.stacc.backend.engagement.fests.CampusFest.Status.PUBLISHED order by fest.startsOn, fest.id")
    List<CampusFest> findPublished();

    @Query("select fest from CampusFest fest order by fest.startsOn desc, fest.id desc")
    List<CampusFest> findAllNewestFirst();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select fest from CampusFest fest where fest.id = :festId")
    Optional<CampusFest> findByIdForUpdate(@Param("festId") Long festId);
}
