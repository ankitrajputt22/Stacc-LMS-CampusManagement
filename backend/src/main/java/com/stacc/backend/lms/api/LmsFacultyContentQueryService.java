package com.stacc.backend.lms.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsModuleRepository;
import com.stacc.backend.lms.content.LmsStudyMaterial;
import com.stacc.backend.lms.content.LmsStudyMaterialRepository;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTopicRepository;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only answers about the content a faculty member manages in one LMS course. */
@Service
public class LmsFacultyContentQueryService {

    static final String COURSE_UNAVAILABLE_MESSAGE = "Course not found or unavailable.";
    static final String PAGE_UNAVAILABLE_MESSAGE = "Page not found or unavailable.";

    private final LmsFacultyAccessService accessService;
    private final LmsCourseRepository lmsCourses;
    private final LmsModuleRepository modules;
    private final LmsTopicRepository topics;
    private final LmsStudyMaterialRepository materials;

    public LmsFacultyContentQueryService(
            LmsFacultyAccessService accessService,
            LmsCourseRepository lmsCourses,
            LmsModuleRepository modules,
            LmsTopicRepository topics,
            LmsStudyMaterialRepository materials) {
        this.accessService = accessService;
        this.lmsCourses = lmsCourses;
        this.modules = modules;
        this.topics = topics;
        this.materials = materials;
    }

    /**
     * One course the faculty member may use, with every module and topic that is not archived, drafts
     * included, and each topic's study materials that are not archived, in course order. Access is
     * decided by {@link LmsFacultyAccessService} for this course on every call. A course they may not
     * use answers the same 404 as a course that does not exist. Archived content stays stored but is
     * not listed.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    @Transactional(readOnly = true)
    public LmsFacultyCourseContentResponse getCourseContent(Long userAccountId, Long lmsCourseId) {
        if (!accessService.canAccessCourse(userAccountId, lmsCourseId)) {
            throw courseUnavailable();
        }
        LmsCourse lmsCourse = lmsCourses.findWithAcademicContextById(lmsCourseId).orElseThrow(
                LmsFacultyContentQueryService::courseUnavailable);

        Map<Long, List<LmsFacultyMaterialResponse>> materialsByTopic = materials
                .findAllInCourse(lmsCourseId, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE)
                .stream()
                .filter(LmsContentPolicy::isInUseByFaculty)
                .collect(Collectors.groupingBy(
                        (LmsStudyMaterial material) -> material.getLmsTopic().getId(),
                        Collectors.mapping(LmsFacultyMaterialResponse::of, Collectors.toList())));

        Map<Long, List<LmsFacultyTopicContentResponse>> topicsByModule = topics
                .findAllInCourse(lmsCourseId, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE).stream()
                .filter(topic -> LmsContentPolicy.isInUse(topic.getStatus())
                        && LmsContentPolicy.isInUse(topic.getLmsModule().getStatus()))
                .collect(Collectors.groupingBy(
                        (LmsTopic topic) -> topic.getLmsModule().getId(),
                        Collectors.mapping(
                                topic -> LmsFacultyTopicContentResponse.of(
                                        topic, materialsByTopic.getOrDefault(topic.getId(), List.of())),
                                Collectors.toList())));

        List<LmsFacultyModuleContentResponse> moduleContent = modules
                .findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(lmsCourseId, LmsContentPolicy.IN_USE).stream()
                .filter(module -> LmsContentPolicy.isInUse(module.getStatus()))
                .map(module -> LmsFacultyModuleContentResponse.of(
                        module, topicsByModule.getOrDefault(module.getId(), List.of())))
                .toList();

        // The course details are read from the academic records every time. The LMS keeps no copy.
        CourseOffering courseOffering = lmsCourse.getCourseOffering();
        Course course = courseOffering.getCourse();
        Semester semester = courseOffering.getSemester();
        return new LmsFacultyCourseContentResponse(
                lmsCourse.getId(),
                course.getCode(),
                course.getName(),
                course.getCredits(),
                semester.getSemesterNumber(),
                semester.getProgram().getCode(),
                semester.getAcademicSession().getCode(),
                lmsCourse.getStatus().name(),
                moduleContent);
    }

    /**
     * One page (a topic) that is not archived, drafts included, of a course the faculty member may use, with the pages
     * before and after it in course order. The course is found from the stored page, never from the client, and the
     * page is found in exactly what {@link #getCourseContent} lists, so the same rules decide. A page that is missing,
     * archived, in an archived module, or in a course they do not teach answers the same 404.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    @Transactional(readOnly = true)
    public LmsFacultyPageResponse getPage(Long userAccountId, Long pageId) {
        Long lmsCourseId = topics.findWithModuleById(pageId)
                .map(topic -> topic.getLmsModule().getLmsCourse().getId())
                .orElseThrow(LmsFacultyContentQueryService::pageUnavailable);
        LmsFacultyCourseContentResponse course;
        try {
            course = getCourseContent(userAccountId, lmsCourseId);
        } catch (ApiException unavailable) {
            throw pageUnavailable();
        }
        List<LmsFacultyModuleContentResponse> moduleOfPage = new ArrayList<>();
        List<LmsFacultyTopicContentResponse> pages = new ArrayList<>();
        for (LmsFacultyModuleContentResponse module : course.modules()) {
            for (LmsFacultyTopicContentResponse page : module.topics()) {
                moduleOfPage.add(module);
                pages.add(page);
            }
        }
        for (int index = 0; index < pages.size(); index++) {
            LmsFacultyTopicContentResponse page = pages.get(index);
            if (page.id().equals(pageId)) {
                LmsFacultyModuleContentResponse module = moduleOfPage.get(index);
                return new LmsFacultyPageResponse(course.lmsCourseId(), course.courseCode(), course.courseName(),
                        course.lmsCourseStatus(), page.id(), page.title(), page.content(), page.contentFormat(),
                        page.status(), module.id(), module.title(), module.status(), page.materials(),
                        index == 0 ? null : link(pages.get(index - 1), moduleOfPage.get(index - 1)),
                        index == pages.size() - 1 ? null : link(pages.get(index + 1), moduleOfPage.get(index + 1)));
            }
        }
        throw pageUnavailable();
    }

    /**
     * Every file that is not archived, drafts included, of a course the faculty member may use, in course order, with
     * the page and module it is on. It is taken from exactly what {@link #getCourseContent} lists.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    @Transactional(readOnly = true)
    public LmsFacultyCourseFilesResponse getCourseFiles(Long userAccountId, Long lmsCourseId) {
        LmsFacultyCourseContentResponse course = getCourseContent(userAccountId, lmsCourseId);
        List<LmsFacultyCourseFileResponse> files = new ArrayList<>();
        for (LmsFacultyModuleContentResponse module : course.modules()) {
            for (LmsFacultyTopicContentResponse page : module.topics()) {
                for (LmsFacultyMaterialResponse file : page.materials()) {
                    files.add(LmsFacultyCourseFileResponse.of(file, page, module));
                }
            }
        }
        return new LmsFacultyCourseFilesResponse(course.lmsCourseId(), course.courseCode(), course.courseName(),
                course.lmsCourseStatus(), files);
    }

    private static LmsPageLink link(LmsFacultyTopicContentResponse page, LmsFacultyModuleContentResponse module) {
        return new LmsPageLink(page.id(), page.title(), module.title());
    }

    private static ApiException pageUnavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, PAGE_UNAVAILABLE_MESSAGE);
    }

    private static ApiException courseUnavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE_MESSAGE);
    }
}
