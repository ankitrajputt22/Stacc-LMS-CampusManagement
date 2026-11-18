package com.stacc.backend.lms.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class AiJobMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private AiJobRepository jobs;

    @Autowired
    private LmsCourseRepository courses;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private TransactionTemplate transaction;

    @Test
    void jobsAreLiveOncePerCourseAndClaimedOnce() throws Exception {
        Long faculty = accounts.findByLoginId("emp1021").orElseThrow().getId();
        List<Long> courseIds = courses.findAll().stream().map(course -> course.getId()).limit(2).toList();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jobs.saveAndFlush(new AiJob(AiJob.Kind.REFRESH_INDEX, courseIds.get(0), faculty, now));
        assertThatThrownBy(() -> jobs.saveAndFlush(new AiJob(AiJob.Kind.REFRESH_INDEX, courseIds.get(0), faculty, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
        jobs.saveAndFlush(new AiJob(AiJob.Kind.REFRESH_INDEX, courseIds.get(1), faculty, now));

        List<Concurrently.Outcome<Long>> outcomes = Concurrently.run(
                () -> transaction.execute(status -> jobs.claimNext().map(job -> { job.start(now); return job.getId(); }).orElse(null)),
                () -> transaction.execute(status -> jobs.claimNext().map(job -> { job.start(now); return job.getId(); }).orElse(null)));

        List<Long> claimed = outcomes.stream().map(Concurrently.Outcome::result).filter(Objects::nonNull).toList();
        assertThat(claimed).doesNotHaveDuplicates();
        assertThat(claimed).isNotEmpty();
        Integer failed = transaction.execute(status -> jobs.failRunning(now));
        assertThat(failed).isGreaterThanOrEqualTo(claimed.size());
    }
}
