package com.stacc.backend.engagement.tournaments;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusFixtureRepository extends JpaRepository<CampusFixture, Long> {

    @Query("""
            select f from CampusFixture f left join fetch f.homeTeam left join fetch f.awayTeam
            where f.tournament.id in :tournamentIds order by f.startsAt, f.id
            """)
    List<CampusFixture> findForTournaments(@Param("tournamentIds") Collection<Long> tournamentIds);

    @Query("select f.tournament.id from CampusFixture f where f.id = :id")
    Optional<Long> findTournamentIdOf(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from CampusFixture f where f.id = :id")
    Optional<CampusFixture> findByIdForUpdate(@Param("id") Long id);
}
