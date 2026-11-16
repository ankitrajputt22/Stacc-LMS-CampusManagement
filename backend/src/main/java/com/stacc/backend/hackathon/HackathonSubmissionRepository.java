package com.stacc.backend.hackathon;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HackathonSubmissionRepository extends JpaRepository<HackathonSubmission, Long> {

    @Query("select s from HackathonSubmission s join fetch s.team where s.team.id in :teamIds")
    List<HackathonSubmission> findForTeams(@Param("teamIds") Collection<Long> teamIds);

    @Query("select s from HackathonSubmission s where s.team.id = :teamId")
    Optional<HackathonSubmission> findForTeam(@Param("teamId") Long teamId);
}
