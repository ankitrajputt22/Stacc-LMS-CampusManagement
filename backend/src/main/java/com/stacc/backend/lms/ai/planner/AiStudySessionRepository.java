package com.stacc.backend.lms.ai.planner;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiStudySessionRepository extends JpaRepository<AiStudySession, Long> {

    List<AiStudySession> findTop50ByUserAccountIdAndPlannedOnLessThanEqualOrderByPlannedOnAscIdAsc(Long userAccountId, LocalDate day);

    boolean existsByUserAccountIdAndLmsCourseIdAndPlannedOnAndTitle(Long userAccountId, Long lmsCourseId, LocalDate plannedOn, String title);
}
