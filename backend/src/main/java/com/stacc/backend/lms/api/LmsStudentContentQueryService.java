package com.stacc.backend.lms.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsLearningPath;
import com.stacc.backend.lms.content.LmsLearningPathService;
import com.stacc.backend.lms.content.LmsModule;
import com.stacc.backend.lms.content.LmsModuleRepository;
import com.stacc.backend.lms.content.LmsStudyMaterial;
import com.stacc.backend.lms.content.LmsStudyMaterialRepository;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTopicRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LmsStudentContentQueryService {

    static final String PAGE_UNAVAILABLE_MESSAGE = "Page not found or unavailable.";

    private final LmsStudentAccessService accessService;
    private final LmsModuleRepository modules;
    private final LmsTopicRepository topics;
    private final LmsStudyMaterialRepository materials;
    private final LmsLearningPathService paths;

    public LmsStudentContentQueryService(
            LmsStudentAccessService accessService,
            LmsModuleRepository modules,
            LmsTopicRepository topics,
            LmsStudyMaterialRepository materials,
            LmsLearningPathService paths) {
        this.accessService = accessService;
        this.modules = modules;
        this.topics = topics;
        this.materials = materials;
        this.paths = paths;
    }

    @Transactional(readOnly = true)
    public LmsCourseContentResponse getPublishedContent(Long userAccountId, Long lmsCourseId) {
        CourseEnrollment enrollment = accessService.findAccessGrantingEnrollment(userAccountId, lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, LmsStudentCourseQueryService.COURSE_UNAVAILABLE_MESSAGE));

        Map<Long, List<LmsCourseMaterialResponse>> materialsByTopic = materials
                .findAllInCourse(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY,
                        LmsContentPolicy.PUBLISHED_ONLY).stream()
                .filter(LmsContentPolicy::isVisibleToStudents)
                .collect(Collectors.groupingBy(
                        (LmsStudyMaterial material) -> material.getLmsTopic().getId(),
                        Collectors.mapping(LmsCourseMaterialResponse::of, Collectors.toList())));

        List<LmsTopic> visibleTopics = topics
                .findAllInCourse(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY).stream()
                .filter(LmsContentPolicy::isVisibleToStudents)
                .toList();
        List<LmsModule> visibleModules = modules
                .findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY)
                .stream()
                .filter(LmsContentPolicy::isVisibleToStudents)
                .toList();
        LmsLearningPath path = LmsLearningPath.of(visibleModules, visibleTopics, paths.completedPageIds(enrollment));

        Map<Long, List<LmsCourseTopicResponse>> topicsByModule = visibleTopics.stream()
                .collect(Collectors.groupingBy(
                        (LmsTopic topic) -> topic.getLmsModule().getId(),
                        Collectors.mapping(
                                topic -> LmsCourseTopicResponse.of(topic, materialsByTopic.getOrDefault(topic.getId(), List.of()),
                                        path.isCompleted(topic.getId())),
                                Collectors.toList())));

        return new LmsCourseContentResponse(visibleModules.stream()
                .map(module -> {
                    LmsLearningPath.ModuleState state = path.module(module.getId());
                    List<LmsCourseTopicResponse> pages = topicsByModule.getOrDefault(module.getId(), List.of());
                    return new LmsCourseModuleResponse(module.getId(), module.getTitle(), module.getDescription(),
                            state.locked() ? pages.stream().map(LmsCourseTopicResponse::withoutContent).toList() : pages,
                            module.isRequiresPrevious(), state.locked(), state.completed(), state.waitingFor());
                })
                .toList());
    }

    @Transactional(readOnly = true)
    public LmsCourseProgressResponse getProgress(Long userAccountId, Long lmsCourseId) {
        int required = 0;
        int completed = 0;
        LmsPageLink next = null;
        for (LmsCourseModuleResponse module : getPublishedContent(userAccountId, lmsCourseId).modules()) {
            for (LmsCourseTopicResponse page : module.topics()) {
                if (!page.required()) {
                    continue;
                }
                required++;
                if (page.completed()) {
                    completed++;
                } else if (next == null && !module.locked()) {
                    next = new LmsPageLink(page.id(), page.title(), module.title());
                }
            }
        }
        return new LmsCourseProgressResponse(required, completed, next);
    }

    @Transactional(readOnly = true)
    public LmsCoursePageResponse getPublishedPage(Long userAccountId, Long lmsCourseId, Long pageId) {
        List<LmsCourseModuleResponse> content = getPublishedContent(userAccountId, lmsCourseId).modules();
        List<LmsCourseModuleResponse> moduleOfPage = new ArrayList<>();
        List<LmsCourseTopicResponse> pages = new ArrayList<>();
        for (LmsCourseModuleResponse module : content) {
            for (LmsCourseTopicResponse page : module.topics()) {
                moduleOfPage.add(module);
                pages.add(page);
            }
        }
        for (int index = 0; index < pages.size(); index++) {
            LmsCourseTopicResponse page = pages.get(index);
            if (page.id().equals(pageId)) {
                LmsCourseModuleResponse module = moduleOfPage.get(index);
                return new LmsCoursePageResponse(page.id(), page.title(), page.content(), page.contentFormat(),
                        module.id(), module.title(), page.materials(),
                        index == 0 ? null : link(pages.get(index - 1), moduleOfPage.get(index - 1)),
                        index == pages.size() - 1 ? null : link(pages.get(index + 1), moduleOfPage.get(index + 1)),
                        page.required(), page.completed(), module.locked(), module.waitingFor());
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, PAGE_UNAVAILABLE_MESSAGE);
    }

    @Transactional(readOnly = true)
    public List<LmsCourseFileResponse> getPublishedFiles(Long userAccountId, Long lmsCourseId) {
        List<LmsCourseFileResponse> files = new ArrayList<>();
        for (LmsCourseModuleResponse module : getPublishedContent(userAccountId, lmsCourseId).modules()) {
            for (LmsCourseTopicResponse page : module.topics()) {
                for (LmsCourseMaterialResponse file : page.materials()) {
                    files.add(LmsCourseFileResponse.of(file, page, module));
                }
            }
        }
        return files;
    }

    private static LmsPageLink link(LmsCourseTopicResponse page, LmsCourseModuleResponse module) {
        return new LmsPageLink(page.id(), page.title(), module.title());
    }
}
