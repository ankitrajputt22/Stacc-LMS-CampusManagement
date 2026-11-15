package com.stacc.backend.lms.ai.job;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    /** The oldest queued job, locked; jobs another worker holds are skipped, so two workers never take the same one. */
    @Query(value = "select * from ai_jobs where status = 'QUEUED' order by id limit 1 for update skip locked", nativeQuery = true)
    Optional<AiJob> claimNext();

    Optional<AiJob> findFirstByLmsCourseIdAndKindOrderByIdDesc(Long lmsCourseId, AiJob.Kind kind);

    /** Jobs left running by a stopped process fail at start-up, so nothing waits on them forever. */
    @Modifying
    @Query("""
            update AiJob job set job.status = com.stacc.backend.lms.ai.job.AiJob.Status.FAILED, job.finishedAt = :at,
              job.errorMessage = 'Stopped by a restart. Start the refresh again.'
            where job.status = com.stacc.backend.lms.ai.job.AiJob.Status.RUNNING
            """)
    int failRunning(@Param("at") Instant at);
}
