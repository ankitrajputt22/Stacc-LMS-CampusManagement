package com.stacc.backend.lms.ai.job;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The short transactions of an AI job's life: queue, claim, finish, and recover. The work itself runs outside them. */
@Service
public class AiJobSteps {

    private final AiJobRepository jobs;

    public AiJobSteps(AiJobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public AiJob queue(AiJob job) {
        return jobs.saveAndFlush(job);
    }

    /** Claims the oldest queued job with FOR UPDATE SKIP LOCKED and marks it running. */
    @Transactional
    public Optional<AiJob> claim(Instant now) {
        return jobs.claimNext().map(job -> {
            job.start(now);
            return job;
        });
    }

    @Transactional
    public void finish(Long jobId, String summary, String error, Instant now) {
        jobs.findById(jobId).ifPresent(job -> {
            if (error == null) {
                job.succeed(summary, now);
            } else {
                job.fail(error, now);
            }
        });
    }

    @Transactional
    public int recover(Instant now) {
        return jobs.failRunning(now);
    }
}
