package com.stacc.backend.lms.content;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change faculty make to the modules and topics of an LMS course. Before any change, the
 * course behind the module or topic is found from stored data, and {@link LmsFacultyAccessService}
 * must allow the signed-in account to use it. A course ID sent by the client never proves anything.
 *
 * <p>Something that does not exist and something the faculty member may not manage get the same
 * 404, so IDs reveal nothing about other courses. Nothing is ever deleted: content is archived.
 */
@Service
@Transactional
public class LmsContentManagementService {

    static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    static final String MODULE_UNAVAILABLE = "Module not found or unavailable.";
    static final String TOPIC_UNAVAILABLE = "Topic not found or unavailable.";
    static final String MODULE_ARCHIVED = "This module is archived and can no longer be changed.";
    static final String TOPIC_ARCHIVED = "This topic is archived and can no longer be changed.";
    static final String MODULE_ORDER_MISMATCH = "List every current module of this course exactly once.";
    static final String TOPIC_ORDER_MISMATCH = "List every current topic of this module exactly once.";

    private final LmsCourseRepository lmsCourses;
    private final LmsModuleRepository modules;
    private final LmsTopicRepository topics;
    private final LmsFacultyAccessService facultyAccess;

    public LmsContentManagementService(
            LmsCourseRepository lmsCourses,
            LmsModuleRepository modules,
            LmsTopicRepository topics,
            LmsFacultyAccessService facultyAccess) {
        this.lmsCourses = lmsCourses;
        this.modules = modules;
        this.topics = topics;
        this.facultyAccess = facultyAccess;
    }

    /** Adds a draft module after the course's current modules. */
    public LmsModule createModule(Long userAccountId, Long lmsCourseId, String title, String description) {
        LmsCourse lmsCourse = manageableCourse(userAccountId, lmsCourseId);
        List<LmsModule> current = modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(
                lmsCourse.getId(), LmsContentPolicy.IN_USE);
        int position = current.stream().mapToInt(LmsModule::getPosition).max().orElse(0) + 1;
        return modules.save(new LmsModule(lmsCourse, title, description, position));
    }

    public LmsModule updateModule(Long userAccountId, Long moduleId, String title, String description) {
        LmsModule module = changeableModule(userAccountId, moduleId);
        module.edit(title, description);
        return module;
    }

    /** Publishes the module only. Its topics keep their own status. */
    public LmsModule publishModule(Long userAccountId, Long moduleId) {
        LmsModule module = changeableModule(userAccountId, moduleId);
        module.publish();
        return module;
    }

    /** Archives the module and keeps its topics. Archiving an archived module changes nothing. */
    public LmsModule archiveModule(Long userAccountId, Long moduleId) {
        LmsModule module = manageableModule(userAccountId, moduleId);
        module.archive();
        return module;
    }

    /**
     * Puts the course's current modules in the given order, numbered 1, 2, 3 and so on. The list must
     * hold every module that is not archived, each exactly once, and nothing else.
     */
    public List<LmsModule> reorderModules(Long userAccountId, Long lmsCourseId, List<Long> moduleIds) {
        LmsCourse lmsCourse = manageableCourse(userAccountId, lmsCourseId);
        List<LmsModule> current = modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(
                lmsCourse.getId(), LmsContentPolicy.IN_USE);
        return reorder(current, moduleIds, LmsModule::getId, LmsModule::moveTo, MODULE_ORDER_MISMATCH);
    }

    /** Adds a draft topic after the module's current topics. */
    public LmsTopic createTopic(Long userAccountId, Long moduleId, String title, String content) {
        LmsModule module = changeableModule(userAccountId, moduleId);
        List<LmsTopic> current = topics.findAllByLmsModuleIdAndStatusInOrderByPositionAscIdAsc(
                module.getId(), LmsContentPolicy.IN_USE);
        int position = current.stream().mapToInt(LmsTopic::getPosition).max().orElse(0) + 1;
        return topics.save(new LmsTopic(module, title, content, position));
    }

