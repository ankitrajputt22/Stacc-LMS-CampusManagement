package com.stacc.backend.lms.content;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageException;
import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Study materials: faculty upload and manage them, and both sides download them. The file details are
 * kept in the database and the bytes in private object storage, under a key generated here.
 *
 * <p>Every faculty action finds the course behind the topic or material from stored data and asks
 * {@link LmsFacultyAccessService}. A download is allowed through the faculty rule or the student rule,
 * whichever applies. Something that does not exist and something the caller may not use get the same
 * 404. Nothing is deleted: materials are archived, and their stored files are kept.
 */
@Service
@Transactional
public class LmsStudyMaterialService {

    static final String MATERIAL_UNAVAILABLE = "Study material not found or unavailable.";
    static final String MATERIAL_ARCHIVED = "This study material is archived and can no longer be changed.";
    static final String MATERIAL_ORDER_MISMATCH = "List every current study material of this topic exactly once.";
    static final String STORAGE_UNAVAILABLE = "File storage is not available right now. Try again later.";

    private static final Logger LOGGER = LoggerFactory.getLogger(LmsStudyMaterialService.class);

    private final LmsTopicRepository topics;
    private final LmsStudyMaterialRepository materials;
    private final LmsFacultyAccessService facultyAccess;
    private final LmsStudentAccessService studentAccess;
    private final ObjectStorageService storage;
    private final long maxFileBytes;

    public LmsStudyMaterialService(
            LmsTopicRepository topics,
            LmsStudyMaterialRepository materials,
            LmsFacultyAccessService facultyAccess,
            LmsStudentAccessService studentAccess,
            ObjectStorageService storage,
            LmsMaterialProperties properties) {
        this.topics = topics;
        this.materials = materials;
        this.facultyAccess = facultyAccess;
        this.studentAccess = studentAccess;
        this.storage = storage;
        this.maxFileBytes = properties.maxFileSize().toBytes();
    }

    /** Which of the two download rules the caller's token lets them use. */
    public record Reader(boolean student, boolean faculty) {
    }

    /** An opened file for a download. The caller must close the content stream. */
    public record Download(String fileName, String contentType, long sizeBytes, InputStream content) {
    }

    /**
     * Adds a draft material at the end of the topic. The file is checked first, then stored under a new
     * key, then recorded. The database and the storage cannot share one transaction: if recording fails
     * after the file was stored, removing the stored file is attempted.
     */
    public LmsStudyMaterial upload(Long userAccountId, Long topicId, String title, String description, MultipartFile file) {
        LmsTopic topic = changeableTopic(userAccountId, topicId);
        LmsMaterialFiles.CheckedFile checked = LmsMaterialFiles.check(file, maxFileBytes);

        int position = materials.findAllByLmsTopicIdAndStatusInOrderByPositionAscIdAsc(topic.getId(), LmsContentPolicy.IN_USE)
                .stream().mapToInt(LmsStudyMaterial::getPosition).max().orElse(0) + 1;
        // The key is made here. Nothing the client sends, not even the file name, becomes part of it.
        String objectKey = "lms/courses/%d/topics/%d/%s".formatted(
                topic.getLmsModule().getLmsCourse().getId(), topic.getId(), UUID.randomUUID());
        LmsStudyMaterial material = new LmsStudyMaterial(topic, title, description, objectKey,
                checked.fileName(), checked.type().contentType(), checked.sizeBytes(), position);

        try (InputStream content = file.getInputStream()) {
            storage.store(objectKey, content, checked.sizeBytes(), checked.type().contentType());
        } catch (IOException failure) {
            throw new ApiException(HttpStatus.BAD_REQUEST, LmsMaterialFiles.UNREADABLE);
        } catch (ObjectStorageException failure) {
            LOGGER.warn("A study material could not be stored", failure);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, STORAGE_UNAVAILABLE);
        }

