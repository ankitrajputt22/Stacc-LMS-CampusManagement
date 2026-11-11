package com.stacc.backend.hackathon;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HackathonJudgeRepository extends JpaRepository<HackathonJudge, Long> {

    @Query("select j from HackathonJudge j join fetch j.account where j.hackathon.id in :hackathonIds order by j.account.loginId")
    List<HackathonJudge> findForHackathons(@Param("hackathonIds") Collection<Long> hackathonIds);

    @Query("select j.hackathon.id from HackathonJudge j where j.account.id = :accountId")
    List<Long> findHackathonIdsFor(@Param("accountId") Long accountId);
}
