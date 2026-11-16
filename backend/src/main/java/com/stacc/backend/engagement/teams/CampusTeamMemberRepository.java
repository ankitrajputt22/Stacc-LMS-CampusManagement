package com.stacc.backend.engagement.teams;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusTeamMemberRepository extends JpaRepository<CampusTeamMember, Long> {

    /** Current players of these teams, with their accounts. */
    @Query("select m from CampusTeamMember m join fetch m.account where m.team.id in :teamIds and m.leftAt is null order by m.team.id, m.joinedAt, m.id")
    List<CampusTeamMember> findCurrentForTeams(@Param("teamIds") Collection<Long> teamIds);

    /** Current players of a team, read with a locking read after the team row lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CampusTeamMember m join fetch m.account where m.team.id = :teamId and m.leftAt is null")
    List<CampusTeamMember> findCurrentForUpdate(@Param("teamId") Long teamId);

    @Query("select m.team.id from CampusTeamMember m where m.id = :memberId")
    Optional<Long> findTeamIdOf(@Param("memberId") Long memberId);

    /** Every roster place the account has held, with team and group. */
    @Query("select m from CampusTeamMember m join fetch m.team t join fetch t.group where m.account.id = :accountId order by m.joinedAt desc")
    List<CampusTeamMember> findForAccount(@Param("accountId") Long accountId);
}
