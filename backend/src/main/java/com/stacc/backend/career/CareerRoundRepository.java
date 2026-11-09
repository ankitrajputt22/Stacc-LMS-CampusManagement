package com.stacc.backend.career;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareerRoundRepository extends JpaRepository<CareerRound, Long> {

    @Query("select r from CareerRound r where r.opportunity.id in :ids order by r.stageIndex")
    List<CareerRound> findAllForOpportunities(@Param("ids") Collection<Long> opportunityIds);

    @Query("select r from CareerRound r where r.opportunity.id = :id and r.stageIndex = :stage")
    Optional<CareerRound> findOne(@Param("id") Long opportunityId, @Param("stage") int stageIndex);
}
