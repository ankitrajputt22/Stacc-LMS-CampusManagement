package com.stacc.backend.lms.content;

import java.util.Optional;
import java.util.Set;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Optional<LmsLearningPath> forStudent(Long userAccountId, Long lmsCourseId) {
        return studentAccess.findAccessGrantingEnrollment(userAccountId, lmsCourseId)
                .map(enrollment -> pathFor(enrollment, lmsCourseId));
    }

    @Transactional(readOnly = true)
    public LmsLearningPath pathFor(CourseEnrollment enrollment, Long lmsCourseId) {
        return LmsLearningPath.of(
                modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY)
                        .stream().filter(LmsContentPolicy::isVisibleToStudents).toList(),
                topics.findAllInCourse(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY)
                        .stream().filter(LmsContentPolicy::isVisibleToStudents).toList(),
                completedPageIds(enrollment));
    }

    @Transactional(readOnly = true)
    public Set<Long> completedPageIds(CourseEnrollment enrollment) {
        return completions.findCompletedTopicIds(enrollment.getId());
    }

    @Transactional(readOnly = true)
    public boolean isOpenTo(Long userAccountId, Long lmsCourseId, Long moduleId) {
        return forStudent(userAccountId, lmsCourseId).map(path -> path.isOpen(moduleId)).orElse(false);
    }
}
