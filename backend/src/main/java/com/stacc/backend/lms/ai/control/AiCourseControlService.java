package com.stacc.backend.lms.ai.control;

import java.time.Clock;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiCourseControlService {

    public static final String TURNED_OFF = "The AI Study Assistant is turned off for this course.";
    static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";

    private final AiCourseControlRepository controls;
    private final LmsFacultyAccessService facultyAccess;
    private final Clock clock;

    public AiCourseControlService(AiCourseControlRepository controls, LmsFacultyAccessService facultyAccess, Clock clock) {
        this.controls = controls;
        this.facultyAccess = facultyAccess;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean studentAiEnabled(Long lmsCourseId) {
        return controls.findByLmsCourseId(lmsCourseId).map(AiCourseControl::isStudentAiEnabled).orElse(true);
    }

    @Transactional(readOnly = true)
    public void requireStudentAi(Long lmsCourseId) {
        if (!studentAiEnabled(lmsCourseId)) {
            throw new ApiException(HttpStatus.CONFLICT, TURNED_OFF);
        }
    }

    @Transactional
    public boolean change(Long accountId, Long lmsCourseId, boolean enabled) {
        if (!facultyAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
        AiCourseControl control = controls.findByLmsCourseId(lmsCourseId).orElseGet(() -> new AiCourseControl(lmsCourseId));
        if (control.isStudentAiEnabled() != enabled) {
            control.change(enabled, accountId, clock.instant());
            try {
                controls.saveAndFlush(control);
            } catch (DataIntegrityViolationException twice) {
                throw new ApiException(HttpStatus.CONFLICT, "The switch was just changed. Reload and try again.");
            }
        }
        return enabled;
    }
}
