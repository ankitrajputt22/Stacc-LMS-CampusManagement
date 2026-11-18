package com.stacc.backend.lms.ai.control;

import java.time.Clock;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttemptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AiQuizPause {

    public static final String PAUSED = "The AI Study Assistant is paused while you have a graded quiz in progress in this course.";

    private final LmsStudentAccessService studentAccess;
    private final LmsQuizAttemptRepository attempts;
    private final Clock clock;

    public AiQuizPause(LmsStudentAccessService studentAccess, LmsQuizAttemptRepository attempts, Clock clock) {
        this.studentAccess = studentAccess;
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean inQuiz(Long accountId, Long lmsCourseId) {
        return studentAccess.findAccessGrantingEnrollment(accountId, lmsCourseId)
                .map(enrollment -> attempts.findInProgressGraded(enrollment.getId(), lmsCourseId).stream()
                        .anyMatch(attempt -> attempt.acceptsAnswersAt(clock.instant())))
                .orElse(false);
    }

    public void requireNoQuizInProgress(Long accountId, Long lmsCourseId) {
        if (inQuiz(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.CONFLICT, PAUSED);
        }
    }
}
