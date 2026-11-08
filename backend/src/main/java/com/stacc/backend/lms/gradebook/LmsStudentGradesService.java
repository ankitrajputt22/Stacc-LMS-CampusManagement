package com.stacc.backend.lms.gradebook;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's own grades in a course they may use now: only the columns whose grades are published, and for quizzes
 * only attempts whose result is released. The student comes from the access token; nobody else's marks are read.
 */
@Service
public class LmsStudentGradesService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";

    private final LmsStudentAccessService studentAccess;
    private final GradebookBuilder builder;
    private final Clock clock;

    public LmsStudentGradesService(LmsStudentAccessService studentAccess, GradebookBuilder builder, Clock clock) {
        this.studentAccess = studentAccess;
        this.builder = builder;
        this.clock = clock;
    }

    /** The published columns with this student's cells, and the enrollment they belong to. */
    public record StudentGrades(GradebookBuilder.Gradebook gradebook, Long courseEnrollmentId) {
    }

    @Transactional(readOnly = true)
    public StudentGrades grades(Long userAccountId, Long lmsCourseId) {
        CourseEnrollment enrollment = studentAccess.findAccessGrantingEnrollment(userAccountId, lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        List<GradebookBuilder.Column> published = builder.columns(lmsCourseId).stream()
                .filter(GradebookBuilder.Column::published).toList();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return new StudentGrades(builder.build(published, List.of(enrollment), true, now), enrollment.getId());
    }
}
