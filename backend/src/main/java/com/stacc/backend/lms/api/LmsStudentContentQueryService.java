package com.stacc.backend.lms.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsModuleRepository;
import com.stacc.backend.lms.content.LmsStudyMaterial;
import com.stacc.backend.lms.content.LmsStudyMaterialRepository;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTopicRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only answers about what a student sees inside an LMS course. */
@Service
public class LmsStudentContentQueryService {

    static final String PAGE_UNAVAILABLE_MESSAGE = "Page not found or unavailable.";

    private final LmsStudentAccessService accessService;
    private final LmsModuleRepository modules;
    private final LmsTopicRepository topics;
    private final LmsStudyMaterialRepository materials;

    public LmsStudentContentQueryService(
            LmsStudentAccessService accessService,
            LmsModuleRepository modules,
            LmsTopicRepository topics,
            LmsStudyMaterialRepository materials) {
        this.accessService = accessService;
        this.modules = modules;
        this.topics = topics;
        this.materials = materials;
    }

    /**
     * The published modules of a course the student may use, each with its published topics, in course
     * order. Access is decided again on every call by {@link LmsStudentAccessService}. A course the student
     * may not use answers the same 404 as a course that does not exist. Drafts and archived content never
     * appear, a published topic in an unpublished module stays hidden, and a study material appears only
     * when it, its topic, and its module are all published.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    @Transactional(readOnly = true)
    public LmsCourseContentResponse getPublishedContent(Long userAccountId, Long lmsCourseId) {
        if (!accessService.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, LmsStudentCourseQueryService.COURSE_UNAVAILABLE_MESSAGE);
        }

        Map<Long, List<LmsCourseMaterialResponse>> materialsByTopic = materials
                .findAllInCourse(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY,
                        LmsContentPolicy.PUBLISHED_ONLY).stream()
                .filter(LmsContentPolicy::isVisibleToStudents)
                .collect(Collectors.groupingBy(
                        (LmsStudyMaterial material) -> material.getLmsTopic().getId(),
                        Collectors.mapping(LmsCourseMaterialResponse::of, Collectors.toList())));

        Map<Long, List<LmsCourseTopicResponse>> topicsByModule = topics
                .findAllInCourse(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY).stream()
                .filter(LmsContentPolicy::isVisibleToStudents)
                .collect(Collectors.groupingBy(
                        (LmsTopic topic) -> topic.getLmsModule().getId(),
                        Collectors.mapping(
                                topic -> LmsCourseTopicResponse.of(topic, materialsByTopic.getOrDefault(topic.getId(), List.of())),
                                Collectors.toList())));

        return new LmsCourseContentResponse(modules
                .findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY)
                .stream()
                .filter(LmsContentPolicy::isVisibleToStudents)
                .map(module -> LmsCourseModuleResponse.of(module, topicsByModule.getOrDefault(module.getId(), List.of())))
                .toList());
    }

    /**
     * One published page (a topic) of a course the student may use, with the published pages before and after it in
     * course order. It is found in exactly what {@link #getPublishedContent} shows, so the same rules decide: a page
     * that is missing, a draft, archived, in an unpublished module, or in another course answers the same 404.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
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
                        index == pages.size() - 1 ? null : link(pages.get(index + 1), moduleOfPage.get(index + 1)));
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, PAGE_UNAVAILABLE_MESSAGE);
    }

    /**
     * Every published file of a course the student may use, in course order (module, page, then file), with the page
     * and module it is on. It is taken from exactly what {@link #getPublishedContent} shows, so the same rules decide.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
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
