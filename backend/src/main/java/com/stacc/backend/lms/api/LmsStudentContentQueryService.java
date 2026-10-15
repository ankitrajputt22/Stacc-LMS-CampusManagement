package com.stacc.backend.lms.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsModuleRepository;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTopicRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only answers about what a student sees inside an LMS course. */
@Service
public class LmsStudentContentQueryService {

    private final LmsStudentAccessService accessService;
    private final LmsModuleRepository modules;
    private final LmsTopicRepository topics;

    public LmsStudentContentQueryService(
            LmsStudentAccessService accessService, LmsModuleRepository modules, LmsTopicRepository topics) {
        this.accessService = accessService;
        this.modules = modules;
        this.topics = topics;
    }

    /**
     * The published modules of a course the student may use, each with its published topics, in course
     * order. Access is decided again on every call by {@link LmsStudentAccessService}. A course the student
     * may not use answers the same 404 as a course that does not exist. Drafts and archived content never
     * appear, and a published topic in an unpublished module stays hidden.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    @Transactional(readOnly = true)
    public LmsCourseContentResponse getPublishedContent(Long userAccountId, Long lmsCourseId) {
        if (!accessService.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, LmsStudentCourseQueryService.COURSE_UNAVAILABLE_MESSAGE);
        }

        Map<Long, List<LmsCourseTopicResponse>> topicsByModule = topics
                .findAllInCourse(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY).stream()
                .filter(LmsContentPolicy::isVisibleToStudents)
                .collect(Collectors.groupingBy(
                        (LmsTopic topic) -> topic.getLmsModule().getId(),
                        Collectors.mapping(LmsCourseTopicResponse::of, Collectors.toList())));

        return new LmsCourseContentResponse(modules
                .findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY)
                .stream()
                .filter(LmsContentPolicy::isVisibleToStudents)
                .map(module -> LmsCourseModuleResponse.of(module, topicsByModule.getOrDefault(module.getId(), List.of())))
                .toList());
    }
}
