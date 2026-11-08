package com.stacc.backend.lms.content;

import java.time.Clock;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records that a student completed a page, once. The student is found from the enrollment that gives the signed-in
 * account access, never from the client. The page must be one they can see, in a module that is open to them. The
 * enrollment row is locked first, so two clicks at once record one completion.
 */
@Service
public class LmsPageCompletionService {

    public static final String PAGE_UNAVAILABLE = "Page not found or unavailable.";
    public static final String LOCKED = "This page is locked. Complete “%s” first.";

    private final LmsStudentAccessService studentAccess;
    private final LmsTopicRepository topics;
    private final LmsPageCompletionRepository completions;
    private final CourseEnrollmentRepository courseEnrollments;
    private final LmsLearningPathService paths;
    private final Clock clock;

    public LmsPageCompletionService(LmsStudentAccessService studentAccess, LmsTopicRepository topics,
            LmsPageCompletionRepository completions, CourseEnrollmentRepository courseEnrollments,
            LmsLearningPathService paths, Clock clock) {
        this.studentAccess = studentAccess;
        this.topics = topics;
        this.completions = completions;
        this.courseEnrollments = courseEnrollments;
        this.paths = paths;
        this.clock = clock;
    }

    /** Marks the page complete for the student, or returns the earlier completion. */
    @Transactional
    public LmsPageCompletion complete(Long userAccountId, Long lmsCourseId, Long pageId) {
        CourseEnrollment enrollment = studentAccess.findAccessGrantingEnrollment(userAccountId, lmsCourseId)
                .orElseThrow(LmsPageCompletionService::unavailable);
        LmsTopic page = topics.findWithModuleById(pageId)
                .filter(topic -> topic.getLmsModule().getLmsCourse().getId().equals(lmsCourseId))
                .filter(LmsContentPolicy::isVisibleToStudents)
                .orElseThrow(LmsPageCompletionService::unavailable);
        LmsLearningPath.ModuleState module = paths.pathFor(enrollment, lmsCourseId).module(page.getLmsModule().getId());
        if (module.locked()) {
            throw new ApiException(HttpStatus.CONFLICT, LOCKED.formatted(module.waitingFor()));
        }
        CourseEnrollment locked = courseEnrollments.findByIdForUpdate(enrollment.getId())
                .filter(current -> current.getStatus() == CourseEnrollmentStatus.ENROLLED)
                .orElseThrow(LmsPageCompletionService::unavailable);
        return completions.findForUpdate(locked.getId(), page.getId())
                .orElseGet(() -> completions.save(new LmsPageCompletion(locked, page, clock.instant())));
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, PAGE_UNAVAILABLE);
    }
}
