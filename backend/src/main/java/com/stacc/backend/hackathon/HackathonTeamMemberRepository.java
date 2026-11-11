package com.stacc.backend.hackathon;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HackathonTeamMemberRepository extends JpaRepository<HackathonTeamMember, Long> {

    @Query("select m from HackathonTeamMember m join fetch m.account where m.team.id in :teamIds and m.leftAt is null order by m.joinedAt, m.id")
    List<HackathonTeamMember> findCurrentFor(@Param("teamIds") Collection<Long> teamIds);

    /** Current members of a team, read with a locking read after the team row lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from HackathonTeamMember m where m.team.id = :teamId and m.leftAt is null")
    List<HackathonTeamMember> findCurrentForUpdate(@Param("teamId") Long teamId);

    @Query("select m from HackathonTeamMember m join fetch m.team where m.hackathonId = :hackathonId and m.account.id = :accountId and m.leftAt is null")
    Optional<HackathonTeamMember> findCurrent(@Param("hackathonId") Long hackathonId, @Param("accountId") Long accountId);
}
