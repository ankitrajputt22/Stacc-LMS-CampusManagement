package com.stacc.backend.lms.ai.control;

import java.time.Clock;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The per-course switch for the student AI Study Assistant (v0.13.7). Faculty who teach the course turn it off or on;
 * while it is off, students' questions, summaries, and practice answer 409, and AI Tools and notes keep working.
 */
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

    /** 409 when students may not use AI in the course now. Callers check the student's access first. */
    @Transactional(readOnly = true)
    public void requireStudentAi(Long lmsCourseId) {
        if (!studentAiEnabled(lmsCourseId)) {
            throw new ApiException(HttpStatus.CONFLICT, TURNED_OFF);
        }
    }

    /** Turns the student assistant on or off; setting the current value changes nothing. */
    @Transactional
    public boolean change(Long accountId, Long lmsCourseId, boolean enabled) {
        if (!facultyAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
        // A course without a row is on, so turning it on needs no row.
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
