package com.stacc.backend.engagement.tournaments;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusTournamentRepository extends JpaRepository<CampusTournament, Long> {

    @Query("select t from CampusTournament t where t.status = com.stacc.backend.engagement.tournaments.CampusTournament.Status.PUBLISHED order by t.season desc, t.name, t.id")
    List<CampusTournament> findPublished();

    @Query("select t from CampusTournament t order by t.status, t.season desc, t.name, t.id")
    List<CampusTournament> findAllForOffice();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CampusTournament t where t.id = :id")
    Optional<CampusTournament> findByIdForUpdate(@Param("id") Long id);
}
