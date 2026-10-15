package com.stacc.backend.lms.api;

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

    private static ApiException courseUnavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE_MESSAGE);
    }
}
