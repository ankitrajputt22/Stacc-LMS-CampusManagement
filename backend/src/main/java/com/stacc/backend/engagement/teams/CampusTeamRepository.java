package com.stacc.backend.engagement.teams;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusTeamRepository extends JpaRepository<CampusTeam, Long> {

    @Query("select t from CampusTeam t where t.group.id = :groupId order by t.active desc, t.season desc, t.name, t.id")
    List<CampusTeam> findForGroup(@Param("groupId") Long groupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CampusTeam t join fetch t.group where t.id = :id")
    Optional<CampusTeam> findByIdForUpdate(@Param("id") Long id);
}
