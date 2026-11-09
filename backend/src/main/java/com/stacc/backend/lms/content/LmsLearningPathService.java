package com.stacc.backend.lms.content;

import java.util.Optional;
import java.util.Set;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Works out a student's learning path in a course from the published content and their completions, every time.
 * Empty when the student may not use the course. It only reads.
 */
@Service
public class LmsLearningPathService {

    private final LmsStudentAccessService studentAccess;
    private final LmsModuleRepository modules;
    private final LmsTopicRepository topics;
    private final LmsPageCompletionRepository completions;

    public LmsLearningPathService(LmsStudentAccessService studentAccess, LmsModuleRepository modules,
            LmsTopicRepository topics, LmsPageCompletionRepository completions) {
        this.studentAccess = studentAccess;
        this.modules = modules;
        this.topics = topics;
        this.completions = completions;
    }

    /** The student's path in the course, or empty when they may not use it. */
    @Transactional(readOnly = true)
    public Optional<LmsLearningPath> forStudent(Long userAccountId, Long lmsCourseId) {
        return studentAccess.findAccessGrantingEnrollment(userAccountId, lmsCourseId)
                .map(enrollment -> pathFor(enrollment, lmsCourseId));
    }

    /** The path of a student's enrollment that gives them access to the course. */
    @Transactional(readOnly = true)
    public LmsLearningPath pathFor(CourseEnrollment enrollment, Long lmsCourseId) {
        return LmsLearningPath.of(
                modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY)
                        .stream().filter(LmsContentPolicy::isVisibleToStudents).toList(),
                topics.findAllInCourse(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY)
                        .stream().filter(LmsContentPolicy::isVisibleToStudents).toList(),
                completedPageIds(enrollment));
    }

    /** The pages the enrollment completed. */
    @Transactional(readOnly = true)
    public Set<Long> completedPageIds(CourseEnrollment enrollment) {
        return completions.findCompletedTopicIds(enrollment.getId());
    }

    /** Whether the student may open the module now: they may use the course and the module is not locked for them. */
    @Transactional(readOnly = true)
    public boolean isOpenTo(Long userAccountId, Long lmsCourseId, Long moduleId) {
        return forStudent(userAccountId, lmsCourseId).map(path -> path.isOpen(moduleId)).orElse(false);
    }
}
