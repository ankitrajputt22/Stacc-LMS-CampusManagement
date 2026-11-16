package com.stacc.backend.lms.ai.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttempt;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttemptRepository;
import org.junit.jupiter.api.Test;

/** AI pauses only while a graded quiz attempt of the course still takes answers. */
class AiQuizPauseTest {

    private static final Instant NOW = Instant.parse("2026-11-16T05:00:00Z");

    @Test
    void anOpenGradedAttemptPausesAndAFinishedOneDoesNot() {
        LmsStudentAccessService access = mock(LmsStudentAccessService.class);
        LmsQuizAttemptRepository attempts = mock(LmsQuizAttemptRepository.class);
        CourseEnrollment enrollment = mock(CourseEnrollment.class);
        when(enrollment.getId()).thenReturn(40L);
        when(access.findAccessGrantingEnrollment(11L, 7L)).thenReturn(Optional.of(enrollment));
        LmsQuizAttempt open = mock(LmsQuizAttempt.class);
        when(open.acceptsAnswersAt(NOW)).thenReturn(true);
        LmsQuizAttempt late = mock(LmsQuizAttempt.class);
        when(late.acceptsAnswersAt(NOW)).thenReturn(false);
        AiQuizPause pause = new AiQuizPause(access, attempts, Clock.fixed(NOW, ZoneOffset.UTC));

        when(attempts.findInProgressGraded(40L, 7L)).thenReturn(List.of(late));
        assertThat(pause.inQuiz(11L, 7L)).isFalse();
        when(attempts.findInProgressGraded(40L, 7L)).thenReturn(List.of(late, open));
        assertThat(pause.inQuiz(11L, 7L)).isTrue();
        assertThat(pause.inQuiz(12L, 7L)).isFalse();
    }
}
