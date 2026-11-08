package com.stacc.backend.lms.copy;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageException;
import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsModule;
import com.stacc.backend.lms.content.LmsModuleRepository;
import com.stacc.backend.lms.content.LmsStudyMaterial;
import com.stacc.backend.lms.content.LmsStudyMaterialRepository;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTopicRepository;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.syllabus.LmsSyllabus;
import com.stacc.backend.lms.syllabus.LmsSyllabusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copies course content into an LMS course the faculty member teaches now, from an earlier offering of the same catalog
 * course that they taught (any term, archived courses included). Modules, pages (text, format, required flag), study
 * files (the bytes are copied to new private keys), and the syllabus text when the target has none: everything arrives
 * as a draft after the target's own content, so students see nothing until faculty publish it. Assignments,
 * announcements, submissions, grades, and anything about students are never copied. The target course row is locked
 * first; a storage failure removes the files already copied and changes nothing.
 */
@Service
public class LmsCourseCopyService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    public static final String SOURCE_UNAVAILABLE = "Choose an earlier offering of this course that you taught.";
    public static final String STORAGE_UNAVAILABLE = "The files could not be copied right now. Nothing was copied; try again later.";

    private static final Logger LOGGER = LoggerFactory.getLogger(LmsCourseCopyService.class);

    /** How many of each thing were copied. */
    public record CopyResult(int modules, int pages, int files, boolean syllabus) {
    }

    private final LmsCourseRepository lmsCourses;
    private final LmsFacultyAccessService facultyAccess;
    private final LmsModuleRepository modules;
    private final LmsTopicRepository topics;
    private final LmsStudyMaterialRepository materials;
    private final LmsSyllabusRepository syllabi;
    private final ObjectStorageService storage;
    private final Clock clock;

    public LmsCourseCopyService(LmsCourseRepository lmsCourses, LmsFacultyAccessService facultyAccess,
            LmsModuleRepository modules, LmsTopicRepository topics, LmsStudyMaterialRepository materials,
            LmsSyllabusRepository syllabi, ObjectStorageService storage, Clock clock) {
        this.lmsCourses = lmsCourses;
        this.facultyAccess = facultyAccess;
        this.modules = modules;
        this.topics = topics;
        this.materials = materials;
        this.syllabi = syllabi;
        this.storage = storage;
        this.clock = clock;
    }

    /** The earlier offerings of the same course the faculty member taught, the latest term first. */
    @Transactional(readOnly = true)
    public List<LmsCourse> sources(Long userAccountId, Long lmsCourseId) {
        LmsCourse target = managedTarget(userAccountId, lmsCourseId);
        return lmsCourses.findCopySources(target.getCourseOffering().getCourse().getId(),
                target.getCourseOffering().getId(), userAccountId);
    }

    /** Copies the source's content into the target as drafts. */
    @Transactional
    public CopyResult copy(Long userAccountId, Long lmsCourseId, Long sourceLmsCourseId) {
        LmsCourse sourceChoice = sources(userAccountId, lmsCourseId).stream()
                .filter(candidate -> candidate.getId().equals(sourceLmsCourseId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SOURCE_UNAVAILABLE));
        LmsCourse target = lmsCourses.findByIdForUpdate(lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        Long sourceId = sourceChoice.getId();

        List<LmsModule> sourceModules = modules
                .findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(sourceId, LmsContentPolicy.IN_USE).stream()
                .filter(module -> LmsContentPolicy.isInUse(module.getStatus()))
                .toList();
        Map<Long, List<LmsTopic>> pagesByModule = topics.findAllInCourse(sourceId, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE)
                .stream()
                .filter(topic -> LmsContentPolicy.isInUse(topic.getStatus()))
                .collect(Collectors.groupingBy(topic -> topic.getLmsModule().getId()));
        Map<Long, List<LmsStudyMaterial>> filesByPage = materials
                .findAllInCourse(sourceId, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE, LmsContentPolicy.IN_USE).stream()
                .filter(LmsContentPolicy::isInUseByFaculty)
                .collect(Collectors.groupingBy(material -> material.getLmsTopic().getId()));

        int modulePosition = modules.findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(lmsCourseId, LmsContentPolicy.IN_USE)
                .stream().mapToInt(LmsModule::getPosition).max().orElse(0);
        List<String> storedKeys = new ArrayList<>();
        int pageCount = 0;
        int fileCount = 0;
        try {
            for (LmsModule sourceModule : sourceModules) {
                LmsModule module = new LmsModule(target, sourceModule.getTitle(), sourceModule.getDescription(), ++modulePosition);
                module.requirePrevious(sourceModule.isRequiresPrevious());
                module = modules.saveAndFlush(module);
                int pagePosition = 0;
                for (LmsTopic sourcePage : pagesByModule.getOrDefault(sourceModule.getId(), List.of())) {
                    LmsTopic page = new LmsTopic(module, sourcePage.getTitle(), sourcePage.getContent(), ++pagePosition);
                    page.changeContentFormat(sourcePage.getContentFormat());
                    page.markRequired(sourcePage.isRequired());
                    page = topics.saveAndFlush(page);
                    pageCount++;
                    int filePosition = 0;
                    for (LmsStudyMaterial sourceFile : filesByPage.getOrDefault(sourcePage.getId(), List.of())) {
                        String key = "lms/courses/%d/topics/%d/%s".formatted(target.getId(), page.getId(), UUID.randomUUID());
                        try (InputStream bytes = storage.open(sourceFile.getObjectKey())) {
                            storage.store(key, bytes, sourceFile.getSizeBytes(), sourceFile.getContentType());
                        }
                        storedKeys.add(key);
                        materials.save(new LmsStudyMaterial(page, sourceFile.getTitle(), sourceFile.getDescription(), key,
                                sourceFile.getOriginalFileName(), sourceFile.getContentType(), sourceFile.getSizeBytes(),
                                ++filePosition));
                        fileCount++;
                    }
                }
            }
        } catch (ObjectStorageException | IOException failure) {
            LOGGER.warn("Course content could not be copied because a file could not be copied", failure);
            removeQuietly(storedKeys);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, STORAGE_UNAVAILABLE);
        } catch (RuntimeException failure) {
            removeQuietly(storedKeys);
            throw failure;
        }

        boolean syllabusCopied = false;
        LmsSyllabus sourceSyllabus = syllabi.findByLmsCourseId(sourceId).orElse(null);
        if (sourceSyllabus != null && syllabi.findByLmsCourseIdForUpdate(lmsCourseId).isEmpty()) {
            syllabi.save(new LmsSyllabus(target, sourceSyllabus.getDraftContent(), clock.instant()));
            syllabusCopied = true;
        }
        return new CopyResult(sourceModules.size(), pageCount, fileCount, syllabusCopied);
    }

    private LmsCourse managedTarget(Long userAccountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
        return lmsCourses.findWithAcademicContextById(lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
    }

    private void removeQuietly(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (ObjectStorageException failure) {
                LOGGER.warn("A copied file could not be removed after a failed copy", failure);
            }
        }
    }
}