        try {
            return materials.saveAndFlush(material);
        } catch (RuntimeException failure) {
            removeQuietly(objectKey);
            throw failure;
        }
    }

    public LmsStudyMaterial updateMaterial(Long userAccountId, Long materialId, String title, String description) {
        LmsStudyMaterial material = changeableMaterial(userAccountId, materialId);
        material.edit(title, description);
        return material;
    }

    /** Publishes the material only. Students see it once its topic and module are published as well. */
    public LmsStudyMaterial publishMaterial(Long userAccountId, Long materialId) {
        LmsStudyMaterial material = changeableMaterial(userAccountId, materialId);
        material.publish();
        return material;
    }

    /** Archives the material. Its row and stored file are kept. Archiving it again changes nothing. */
    public LmsStudyMaterial archiveMaterial(Long userAccountId, Long materialId) {
        LmsStudyMaterial material = manageableMaterial(userAccountId, materialId);
        material.archive();
        return material;
    }

    /**
     * Puts the topic's current materials in the given order, numbered from 1. The list must hold every
     * material that is not archived, each exactly once, and nothing else.
     */
    public List<LmsStudyMaterial> reorderMaterials(Long userAccountId, Long topicId, List<Long> materialIds) {
        LmsTopic topic = changeableTopic(userAccountId, topicId);
        List<LmsStudyMaterial> current = materials.findAllByLmsTopicIdAndStatusInOrderByPositionAscIdAsc(
                topic.getId(), LmsContentPolicy.IN_USE);

        Map<Long, LmsStudyMaterial> byId = current.stream()
                .collect(Collectors.toMap(LmsStudyMaterial::getId, Function.identity()));
        Set<Long> requested = materialIds == null ? Set.of() : new HashSet<>(materialIds);
        if (materialIds == null || requested.size() != materialIds.size() || !requested.equals(byId.keySet())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MATERIAL_ORDER_MISMATCH);
        }
        List<LmsStudyMaterial> ordered = materialIds.stream().map(byId::get).toList();
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).moveTo(index + 1);
        }
        return ordered;
    }

    /**
     * Opens a material for download if one of the caller's rules allows it. As faculty: the material, its
     * topic, and its module are not archived, and the caller teaches the course. As a student: the
     * material, its topic, and its module are published, and the student may use the course. Otherwise
     * the answer is the same 404 as for a material that does not exist.
     */
    @Transactional(readOnly = true)
    public Download openForDownload(Long userAccountId, Reader reader, Long materialId) {
        LmsStudyMaterial material = materialId == null ? null : materials.findWithTopicAndModuleById(materialId).orElse(null);
        if (material == null || !mayDownload(userAccountId, reader, material)) {
            throw notFound(MATERIAL_UNAVAILABLE);
        }
        try {
            InputStream content = storage.open(material.getObjectKey());
            return new Download(material.getOriginalFileName(), material.getContentType(), material.getSizeBytes(), content);
        } catch (ObjectStorageException failure) {
            LOGGER.warn("A study material could not be read from storage", failure);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, STORAGE_UNAVAILABLE);
        }
    }

    private boolean mayDownload(Long userAccountId, Reader reader, LmsStudyMaterial material) {
        Long lmsCourseId = material.getLmsTopic().getLmsModule().getLmsCourse().getId();
        boolean asFaculty = reader.faculty()
                && LmsContentPolicy.isInUseByFaculty(material)
                && facultyAccess.canAccessCourse(userAccountId, lmsCourseId);
        return asFaculty
                || (reader.student()
                        && LmsContentPolicy.isVisibleToStudents(material)
                        && studentAccess.canAccessCourse(userAccountId, lmsCourseId));
    }

    // The course is taken from the stored topic and its module, never from the request.
    private LmsTopic changeableTopic(Long userAccountId, Long topicId) {
        LmsTopic topic = topicId == null ? null : topics.findWithModuleById(topicId).orElse(null);
        if (topic == null
                || !facultyAccess.canAccessCourse(userAccountId, topic.getLmsModule().getLmsCourse().getId())) {
            throw notFound(LmsContentManagementService.TOPIC_UNAVAILABLE);
        }
        if (topic.isArchived()) {
            throw conflict(LmsContentManagementService.TOPIC_ARCHIVED);
        }
        if (topic.getLmsModule().isArchived()) {
            throw conflict(LmsContentManagementService.MODULE_ARCHIVED);
        }
        return topic;
    }

    // The course is taken from the stored material, its topic, and its module, never from the request.
    private LmsStudyMaterial manageableMaterial(Long userAccountId, Long materialId) {
        LmsStudyMaterial material = materialId == null ? null : materials.findWithTopicAndModuleById(materialId).orElse(null);
        if (material == null || !facultyAccess.canAccessCourse(
                userAccountId, material.getLmsTopic().getLmsModule().getLmsCourse().getId())) {
            throw notFound(MATERIAL_UNAVAILABLE);
        }
        return material;
    }

    // A material can be changed only while it, its topic, and its module are all still in use.
    private LmsStudyMaterial changeableMaterial(Long userAccountId, Long materialId) {
        LmsStudyMaterial material = manageableMaterial(userAccountId, materialId);
        if (material.isArchived()) {
            throw conflict(MATERIAL_ARCHIVED);
        }
        if (material.getLmsTopic().isArchived()) {
            throw conflict(LmsContentManagementService.TOPIC_ARCHIVED);
        }
        if (material.getLmsTopic().getLmsModule().isArchived()) {
            throw conflict(LmsContentManagementService.MODULE_ARCHIVED);
        }
        return material;
    }

    private void removeQuietly(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException failure) {
            LOGGER.warn("A stored study material could not be removed after its record failed to save", failure);
        }
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
