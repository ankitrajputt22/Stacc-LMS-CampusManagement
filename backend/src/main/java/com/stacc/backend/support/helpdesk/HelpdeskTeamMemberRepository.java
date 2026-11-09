package com.stacc.backend.support.helpdesk;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HelpdeskTeamMemberRepository extends JpaRepository<HelpdeskTeamMember, Long> {

    /** Whether the account is a member of the active team. */
    @Query("select count(m) > 0 from HelpdeskTeamMember m where m.team.id = :teamId and m.account.id = :accountId and m.team.active = true")
    boolean isActiveMember(@Param("teamId") Long teamId, @Param("accountId") Long accountId);

    /** The active teams the account belongs to. */
    @Query("select m.team.id from HelpdeskTeamMember m where m.account.id = :accountId and m.team.active = true")
    List<Long> findActiveTeamIds(@Param("accountId") Long accountId);

    @Query("select m from HelpdeskTeamMember m join fetch m.account where m.team.id = :teamId order by m.account.loginId")
    List<HelpdeskTeamMember> findAllWithAccountByTeamId(@Param("teamId") Long teamId);

    @Query("select m from HelpdeskTeamMember m join fetch m.account join fetch m.team order by m.team.id, m.account.loginId")
    List<HelpdeskTeamMember> findAllWithAccountAndTeam();
}