    public LmsTopic updateTopic(Long userAccountId, Long topicId, String title, String content) {
        LmsTopic topic = changeableTopic(userAccountId, topicId);
        topic.edit(title, content);
        return topic;
    }

    /** Publishes the topic only. Students see it once its module is published as well. */
    public LmsTopic publishTopic(Long userAccountId, Long topicId) {
        LmsTopic topic = changeableTopic(userAccountId, topicId);
        topic.publish();
        return topic;
    }

    /** Archives the topic. Archiving an archived topic changes nothing. */
    public LmsTopic archiveTopic(Long userAccountId, Long topicId) {
        LmsTopic topic = manageableTopic(userAccountId, topicId);
        topic.archive();
        return topic;
    }

    /** Like {@link #reorderModules}, for the current topics of one module. */
    public List<LmsTopic> reorderTopics(Long userAccountId, Long moduleId, List<Long> topicIds) {
        LmsModule module = changeableModule(userAccountId, moduleId);
        List<LmsTopic> current = topics.findAllByLmsModuleIdAndStatusInOrderByPositionAscIdAsc(
                module.getId(), LmsContentPolicy.IN_USE);
        return reorder(current, topicIds, LmsTopic::getId, LmsTopic::moveTo, TOPIC_ORDER_MISMATCH);
    }

    private LmsCourse manageableCourse(Long userAccountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw notFound(COURSE_UNAVAILABLE);
        }
        return lmsCourses.findById(lmsCourseId).orElseThrow(() -> notFound(COURSE_UNAVAILABLE));
    }

    // The course is taken from the stored module, never from the request.
    private LmsModule manageableModule(Long userAccountId, Long moduleId) {
        LmsModule module = moduleId == null ? null : modules.findById(moduleId).orElse(null);
        if (module == null || !facultyAccess.canAccessCourse(userAccountId, module.getLmsCourse().getId())) {
            throw notFound(MODULE_UNAVAILABLE);
        }
        return module;
    }

    private LmsModule changeableModule(Long userAccountId, Long moduleId) {
        LmsModule module = manageableModule(userAccountId, moduleId);
        if (module.isArchived()) {
            throw conflict(MODULE_ARCHIVED);
        }
        return module;
    }

    // The course is taken from the stored topic and its module, never from the request.
    private LmsTopic manageableTopic(Long userAccountId, Long topicId) {
        LmsTopic topic = topicId == null ? null : topics.findWithModuleById(topicId).orElse(null);
        if (topic == null
                || !facultyAccess.canAccessCourse(userAccountId, topic.getLmsModule().getLmsCourse().getId())) {
            throw notFound(TOPIC_UNAVAILABLE);
        }
        return topic;
    }

    // A topic can be changed only while it and its module are both still in use.
    private LmsTopic changeableTopic(Long userAccountId, Long topicId) {
        LmsTopic topic = manageableTopic(userAccountId, topicId);
        if (topic.isArchived()) {
            throw conflict(TOPIC_ARCHIVED);
        }
        if (topic.getLmsModule().isArchived()) {
            throw conflict(MODULE_ARCHIVED);
        }
        return topic;
    }

    private static <T> List<T> reorder(
            List<T> current, List<Long> requestedIds, Function<T, Long> idOf, Positioner<T> moveTo, String mismatch) {
        Map<Long, T> byId = current.stream().collect(Collectors.toMap(idOf, Function.identity()));
        Set<Long> requested = requestedIds == null ? Set.of() : new HashSet<>(requestedIds);
        if (requestedIds == null || requested.size() != requestedIds.size() || !requested.equals(byId.keySet())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, mismatch);
        }

        List<T> ordered = requestedIds.stream().map(byId::get).toList();
        for (int index = 0; index < ordered.size(); index++) {
            moveTo.moveTo(ordered.get(index), index + 1);
        }
        return ordered;
    }

    @FunctionalInterface
    private interface Positioner<T> {
        void moveTo(T item, int position);
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
