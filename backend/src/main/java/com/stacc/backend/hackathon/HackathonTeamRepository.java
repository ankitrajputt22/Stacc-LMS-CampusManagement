package com.stacc.backend.hackathon;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HackathonTeamRepository extends JpaRepository<HackathonTeam, Long> {

    @Query("select t from HackathonTeam t where t.hackathon.id in :hackathonIds order by t.name, t.id")
    List<HackathonTeam> findForHackathons(@Param("hackathonIds") Collection<Long> hackathonIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from HackathonTeam t join fetch t.hackathon where t.id = :id")
    Optional<HackathonTeam> findByIdForUpdate(@Param("id") Long id);
}
