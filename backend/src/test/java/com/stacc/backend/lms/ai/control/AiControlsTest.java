package com.stacc.backend.lms.ai.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import com.stacc.backend.Concurrently;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The per-course switch and the request limits. */
class AiControlsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T05:00:00Z"), ZoneOffset.UTC);

    @Test
    void aStudentsSecondRequestWaitsForTheFirstAndThenGivesUp() throws Exception {
        AiRequestLimiter limiter = new AiRequestLimiter(new AiLimitProperties(4, Duration.ofMillis(200), null));
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        List<Concurrently.Outcome<String>> outcomes = Concurrently.run(
                () -> limiter.run(1L, () -> {
                    started.countDown();
                    await(release, 2000);
                    return "first";
                }),
                () -> {
                    await(started, 2000);
                    try {
                        return limiter.run(1L, () -> "second");
                    } finally {
                        release.countDown();
                    }
                });
        assertThat(outcomes.get(0).result()).isEqualTo("first");
        assertThat(outcomes.get(1).failure()).isInstanceOfSatisfying(ApiException.class, refused -> {
            assertThat(refused.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(refused.getMessage()).isEqualTo(AiRequestLimiter.STILL_ANSWERING);
        });
        // Another student is not held up by the first one, and the account's turn is free again.
        assertThat(limiter.run(2L, () -> "other")).isEqualTo("other");
        assertThat(limiter.run(1L, () -> "again")).isEqualTo("again");
        assertThat(limiter.running()).isZero();
    }

    @Test
    void aFullServiceMakesOthersWaitThenAnswers429() throws Exception {
        AiRequestLimiter limiter = new AiRequestLimiter(new AiLimitProperties(1, Duration.ofMillis(200), null));
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        List<Concurrently.Outcome<String>> outcomes = Concurrently.run(
                () -> limiter.run(1L, () -> {
                    started.countDown();
                    await(release, 2000);
                    return "first";
                }),
                () -> {
                    await(started, 2000);
                    try {
                        return limiter.run(2L, () -> "second");
                    } finally {
                        release.countDown();
                    }
                });
        assertThat(outcomes.get(1).failure()).isInstanceOfSatisfying(ApiException.class,
                refused -> assertThat(refused.getMessage()).isEqualTo(AiRequestLimiter.BUSY));
    }

    @Test
    void facultyWhoTeachTheCourseTurnTheAssistantOffAndOn() {
        AiCourseControlRepository repository = mock(AiCourseControlRepository.class);
        LmsFacultyAccessService access = mock(LmsFacultyAccessService.class);
        when(access.canAccessCourse(5L, 3L)).thenReturn(true);
        AiCourseControlService service = new AiCourseControlService(repository, access, CLOCK);

        assertThat(service.studentAiEnabled(3L)).isTrue();
        service.change(5L, 3L, true);
        verify(repository, never()).saveAndFlush(any());

        service.change(5L, 3L, false);
        verify(repository).saveAndFlush(any());
        AiCourseControl off = new AiCourseControl(3L);
        off.change(false, 5L, CLOCK.instant());
        when(repository.findByLmsCourseId(3L)).thenReturn(Optional.of(off));
        assertThatThrownBy(() -> service.requireStudentAi(3L)).isInstanceOfSatisfying(ApiException.class,
                refused -> assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.change(6L, 3L, true)).isInstanceOfSatisfying(ApiException.class,
                refused -> assertThat(refused.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void settingsAreChecked() {
        assertThat(new AiLimitProperties(0, null, null)).isEqualTo(new AiLimitProperties(4, Duration.ofSeconds(60), Duration.ofDays(90)));
        assertThatThrownBy(() -> new AiLimitProperties(65, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiLimitProperties(4, Duration.ofMinutes(11), null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void await(CountDownLatch latch, long millis) {
        try {
            latch.await(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
