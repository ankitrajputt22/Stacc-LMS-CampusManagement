package com.stacc.backend.hackathon;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HackathonScoreRepository extends JpaRepository<HackathonScore, Long> {

    @Query("select s from HackathonScore s join fetch s.judge where s.submission.id in :submissionIds")
    List<HackathonScore> findForSubmissions(@Param("submissionIds") Collection<Long> submissionIds);

    @Query("select s from HackathonScore s where s.submission.id = :submissionId and s.judge.id = :judgeId")
    Optional<HackathonScore> findOne(@Param("submissionId") Long submissionId, @Param("judgeId") Long judgeId);
}
